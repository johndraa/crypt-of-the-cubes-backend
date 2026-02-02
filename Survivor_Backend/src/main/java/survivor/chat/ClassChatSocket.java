package survivor.chat;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class-style WebSocket chat (no STOMP), scoped per matchId.
 * URL: ws://host:port/classchat/{matchId}/{username}
 */
@Slf4j
@Component
@ServerEndpoint(value = "/classchat/{matchId}/{username}")
public class ClassChatSocket
{

    // ---- Wire Spring beans into this static field pattern (as in class demo) ----
    private static MessageRepository messageRepo;

    @Autowired
    public void setMessageRepository(MessageRepository repo)
    {
        ClassChatSocket.messageRepo = repo;
    }

    // ---- Session maps ----
    // Map session -> (matchId, username)
    private static final Map<Session, Client> sessionInfo = new ConcurrentHashMap<>();
    // Map matchId -> username -> session (room-scoped registry)
    private static final Map<Long, Map<String, Session>> rooms = new ConcurrentHashMap<>();

    private record Client(long matchId, String username) {}

    @OnOpen
    public void onOpen(Session session,
                       @PathParam("matchId") long matchId,
                       @PathParam("username") String username) throws IOException {

        log.info("Chat open: match={} user={}", matchId, username);

        sessionInfo.put(session, new Client(matchId, username));
        rooms.computeIfAbsent(matchId, k -> new ConcurrentHashMap<>()).put(username, session);

        // Send recent history (last 50)
        String history = getChatHistory(matchId);
        sendToUser(session, history);

        // Broadcast join message to the room
        broadcast(matchId, "System: " + username + " joined the lobby chat");
    }

    @OnMessage
    public void onMessage(Session session, String message)
    {
        Client c = sessionInfo.get(session);
        if (c == null) return; // defensive

        String user = c.username;
        long matchId = c.matchId;

        log.info("Chat msg: match={} user={} text={}", matchId, user, message);

        try
        {
            if (message != null && message.startsWith("@"))
            {
                // DM format: @username message...
                String[] parts = message.split("\\s+", 2);
                if (parts.length >= 2)
                {
                    String dest = parts[0].substring(1); // strip '@'
                    String body = parts[1];

                    // send to sender and receiver if receiver exists in the room
                    Session destSess = rooms.getOrDefault(matchId, Map.of()).get(dest);
                    String framed = "[DM] " + user + ": " + body;

                    // always echo to sender
                    sendToUser(session, framed);
                    if (destSess != null) sendToUser(destSess, framed);

                    // Save the DM content too (optional—still part of history)
                    messageRepo.save(new Message(matchId, user, "[DM->" + dest + "] " + body));
                    return;
                }
            }

            // Normal broadcast
            String framed = user + ": " + message;
            broadcast(matchId, framed);

            // Persist
            messageRepo.save(new Message(matchId, user, message));

        }
        catch (Exception e)
        {
            log.warn("Chat error handling message", e);
            sendToUser(session, "System: error processing message");
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason)
    {
        Client c = sessionInfo.remove(session);
        if (c == null) return;

        rooms.computeIfPresent(c.matchId, (mid, map) ->
        {
            map.remove(c.username);
            return map.isEmpty() ? null : map;
        });

        log.info("Chat close: match={} user={} reason={}", c.matchId, c.username, reason);
        broadcast(c.matchId, "System: " + c.username + " left the lobby chat");
    }

    @OnError
    public void onError(Session session, Throwable t)
    {
        log.warn("Chat socket error", t);
        if (session != null) sendToUser(session, "System: error");
    }

    // ---- helpers ----
    private static void sendToUser(Session sess, String text)
    {
        try
        {
            sess.getBasicRemote().sendText(text);
        }
        catch (IOException e)
        {
            log.warn("sendToUser failed", e);
        }
    }

    private static void broadcast(long matchId, String text)
    {
        Map<String, Session> room = rooms.get(matchId);
        if (room == null) return;
        for (Session s : room.values())
        {
            sendToUser(s, text);
        }
    }

    private static String getChatHistory(long matchId)
    {
        var recent = messageRepo.findRecentByMatchId(matchId, PageRequest.of(0, 50));
        var sb = new StringBuilder();
        // we queried newest first; reverse for chronological display
        for (int i = recent.size() - 1; i >= 0; i--)
        {
            var m = recent.get(i);
            sb.append(m.getUserName()).append(": ").append(m.getContent()).append("\n");
        }
        return sb.toString();
    }
}