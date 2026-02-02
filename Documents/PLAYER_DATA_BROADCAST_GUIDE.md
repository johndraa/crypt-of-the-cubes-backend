# PlayerDataBroadcaster 集成指南

## 概述

`PlayerDataBroadcaster` 是一个网络工具类，用于将玩家的实时统计数据（等级、伤害、升级等）同步到服务器，服务器会将这些数据广播给所有其他玩家。

## 使用场景

由于游戏 match 由服务器 handle，我们需要：
1. 在玩家升级时广播新的统计数据
2. 在玩家受伤时同步伤害信息
3. 其他玩家可以看到你的升级、伤害动画等

## GameActivity 中的集成

### 第一步：创建 PlayerDataBroadcaster 实例

```java
public class GameActivity extends Activity {
    
    private GameEngine gameEngine;
    private PlayerDataBroadcaster playerDataBroadcaster;
    private WebSocketManager webSocketManager;  // 你现有的 WebSocket 连接
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        
        // 初始化 GameEngine
        gameEngine = new GameEngine();
        
        // 创建广播器，实现回调来发送数据到服务器
        playerDataBroadcaster = new PlayerDataBroadcaster(
            playerData -> {
                // 将数据发送给服务器
                sendPlayerDataToServer(playerData);
            }
        );
        
        // 设置到 GameEngine
        gameEngine.setPlayerDataBroadcaster(playerDataBroadcaster);
    }
    
    /**
     * 将玩家数据发送给服务器（通过 WebSocket 或 STOMP）
     */
    private void sendPlayerDataToServer(JSONObject playerData) {
        try {
            Log.d("GameActivity", "发送玩家数据: " + playerData.toString());
            
            // 方案 1: 使用 STOMP 消息总线
            if (webSocketManager != null) {
                webSocketManager.send(
                    "/app/game/player/update", 
                    playerData.toString()
                );
            }
            
            // 方案 2: 使用 REST API (可选)
            // NetworkManager.updatePlayerStats(playerData);
            
        } catch (Exception e) {
            Log.e("GameActivity", "发送玩家数据失败", e);
        }
    }
}
```

### 第二步：处理服务器的广播消息

当其他玩家升级或受伤时，服务器会广播消息给你：

```java
// 在你的 WebSocket 监听器中
webSocketManager.subscribe("/user/queue/game-events", message -> {
    try {
        JSONObject data = new JSONObject(message);
        String event = data.getString("event");
        
        switch (event) {
            case "PLAYER_LEVEL_UP":
                handleRemotePlayerLevelUp(data);
                break;
            case "PLAYER_DAMAGE":
                handleRemotePlayerDamage(data);
                break;
            case "PLAYER_DEATH":
                handleRemotePlayerDeath(data);
                break;
            default:
                break;
        }
    } catch (JSONException e) {
        Log.e("GameActivity", "处理广播消息失败", e);
    }
});

private void handleRemotePlayerLevelUp(JSONObject data) throws JSONException {
    String playerId = data.getString("playerId");
    int newLevel = data.getInt("level");
    String reward = data.getString("reward");
    
    Log.d("GameActivity", playerId + " 升级到 Lv." + newLevel + "，获得: " + reward);
    
    // 在屏幕上显示升级动画或提示
    Toast.makeText(this, playerId + " 获得: " + reward, Toast.LENGTH_SHORT).show();
}

private void handleRemotePlayerDamage(JSONObject data) throws JSONException {
    String playerId = data.getString("playerId");
    int damage = data.getInt("damage");
    
    Log.d("GameActivity", playerId + " 受到 " + damage + " 伤害");
    
    // 在屏幕上显示伤害数字
    // showDamageNumber(playerId, damage);
}

private void handleRemotePlayerDeath(JSONObject data) throws JSONException {
    String playerId = data.getString("playerId");
    int level = data.getInt("level");
    
    Log.d("GameActivity", playerId + " 死亡 (Lv." + level + ")");
    
    // 从游戏世界中移除玩家
    gameEngine.removePlayer(playerId);
}
```

## 数据格式参考

### PLAYER_LEVEL_UP (玩家升级)
```json
{
    "event": "PLAYER_LEVEL_UP",
    "playerId": "player123",
    "level": 5,
    "reward": "Attack Speed",
    "health": 120,
    "maxHealth": 120,
    "attackPower": 15,
    "attackSpeed": 1.5,
    "damageMult": 1.2,
    "timestamp": 1702800000000
}
```

### PLAYER_DAMAGE (玩家伤害)
```json
{
    "event": "PLAYER_DAMAGE",
    "playerId": "player123",
    "damage": 10,
    "currentHealth": 110,
    "enemyId": "Zombie",
    "timestamp": 1702800001000
}
```

