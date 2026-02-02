# 等级提升系统实现总结

## 概述

已成功实现完整的三层等级提升系统，包括奖励池、UI对话框、玩家统计应用和无敌状态管理。系统架构遵循 SOLID 原则，具有高度的可扩展性和可维护性。

---

## 核心组件

### 1. **Reward.java** (奖励数据模型)
**位置**: `app/src/main/java/com/vampiresurvival/game/reward/Reward.java`

**功能**: 代表单个等级提升奖励

**奖励类型** (8种):
- `HEALTH` (+20 最大生命值)
- `ATTACK_SPEED` (+0.5x 攻击速度)
- `DAMAGE` (+0.1x 伤害倍数)
- `CRIT_CHANCE` (+0.1 暴击率)
- `MOVE_SPEED` (+1 移动速度)
- `LIFESTEAL` (+0.1 吸血率)
- `ARMOR` (+5 护甲)
- `RANGE` (+10 攻击范围)

**关键方法**:
```java
Reward(RewardType type)  // 构造函数，自动填充名称/描述/值
getRewardName()          // 获取奖励名称
getRewardDescription()   // 获取奖励描述
getDefaultValue()        // 获取默认效果值
getType()                // 获取奖励类型
```

### 2. **RewardPool.java** (奖励管理器)
**位置**: `app/src/main/java/com/vampiresurvival/game/reward/RewardPool.java`

**功能**: 管理所有 8 种奖励类型；随机选择 3 个不同的奖励供玩家选择

**关键方法**:
```java
RewardPool()                    // 初始化所有 8 种奖励类型
selectRandomRewards()           // 返回 3 个随机不同的奖励 (List<Reward>)
```

**算法**: 使用 Random 类随机选择，确保选中的 3 个奖励互不相同

### 3. **RewardApplier.java** (奖励应用逻辑)
**位置**: `app/src/main/java/com/vampiresurvival/game/reward/RewardApplier.java`

**功能**: 应用选中的奖励到玩家统计

**关键方法**:
```java
static applyReward(Player player, Reward reward)  // 根据奖励类型应用效果
```

**实现原理**:
- 使用 Switch 语句判断奖励类型
- 调用相应的 Player setter 方法应用效果
- 特殊处理 `LIFESTEAL` 类型 (启用吸血机制)

**支持的玩家属性修改**:
- `setMaxHealth(int)` - 增加最大生命值
- `setAttackSpeed(float)` - 设置攻击速度倍数
- `setDamageMult(float)` - 设置伤害倍数
- `setCritChance(float)` - 设置暴击率
- `setMoveSpeed(float)` - 设置移动速度
- `setArmor(float)` - 设置护甲值
- `setAttackRange(float)` - 设置攻击范围
- `enableLifesteal()`/`increaseLifesteal(float)` - 启用并增加吸血

### 4. **LevelUpDialog.java** (UI对话框)
**位置**: `app/src/main/java/com/vampiresurvival/ui/LevelUpDialog.java`

**功能**: 显示 3 个奖励选项的模态对话框，玩家必须选择一个

**关键特性**:
- 不可取消 (`setCancelable(false)`)
- 3 个并排的奖励卡片
- 每张卡片显示：奖励名称、描述和"选择"按钮
- 选择后自动关闭

**关键方法**:
```java
LevelUpDialog(Context context)                    // 构造函数
displayRewards(List<Reward> rewards)             // 显示 3 个奖励
setOnRewardSelectedListener(OnRewardSelectedListener listener)  // 设置回调
show()                                           // 显示对话框
```

**布局文件**: `res/layout/dialog_level_up.xml`
- 垂直 LinearLayout 容器
- 标题: "升级！选择一个增益"
- 水平排列的 3 个奖励卡片容器
- 每个卡片有名称、描述和按钮

