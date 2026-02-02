package survivor.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * @author John Draa
 */

@Component
@RequiredArgsConstructor
public class GameWs
{
    private final SimpMessagingTemplate broker;

    // Lobby broadcast
    public void lobby(long matchId, Object payload)
    {
        broker.convertAndSend("/topic/match." + matchId + ".lobby", payload);
    }

    // Lobby chat
    public void chat(long matchId, Object payload)
    {
        broker.convertAndSend("/topic/match." + matchId + ".chat", payload);
    }

    // Gameplay broadcast (snapshots/events)
    public void game(long matchId, Object payload)
    {
        broker.convertAndSend("/topic/match." + matchId + ".game", payload);
    }

    // Per-user queue (requires an authenticated Principal whose name == accountId string)
    // If you don't have auth yet, prefer game(...) broadcast instead.
    public void toPlayer(long matchId, int accountId, Object payload)
    {
        broker.convertAndSendToUser(String.valueOf(accountId),
                "/queue/match." + matchId + ".game", payload);
    }
}
