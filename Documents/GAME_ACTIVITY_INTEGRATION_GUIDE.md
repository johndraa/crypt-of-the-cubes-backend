# GameActivity 集成指南

本指南说明如何在 GameActivity 中连接等级提升 UI 系统。

## 实现步骤

### 1. 让 GameActivity 实现 OnLevelUpWindowListener 接口

```java
public class GameActivity extends Activity implements OnLevelUpWindowListener {
    
    private GameEngine gameEngine;
    private LevelUpDialog levelUpDialog;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // ... 其他初始化代码 ...
        
        // 连接等级提升监听器
        gameEngine.setOnLevelUpWindowListener(this);
    }
    
    @Override
    public void onLevelUpRewardSelection(List<Reward> rewards, 
                                         OnRewardSelectedCallback callback) {
        // 创建并显示对话框
        levelUpDialog = new LevelUpDialog(this);
        levelUpDialog.displayRewards(rewards);
        
        // 设置选择回调
        levelUpDialog.setOnRewardSelectedListener(new LevelUpDialog.OnRewardSelectedListener() {
            @Override
            public void onRewardSelected(Reward selectedReward) {
                // 调用游戏引擎的回调
                callback.onRewardSelected(selectedReward);
                
                // 可选：播放 UI 动画或音效
                playRewardAppliedAnimation(selectedReward);
            }
        });
        
        // 显示对话框
        levelUpDialog.show();
    }
    
    private void playRewardAppliedAnimation(Reward reward) {
        // 实现你的动画逻辑
        Toast.makeText(this, "获得: " + reward.getRewardName(), Toast.LENGTH_SHORT).show();
    }
}
```

### 2. 导入必要的类

```java
import com.vampiresurvival.game.OnLevelUpWindowListener;
import com.vampiresurvival.game.OnRewardSelectedCallback;
import com.vampiresurvival.game.reward.Reward;
import com.vampiresurvival.ui.LevelUpDialog;
import java.util.List;
```

### 3. 确保 LevelUpDialog 有正确的监听器接口

LevelUpDialog 应该有以下方法：

```java
public interface OnRewardSelectedListener {
    void onRewardSelected(Reward selectedReward);
}

public void setOnRewardSelectedListener(OnRewardSelectedListener listener) {
    this.listener = listener;
}
```

### 4. 在 GameActivity 中正确处理生命周期

```java
@Override
protected void onDestroy() {
    if (levelUpDialog != null && levelUpDialog.isShowing()) {
        levelUpDialog.dismiss();
    }
    super.onDestroy();
}
```

## 重要注意事项

1. **线程安全**: 确保 LevelUpDialog 在主线程上显示
   ```java
   runOnUiThread(() -> {
       levelUpDialog.show();
   });
   ```

2. **对话框管理**: 使用 FragmentDialog 而不是普通 Dialog 可能更好
   ```java
   // 推荐的实现方式
   public static class LevelUpDialogFragment extends DialogFragment {
       // ...
   }
   ```

3. **测试**: 快速测试等级提升系统
   ```java
   // 在 GameActivity 中添加测试按钮
   findViewById(R.id.test_level_up).setOnClickListener(v -> {
       experienceSystem.addExperience(1000);  // 直接升级
   });
   ```

## 可选增强功能

### 添加等级提升特效

```java
private void playLevelUpEffect() {
    // 播放音效
    MediaPlayer.create(this, R.raw.level_up_sound).start();
    
    // 显示粒子效果 (需要自己实现或使用库)
    // showParticleEffect(gameEngine.getLocalPlayer().getX(), 
    //                    gameEngine.getLocalPlayer().getY());
    
    // 屏幕震动
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Vibrator vibrator = getSystemService(Vibrator.class);
        vibrator.vibrate(VibrationEffect.createOneShot(
            100, VibrationEffect.DEFAULT_AMPLITUDE));
    }
}
```

### 添加奖励预览提示

```java
private void showRewardToast(Reward reward) {
    String message = String.format(
        "%s: %s", 
        reward.getRewardName(), 
        reward.getRewardDescription()
    );
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
}
```