**样式资源**: `res/drawable/reward_card_background.xml`
- 金色边框 (#FFD700)
- 浅灰色填充 (#F0F0F0)
- 8dp 圆角

### 5. **监听器接口**

#### **OnRewardSelectedCallback.java** (奖励选择回调)
**位置**: `app/src/main/java/com/vampiresurvival/game/OnRewardSelectedCallback.java`

**功能**: 当玩家在对话框中选择奖励时的回调

```java
public interface OnRewardSelectedCallback {
    void onRewardSelected(Reward selectedReward);
}
```

#### **OnLevelUpWindowListener.java** (等级提升窗口监听器)
**位置**: `app/src/main/java/com/vampiresurvival/game/OnLevelUpWindowListener.java`

**功能**: UI 组件实现此接口来处理等级提升事件

```java
public interface OnLevelUpWindowListener {
    void onLevelUpRewardSelection(List<Reward> rewards, 
                                  OnRewardSelectedCallback callback);
}
```

---

## 系统集成

### 游戏流程

```
1. 玩家击杀敌人
        ↓
2. AutoAttack.attack() 调用 experienceSystem.addExperience(2)
        ↓
3. ExperienceSystem 检查是否升级 (checkLevelUp)
        ↓
4. 升级时调用监听器回调: experienceSystem.onLevelUp(newLevel)
        ↓
5. GameEngine.handleLevelUp() 被调用
        ↓
6. 设置 isInLevelUpState = true (玩家无敌)
        ↓
7. RewardPool.selectRandomRewards() 获取 3 个奖励
        ↓
8. GameActivity (via OnLevelUpWindowListener) 显示 LevelUpDialog
        ↓
9. 玩家选择奖励
        ↓
10. RewardApplier.applyReward() 应用统计修改
        ↓
11. 设置 isInLevelUpState = false
        ↓
12. 游戏继续
```

### 关键修改

#### **GameEngine.java**
- **新字段**:
  - `RewardPool rewardPool` - 奖励管理器
  - `boolean isInLevelUpState` - 无敌状态标志
  - `OnLevelUpWindowListener levelUpWindowListener` - UI 监听器

- **修改的方法**:
  - `initializeLocalPlayer()` - 初始化 RewardPool，设置 ExperienceSystem 监听器，为 AutoAttack 设置 ExperienceSystem
  - `handleCollisions()` - 在等级提升状态下跳过伤害处理

- **新方法**:
  - `handleLevelUp(int)` - 处理等级提升事件，显示对话框
  - `setOnLevelUpWindowListener(OnLevelUpWindowListener)` - 设置 UI 监听器
  - `isInLevelUpState()` - 检查是否处于选择奖励状态

#### **ExperienceSystem.java**
- **新方法**:
  - `setOnLevelUpListener(OnLevelUpListener)` - 设置升级回调
  - `levelUp()` - 触发回调

- **回调接口**:
  ```java
  public interface OnLevelUpListener {
      void onLevelUp(int newLevel);
  }
  ```

#### **AutoAttack.java**
- **新方法**:
  - `setExperienceSystem(ExperienceSystem)` - 设置体验系统引用

- **修改的方法**:
  - `attack()` - 敌人死亡时调用 `experienceSystem.addExperience(2)`

#### **Player.java**
- **新属性**:
  ```java
  private float attackSpeed = 1.0f;
  private float damageMult = 1.0f;
  private float critChance = 0.0f;
  private float armor = 0.0f;
  private float attackRange = 50.0f;
  private float lifeSteal = 0.0f;
  private boolean hasLifeSteal = false;
  ```

- **新方法**: (共 18 个 getter/setter/adder)
  - `getAttackSpeed()`, `setAttackSpeed()`, `addAttackSpeed()`
  - `getDamageMult()`, `setDamageMult()`, `addDamageMult()`
  - `getCritChance()`, `setCritChance()`, `addCritChance()`
  - `getArmor()`, `setArmor()`, `addArmor()`
  - `getAttackRange()`, `setAttackRange()`, `addAttackRange()`
  - `getLifeSteal()`, `setLifeSteal()`, `addLifeSteal()`
  - `hasLifesteal()`, `enableLifesteal()`, `increaseLifesteal()`

---

## 无敌状态管理

### 实现机制
- **启用**: 当 `GameEngine.handleLevelUp()` 被调用时，设置 `isInLevelUpState = true`
- **禁用**: 当玩家选择奖励并应用时，设置 `isInLevelUpState = false`
- **使用**: 在 `handleCollisions()` 中检查此标志，如果为 true 则跳过伤害处理

### 代码示例
```java
// 在 GameEngine.handleCollisions() 中
if (player == localPlayer && isInLevelUpState) {
    continue;  // 跳过伤害
}
```

---

## 可扩展性设计

### 添加新奖励类型
1. 在 `Reward.java` 中添加新的 `RewardType` 枚举值
2. 在 `getRewardName()`, `getRewardDescription()`, `getDefaultValue()` 中添加 case
3. 在 `RewardApplier.java` 中添加新的 case 来应用效果
4. 在 `Player.java` 中添加相应的属性和 setter 方法

### 示例：添加"暴击伤害"奖励
```java
// 1. Reward.java
public enum RewardType {
    HEALTH, ATTACK_SPEED, DAMAGE, CRIT_CHANCE, MOVE_SPEED, 
    LIFESTEAL, ARMOR, RANGE, CRIT_DAMAGE  // 新增
}

// 2. Reward.java getDefaultValue()
case CRIT_DAMAGE:
    return 0.5f;

// 3. RewardApplier.java applyReward()
case CRIT_DAMAGE:
    player.setCritDamage(player.getCritDamage() + reward.getDefaultValue());
    break;

// 4. Player.java
private float critDamage = 1.0f;
public float getCritDamage() { return critDamage; }
public void setCritDamage(float critDamage) { this.critDamage = critDamage; }
```

---

## 测试清单

### 基本功能测试
- [ ] 敌人击杀时获得 +2 XP
- [ ] 达到 XP 阈值时触发等级提升
- [ ] 等级提升时显示 LevelUpDialog
- [ ] 玩家可以选择 3 个奖励中的任何一个
- [ ] 选择奖励后对话框关闭

### 无敌状态测试
- [ ] 等级提升选择时玩家不受伤害
- [ ] 选择奖励后恢复受伤状态
- [ ] 多次升级时无敌状态正常工作

### 奖励应用测试
- [ ] HEALTH：最大生命值增加 20
- [ ] ATTACK_SPEED：攻击速度乘以 0.5
- [ ] DAMAGE：伤害乘以 0.1
- [ ] CRIT_CHANCE：暴击率增加 0.1
- [ ] MOVE_SPEED：移动速度增加 1
- [ ] LIFESTEAL：启用吸血，吸血率增加 0.1
- [ ] ARMOR：护甲增加 5
- [ ] RANGE：攻击范围增加 10

### UI/UX 测试
- [ ] 对话框显示正确的奖励名称和描述
- [ ] 奖励卡片样式美观
- [ ] 按钮响应迅速
- [ ] 文字清晰可读

### 集成测试
- [ ] 游戏不会因为等级提升而崩溃
- [ ] 多个玩家时只有本地玩家无敌
- [ ] 网络消息传输正常工作
- [ ] 与现有系统无冲突

---

## 文件清单

### 新创建文件 (6 个)
1. `app/src/main/java/com/vampiresurvival/game/reward/Reward.java`
2. `app/src/main/java/com/vampiresurvival/game/reward/RewardPool.java`
3. `app/src/main/java/com/vampiresurvival/game/reward/RewardApplier.java`
4. `app/src/main/java/com/vampiresurvival/ui/LevelUpDialog.java`
5. `app/src/main/java/com/vampiresurvival/game/OnRewardSelectedCallback.java`
6. `app/src/main/java/com/vampiresurvival/game/OnLevelUpWindowListener.java`

### 新创建资源 (2 个)
7. `app/src/main/res/layout/dialog_level_up.xml`
8. `app/src/main/res/drawable/reward_card_background.xml`

### 修改的文件 (5 个)
1. `app/src/main/java/com/vampiresurvival/game/GameEngine.java`
2. `app/src/main/java/com/vampiresurvival/game/ExperienceSystem.java`
3. `app/src/main/java/com/vampiresurvival/game/AutoAttack.java`
4. `app/src/main/java/com/vampiresurvival/models/Player.java`

---

## 设计模式

- **Strategy Pattern**: RewardApplier 使用 switch 策略模式应用不同的奖励
- **Observer Pattern**: ExperienceSystem -> GameEngine -> UI 的监听器链
- **Factory Pattern**: RewardPool 工厂创建 Reward 对象
- **Singleton-like Pattern**: RewardPool 每场游戏一个实例

---

## 性能考虑

- **内存**: RewardPool 创建 8 个 Reward 对象，选择时创建 List，开销最小
- **CPU**: 随机选择操作 O(3n) = O(24)，忽略不计
- **无敌状态**: 仅简单的 boolean 检查，零性能影响

---

## 已解决的问题

1. **店铺显示问题**: 修复了 `characterId` 字段映射，实现了 fallback diff 计算
2. **构建错误**: 移除了 android.jar 冲突，添加了 desugaring 配置
3. **JavaDoc 生成**: 修复了源设置时序和 Windows 路径问题
4. **现在**: 完整的等级提升系统实现，包含 3 个奖励选择、无敌状态和统计应用

---

## 后续改进建议

1. 添加动画/过渡效果到 LevelUpDialog
2. 添加声音效果到奖励选择
3. 实现奖励历史/统计页面
4. 添加更多奖励类型
5. 实现奖励预览/提示功能
6. 添加战斗计数器 (升级前需要多少次击杀)
7. 实现网络同步（其他玩家看到等级提升的视觉反馈）

---

## 验证命令

```bash
# 编译项目
cd "Frontend/game_proj/VampireSurvivalGame"
./gradlew clean assembleDebug

# 检查所有新文件是否存在
ls -la app/src/main/java/com/vampiresurvival/game/reward/
ls -la app/src/main/java/com/vampiresurvival/ui/LevelUpDialog.java
ls -la app/src/main/res/layout/dialog_level_up.xml
ls -la app/src/main/res/drawable/reward_card_background.xml
```

---

**实现日期**: 2024年
**状态**: ✅ 完成且可编译
**集成状态**: ✅ 全部集成到 GameEngine
**测试状态**: ⏳ 等待手动测试
