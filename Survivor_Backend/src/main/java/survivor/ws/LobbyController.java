package survivor.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import survivor.match.LobbySocketHandler;
import survivor.ws.dto.*;

@Controller
@RequiredArgsConstructor
public class LobbyController
{
    private final LobbySocketHandler handler;

    @MessageMapping("/lobby.join")
    public void onJoin(JoinMsg msg) {
        handler.onJoin(msg);
    }

    @MessageMapping("/lobby.leave")
    public void onLeave(LeaveMsg msg) {
        handler.onLeave(msg);
    }

    @MessageMapping("/lobby.ready")
    public void onReady(ReadyMsg msg) {
        handler.onReady(msg);
    }

    @MessageMapping("/lobby.select")
    public void onSelect(SelectMsg msg) {
        handler.onSelect(msg);
    }

    @MessageMapping("/lobby.chat")
    public void onChat(ChatMessage msg) {
        handler.onChat(msg);
    }

    @MessageMapping("/lobby.start")
    public void onRequestStart(RequestStartMsg msg) {
        handler.onRequestStart(msg);
    }
}
