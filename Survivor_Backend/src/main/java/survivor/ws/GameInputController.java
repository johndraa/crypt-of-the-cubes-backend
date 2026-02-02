package survivor.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import survivor.match.MatchStore;
import survivor.runtime.MatchRuntimeRegistry;
import survivor.runtime.UserActionQueueService; // <-- add this import
import survivor.ws.dto.InputMsg;

@Controller
@RequiredArgsConstructor
public class GameInputController
{
    private final MatchRuntimeRegistry runtimes;
    private final MatchStore store; // optional: to validate the player is in the match
    private final UserActionQueueService userActions; // <-- add this field

    @MessageMapping("/match.input") // client sends to /app/match.input
    public void onInput(InputMsg m)
    {
        // TODO Optional membership guard
        //if (!store.isParticipant(m.matchId(), m.accountId())) return;

        int userId = m.accountId();
        long matchId = m.matchId();

        // enqueue the work for this user; runs FIFO on a single thread per user
        userActions.enqueue(userId, () ->
                runtimes.get(matchId).ifPresent(rt ->
                        rt.player(userId).ifPresent(p ->
                                p.setMove(clamp(m.moveX()), clamp(m.moveY()), m.seq())
                        )
                )
        );
    }

    private float clamp(float v)
    {
        if (v > 1f) return 1f;
        if (v < -1f) return -1f;
        return v;
    }
}
