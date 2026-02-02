package survivor.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import survivor.runtime.MatchRuntime;
import survivor.runtime.MatchRuntimeRegistry;
import survivor.runtime.UserActionQueueService;
import survivor.runtime.UpgradeType;
import survivor.ws.dto.UpgradePickDTO;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class UpgradeController {

    private final MatchRuntimeRegistry runtimes;
    private final UserActionQueueService userActions;
    private final GameWs ws;

    @MessageMapping("/upgrade.pick") // client sends to /app/upgrade.pick
    public void onUpgradePick(UpgradePickDTO msg) {
        int playerId = msg.playerId();

        // Serialize via per-user action queue (same pattern as movement)
        userActions.enqueue(playerId, () -> {

            // Find the match runtime that currently has this player
            Optional<MatchRuntime> rtOpt = runtimes.active().stream()
                    .filter(rt -> rt.player(playerId).isPresent())
                    .findFirst();

            if (rtOpt.isEmpty()) return;

            MatchRuntime rt = rtOpt.get();

            // #region agent log
        try (var fw = new java.io.FileWriter("C:\\Users\\jh_dr\\OneDrive\\Desktop\\School\\Git\\3_rasel_6\\Backend\\Survivor_Backend\\.cursor\\debug.log", true);
             var bw = new java.io.BufferedWriter(fw)) {
            String payload = "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H1\",\"location\":\"UpgradeController.java:onUpgradePick\",\"message\":\"upgrade_pick_received\",\"data\":{\"playerId\":" + playerId + ",\"selectedUpgrade\":\"" + msg.selectedUpgrade() + "\",\"matchId\":" + rt.id() + "},\"timestamp\":" + System.currentTimeMillis() + "}";
            bw.write(payload);
            bw.newLine();
            System.out.println(payload);
        } catch (Exception ignored) {}
            // #endregion

            rt.player(playerId).ifPresent(p -> {
                UpgradeType type;
                try {
                    type = UpgradeType.valueOf(msg.selectedUpgrade());
                } catch (IllegalArgumentException ex) {
                    // No cheat detection: just default something simple
                    type = UpgradeType.DAMAGE_UP;
                }

                // Apply upgrade (unfreezes player + removes invincibility)
                p.applyUpgrade(type);

                // Confirmation event to frontend
                var payload = new java.util.HashMap<String, Object>();
                payload.put("event", "upgradeApplied");
                payload.put("playerId", playerId);
                payload.put("selected", type.name());

                ws.game(rt.id(), payload);
            });
        });
    }
}
