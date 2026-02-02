# 核心架构：Per-User Snapshot + 升级系统集成

## 概述

游戏有两个核心系统需要协调：

1. **Per-User Snapshot** - 服务器定期发送完整的游戏状态
2. **升级系统** - 客户端本地处理经验值和奖励选择

## 架构设计

### Per-User Snapshot 流程

```
服务器
  ↓
生成当前游戏状态 JSON
  ↓
发送给所有客户端
  ↓
客户端接收 Snapshot
  ↓
GameEngine.applySnapshot(JSONObject)
  ↓
  ├─ 更新所有玩家位置和生命值
  ├─ 更新所有敌人位置和生命值
  ├─ 更新升级系统属性（等级、经验、统计）
  └─ 同步状态
```

### 升级系统流程（客户端本地）

```
玩家击杀敌人 (+2 XP)
  ↓
ExperienceSystem.addExperience(2)
  ↓
checkLevelUp() - 检查是否达到升级阈值
  ↓
触发回调 → GameEngine.handleLevelUp()
  ↓
  ├─ 设置 isInLevelUpState = true
  ├─ 显示 LevelUpDialog (UI)
  └─ 等待玩家选择奖励
  ↓
玩家选择奖励
  ↓
RewardApplier.applyReward(Player, Reward)
  ↓
修改玩家属性 (attackSpeed, damage 等)
  ↓
PlayerDataBroadcaster.broadcastLevelUp()
  ↓
发送给服务器
  ↓
  └─ 服务器广播给其他玩家
```

## 数据同步策略

### 权限分配

| 属性 | 权限 | 说明 |
|------|------|------|
| 位置 (x, y) | 服务器权威 | 由服务器通过 snapshot 更新 |
| 血量 (health) | 服务器权威 | 服务器计算伤害和恢复 |
| 等级 (level) | 双向同步 | 客户端本地升级后广播给服务器 |
| 经验 (experience) | 本地管理 | 客户端计算，升级时同步 |
| 统计 (attackSpeed, damage 等) | 双向同步 | 客户端应用奖励后广播 |

### 同步流程

```
Timeline:
─────────────────────────────────────────────────

T1: 服务器发送 Snapshot
    ├─ Player.level = 3
    ├─ Player.experience = 50
    ├─ Player.attackSpeed = 1.0
    └─ Player.armor = 0

T2: 客户端收到 Snapshot
    └─ applySnapshot() 应用这些值

T3: 玩家击杀敌人 +2 XP
    ├─ experience = 52
    ├─ 不达到升级阈值（需要 300）
    └─ 继续游戏

T4: 玩家继续击杀，累积 XP
    ├─ experience = 250
    ├─ 继续

T5: 玩家击杀，经验溢出
    ├─ experience = 350 (>= 300)
    ├─ checkLevelUp() 触发
    ├─ level++ (3 → 4)
    ├─ experience = 50 (重置)
    └─ 触发 handleLevelUp()

T6: 显示升级对话框（无敌状态）
    ├─ isInLevelUpState = true
    └─ 等待玩家选择

T7: 玩家选择"Attack Speed" 奖励
    ├─ attackSpeed = 1.0 + 0.5 = 1.5
    ├─ isInLevelUpState = false
    └─ 广播给服务器

T8: 服务器收到升级数据
    ├─ 验证和保存
    └─ 广播给其他玩家

T9: 下一个 Snapshot 到来
    ├─ 包含最新的等级和统计
    └─ 重新应用 applySnapshot()

─────────────────────────────────────────────────
```

## 关键集成点

### 1. applySnapshot() 中的升级系统属性同步

```java
// GameEngine.java
private void applyPlayerUpgradeStats(Player player, JSONObject playerData) {
    // 从快照中恢复玩家属性
    if (playerData.has("level")) {
        player.setLevel(playerData.getInt("level"));
    }
    if (playerData.has("attackSpeed")) {
        player.setAttackSpeed((float) playerData.getDouble("attackSpeed"));
    }
    // ... 其他属性
}
```

**作用**: 确保其他玩家的升级奖励在快照中得到更新

### 2. 升级时的广播机制

```java
// GameEngine.java - handleLevelUp()
private void handleLevelUp(int newLevel) {
    // ... 显示对话框 ...
    
    levelUpWindowListener.onLevelUpRewardSelection(rewards, 
        selectedReward -> {
            RewardApplier.applyReward(localPlayer, selectedReward);
            
            // 立即广播给服务器
            if (playerDataBroadcaster != null) {
                playerDataBroadcaster.broadcastLevelUp(
                    localPlayer, 
                    newLevel, 
                    selectedReward.getName()
                );
            }
        }
    );
}
```

**作用**: 升级后立即通知服务器，不等待下一个 snapshot

### 3. 伤害事件的广播

```java
// GameEngine.java - handleCollisions()
if (playerDataBroadcaster != null) {
    playerDataBroadcaster.broadcastPlayerDamage(
        localPlayer, 
        damageAmount, 
        enemy.getName()
    );
}
```

**作用**: 其他玩家可以看到你受伤的动画效果

## 冲突解决策略

### 情景 1: 升级后立即收到 Snapshot

**问题**: 
- T7: 客户端升级为 Lv.4，attackSpeed = 1.5
- T8: 服务器还没更新，发送 Lv.3, attackSpeed = 1.0 的 snapshot

**解决**: 
- 使用 `setLevel()` 而不是重置
- 升级后的本地值临时保留
- 下一个 snapshot 包含正确的值时再更新

### 情景 2: 网络延迟导致数据不一致