### PLAYER_STATS_UPDATE (统计更新)
```json
{
    "event": "PLAYER_STATS_UPDATE",
    "playerId": "player123",
    "health": 110,
    "maxHealth": 120,
    "level": 5,
    "experience": 250,
    "attackPower": 15,
    "attackSpeed": 1.5,
    "damageMult": 1.2,
    "critChance": 0.1,
    "armor": 5,
    "attackRange": 60,
    "lifeSteal": 0.1,
    "hasLifesteal": true,
    "x": 100.5,
    "y": 200.3,
    "moveSpeed": 6.0
}
```

### PLAYER_DEATH (玩家死亡)
```json
{
    "event": "PLAYER_DEATH",
    "playerId": "player123",
    "level": 5,
    "timestamp": 1702800010000
}
```

## 服务器端实现建议

你的 Spring Boot 后端应该：

### 1. 接收客户端的更新
```java
@RestController
@RequestMapping("/api/game")
public class GameController {
    
    @MessageMapping("/game/player/update")
    @SendTo("/topic/game/updates")
    public PlayerUpdateMessage updatePlayer(String playerData) {
        // 解析 JSON
        JSONObject data = new JSONObject(playerData);
        
        // 验证数据
        // 保存到数据库或缓存
        // 广播给其他玩家
        
        return new PlayerUpdateMessage(data);
    }
}
```

### 2. 广播给所有其他玩家
```java
@Component
public class GameBroadcaster {
    
    @Autowired
    private SimpMessagingTemplate template;
    
    public void broadcastPlayerUpdate(String playerId, JSONObject data) {
        // 发送给这个玩家所在的 match 中的所有其他玩家
        template.convertAndSend(
            "/topic/game/" + matchId + "/updates", 
            data.toString()
        );
    }
}
```

### 3. 处理多人同步
```java
// 当新玩家加入时，发送当前的游戏状态
@EventListener
public void handlePlayerConnected(PlayerConnectedEvent event) {
    String playerId = event.getPlayerId();
    
    // 获取所有其他玩家的当前统计
    List<PlayerStats> otherPlayers = gameService.getOtherPlayersStats(playerId);
    
    // 发送给新玩家
    template.convertAndSendToUser(
        playerId,
        "/queue/game-state",
        otherPlayers
    );
}
```

## 性能优化建议

### 1. 限制广播频率
不是每个伤害都广播，可以设置时间间隔：

```java
public class RateLimitedBroadcaster {
    private static final long BROADCAST_INTERVAL = 100;  // 100ms
    private long lastBroadcastTime = 0;
    
    public void broadcastIfReady(PlayerDataBroadcaster broadcaster, Player player) {
        long now = System.currentTimeMillis();
        if (now - lastBroadcastTime >= BROADCAST_INTERVAL) {
            broadcaster.broadcastPlayerStats(player);
            lastBroadcastTime = now;
        }
    }
}
```

### 2. 合并多个更新
```java
// 不是立即发送，而是收集更新然后批量发送
public class BatchedBroadcaster {
    private Queue<PlayerUpdate> updates = new LinkedList<>();
    
    public void addUpdate(PlayerUpdate update) {
        updates.offer(update);
    }
    
    public void flushBatch() {
        if (!updates.isEmpty()) {
            JSONArray batch = new JSONArray();
            while (!updates.isEmpty()) {
                batch.put(updates.poll().toJSON());
            }
            broadcast(batch);
        }
    }
}
```

### 3. 只发送变化的字段
```java
// 跟踪上次发送的值，只发送改变的部分
public class DeltaBroadcaster {
    private PlayerStats lastStats;
    
    public JSONObject getDelta(Player player) {
        JSONObject delta = new JSONObject();
        
        if (player.getHealth() != lastStats.health) {
            delta.put("health", player.getHealth());
        }
        if (player.getLevel() != lastStats.level) {
            delta.put("level", player.getLevel());
        }
        // ... 其他字段
        
        return delta;
    }
}
```

## 测试检查清单

- [ ] 玩家升级时服务器收到数据
- [ ] 服务器向其他玩家广播升级事件
- [ ] 其他玩家看到升级提示/动画
- [ ] 伤害事件正确同步
- [ ] 新玩家加入时收到现有玩家的统计
- [ ] 玩家死亡事件正确处理
- [ ] 网络延迟不会导致数据丢失
- [ ] 多个玩家同时升级不会冲突

## 常见问题

**Q: 如果网络连接丢失怎么办？**  
A: 使用重试机制，或者缓存未发送的数据

**Q: 服务器数据和客户端数据不一致怎么办？**  
A: 定期同步完整的玩家统计，或者只接受服务器的权威值

**Q: 如何防止数据被篡改？**  
A: 服务器端验证数据，不要信任客户端发来的所有值

---

**更新日期**: 2024年  
**版本**: 1.0