### 添加等级显示 UI

```java
private void updateLevelDisplay() {
    Player player = gameEngine.getLocalPlayer();
    levelTextView.setText("等级: " + player.getLevel());
    
    // 更新经验条
    int xpPercent = (int)(((float)player.getExperience() / 
                           (player.getLevel() * 100)) * 100);
    experienceBar.setProgress(xpPercent);
}
```

## 调试日志

启用调试日志查看等级提升系统的运行状态：

```bash
# 过滤等级提升相关日志
adb logcat | grep -E "GameEngine|ExperienceSystem|LevelUp"
```

---

## 完整示例 (GameActivity)

```java
public class GameActivity extends Activity implements OnLevelUpWindowListener {
    
    private GameEngine gameEngine;
    private GameView gameView;
    private LevelUpDialog levelUpDialog;
    private TextView levelTextView;
    private ProgressBar experienceBar;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        
        gameView = findViewById(R.id.game_view);
        levelTextView = findViewById(R.id.level_text);
        experienceBar = findViewById(R.id.experience_bar);
        
        gameEngine = gameView.getGameEngine();
        
        // 连接等级提升 UI
        gameEngine.setOnLevelUpWindowListener(this);
        
        // 设置定期更新 UI
        gameView.post(this::updateUI);
    }
    
    @Override
    public void onLevelUpRewardSelection(List<Reward> rewards, 
                                         OnRewardSelectedCallback callback) {
        levelUpDialog = new LevelUpDialog(this);
        levelUpDialog.displayRewards(rewards);
        
        levelUpDialog.setOnRewardSelectedListener(selectedReward -> {
            callback.onRewardSelected(selectedReward);
            playLevelUpEffect();
            dismissLevelUpDialog();
        });
        
        levelUpDialog.show();
    }
    
    private void playLevelUpEffect() {
        // 播放升级效果
        Toast.makeText(this, "升级了！", Toast.LENGTH_SHORT).show();
    }
    
    private void dismissLevelUpDialog() {
        if (levelUpDialog != null) {
            levelUpDialog.dismiss();
        }
    }
    
    private void updateUI() {
        if (gameEngine == null) return;
        
        Player player = gameEngine.getLocalPlayer();
        if (player == null) return;
        
        updateLevelDisplay();
        
        gameView.post(this::updateUI);
    }
    
    private void updateLevelDisplay() {
        Player player = gameEngine.getLocalPlayer();
        levelTextView.setText(String.format(
            "Lv.%d (XP: %d/%d)", 
            player.getLevel(),
            player.getExperience(),
            player.getLevel() * 100
        ));
        
        int xpPercent = (int)(((float)player.getExperience() / 
                               (player.getLevel() * 100)) * 100);
        experienceBar.setProgress(Math.min(xpPercent, 100));
    }
    
    @Override
    protected void onDestroy() {
        if (levelUpDialog != null && levelUpDialog.isShowing()) {
            levelUpDialog.dismiss();
        }
        super.onDestroy();
    }
}
```

---

## 故障排除

### 问题 1: LevelUpDialog 不显示

**原因**: 可能在后台线程上调用

**解决**:
```java
runOnUiThread(() -> levelUpDialog.show());
```

### 问题 2: 玩家在升级时仍然受伤害

**原因**: `isInLevelUpState` 可能没有正确设置

**调试**:
```java
Log.d("GameActivity", "InLevelUp: " + gameEngine.isInLevelUpState());
```

### 问题 3: 奖励没有应用到玩家

**原因**: Player 的 setter 方法可能不存在或拼写错误

**解决**:
- 检查 Player.java 中是否有所有必要的 setter
- 确保 RewardApplier 中的方法调用与 Player 的 setter 匹配

---

## 性能优化建议

1. **使用 ViewHolder 模式**: 如果有多张卡片，重用 views
2. **异步加载**: 在线程中进行重的计算
3. **内存管理**: 及时释放对话框资源

---

**最后更新**: 2024年
**状态**: ✅ 就绪实现