**问题**: 
- 客户端说：Lv.5, experience = 100
- 服务器说：Lv.4, experience = 300

**解决**: 
- 服务器是权威数据源
- 使用 snapshot 校正客户端状态
- 客户端显示临时状态，等 snapshot 确认

### 情景 3: 同时接收广播和 Snapshot

**问题**: 
- 广播说升级获得"Attack Speed"
- Snapshot 说 attackSpeed = 1.0

**解决**: 
- 按时间戳排序
- 较新的数据覆盖较旧的
- 或者服务器在 snapshot 中包含广播的更新

## 实现检查清单

### 客户端

- [x] ExperienceSystem 处理 XP 和升级检测
- [x] GameEngine.applySnapshot() 应用所有属性
- [x] applyPlayerUpgradeStats() 同步升级系统属性
- [x] Player 有 setLevel() 和 setExperience() 方法
- [x] handleLevelUp() 显示对话框
- [x] RewardApplier 修改玩家属性
- [x] PlayerDataBroadcaster 发送更新
- [x] 伤害时广播信息

### 服务器

- [ ] 接收 PLAYER_LEVEL_UP 事件
- [ ] 验证升级数据
- [ ] 保存到数据库
- [ ] 在 snapshot 中包含最新属性
- [ ] 广播升级事件给其他玩家
- [ ] 接收 PLAYER_DAMAGE 事件
- [ ] 处理多人冲突

## 网络数据格式

### 快照中的升级系统属性

```json
{
    "players": [
        {
            "id": 123,
            "name": "Player1",
            "x": 100.5,
            "y": 200.3,
            "health": 80,
            "maxHealth": 100,
            "level": 5,
            "experience": 150,
            "attackPower": 15,
            "attackSpeed": 1.5,
            "damageMult": 1.2,
            "critChance": 0.1,
            "armor": 5,
            "attackRange": 60,
            "lifeSteal": 0.1,
            "hasLifesteal": true,
            "moveSpeed": 6.0
        }
    ]
}
```

### 广播格式

```json
{
    "event": "PLAYER_LEVEL_UP",
    "playerId": 123,
    "level": 5,
    "reward": "Attack Speed",
    "timestamp": 1702800000000
}
```

## 性能优化

### 1. 减少 Snapshot 频率

```java
// 不是每帧都应用 snapshot，而是定期
private long lastSnapshotTime = 0;
private static final long SNAPSHOT_INTERVAL = 100;  // 100ms

public void applySnapshot(JSONObject snapshot) {
    long now = System.currentTimeMillis();
    if (now - lastSnapshotTime < SNAPSHOT_INTERVAL) {
        return;  // 跳过频繁更新
    }
    lastSnapshotTime = now;
    // ... 应用快照 ...
}
```

### 2. 增量更新而不是完全替换

```java
// 不是每次都重新创建 Player 对象，而是更新属性
if (playerData.has("level") && 
    playerData.getInt("level") != player.getLevel()) {
    player.setLevel(playerData.getInt("level"));
}
```

### 3. 缓存 JSON 解析结果

```java
private Map<Integer, CachedPlayerData> playerCache = new HashMap<>();

public void applySnapshot(JSONObject snapshot) {
    // 只处理变化的玩家
    for (Player player : allPlayers) {
        CachedPlayerData cached = playerCache.get(player.getId());
        if (cached != null && cached.hash == newDataHash) {
            continue;  // 数据未改变，跳过
        }
    }
}
```

## 测试场景

### 场景 1: 单人升级

1. 击杀敌人获得 XP
2. 升级显示对话框
3. 选择奖励应用统计
4. 广播给服务器
5. 下一个 snapshot 确认

### 场景 2: 多人协作

1. 玩家 A 升级
2. 玩家 B 看到升级提示
3. 下一个 snapshot 包含 A 的新属性

### 场景 3: 快速升级

1. 快速连续升级多次
2. 每次升级都广播
3. 确保所有广播都被发送
4. Snapshot 最终状态一致

### 场景 4: 离线和重新连接

1. 玩家离线，客户端保持本地升级
2. 玩家重新连接
3. 收到服务器 snapshot
4. 同步到服务器状态
5. 确保数据一致

### 场景 5: 网络延迟

1. 升级后立即收到旧的 snapshot
2. 使用时间戳判断哪个更新
3. 显示合理的状态给用户

## 故障排除

### 问题: 升级后统计没有更新

**检查**:
1. RewardApplier 是否正确调用了 setter
2. PlayerDataBroadcaster 是否发送了数据
3. 服务器是否收到并验证了数据

### 问题: 其他玩家看不到你的升级

**检查**:
1. 广播是否成功发送到服务器
2. 服务器是否广播给其他玩家
3. 其他玩家是否正确处理了广播消息

### 问题: Snapshot 和本地数据不一致

**检查**:
1. 时间戳是否正确
2. 是否有网络延迟导致数据过时
3. applyPlayerUpgradeStats() 是否应用了所有属性

## 扩展考虑

### 未来改进

1. **离线模式**: 本地升级，连接时同步
2. **推测执行**: 预测服务器更新，优化显示
3. **回滚机制**: 如果服务器数据不同，平滑回滚
4. **增量 snapshot**: 只发送变化的部分而不是完整状态

---

**关键要点**:
- Per-User Snapshot 是服务器权威数据源
- 升级系统在客户端本地计算，然后广播
- 两个系统通过 applyPlayerUpgradeStats() 集成
- 所有属性必须支持从 JSON 反序列化

---

*文档版本*: 1.0  
*最后更新*: 2024年12月5日
