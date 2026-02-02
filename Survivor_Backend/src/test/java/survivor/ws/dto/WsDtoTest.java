package survivor.ws.dto;

import org.junit.Test;
import survivor.match.MatchStatus;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test: WS DTO records
 * Coverage: All DTO constructors and accessors
 * Strategy: Black-box, simple validation
 */
public class WsDtoTest {

    @Test
    public void testJoinMsg() {
        JoinMsg msg = new JoinMsg(1L, 100);
        assertEquals(1L, msg.matchId());
        assertEquals(100, msg.accountId());
    }

    @Test
    public void testLeaveMsg() {
        LeaveMsg msg = new LeaveMsg(1L, 100);
        assertEquals(1L, msg.matchId());
        assertEquals(100, msg.accountId());
    }

    @Test
    public void testReadyMsg() {
        ReadyMsg msg = new ReadyMsg(1L, 100, true);
        assertEquals(1L, msg.matchId());
        assertEquals(100, msg.accountId());
        assertTrue(msg.ready());
    }

    @Test
    public void testSelectMsg() {
        SelectMsg msg = new SelectMsg(1L, 100, "WANDERER", 1);
        assertEquals(1L, msg.matchId());
        assertEquals(100, msg.accountId());
        assertEquals("WANDERER", msg.characterCode());
        assertEquals(Integer.valueOf(1), msg.characterId());
    }

    @Test
    public void testLobbyPlayer() {
        LobbyPlayer player = new LobbyPlayer(100, "WANDERER", true);
        assertEquals(100, player.accountId());
        assertEquals("WANDERER", player.characterCode());
        assertTrue(player.ready());
    }

    @Test
    public void testParticipantResult() {
        ParticipantResult result = new ParticipantResult(100, 1000, 50, 5, 120000L);
        assertEquals(100, result.accountId());
        assertEquals(1000, result.score());
        assertEquals(50, result.coins());
        assertEquals(5, result.kills());
        assertEquals(120000L, result.timeAliveMs());
    }

    @Test
    public void testUpgradePickDTO() {
        UpgradePickDTO dto = new UpgradePickDTO(100, "DAMAGE_UP");
        assertEquals(100, dto.playerId());
        assertEquals("DAMAGE_UP", dto.selectedUpgrade());
    }

    @Test
    public void testInputMsg() {
        InputMsg msg = new InputMsg(1L, 100, 0.5f, 0.3f, 123L);
        assertEquals(1L, msg.matchId());
        assertEquals(100, msg.accountId());
        assertEquals(0.5f, msg.moveX(), 0.001f);
        assertEquals(0.3f, msg.moveY(), 0.001f);
        assertEquals(123L, msg.seq());
    }

    @Test
    public void testChatMessage() {
        Instant now = Instant.now();
        ChatMessage msg = new ChatMessage(1L, 100, "testuser", "Hello", now);
        assertEquals(1L, msg.matchId());
        assertEquals(100, msg.accountId());
        assertEquals("testuser", msg.username());
        assertEquals("Hello", msg.text());
        assertEquals(now, msg.sentAt());
    }

    @Test
    public void testRequestStartMsg() {
        RequestStartMsg msg = new RequestStartMsg(1L);
        assertEquals(1L, msg.matchId());
    }

    @Test
    public void testStartMsg() {
        Fog fog = new Fog(10, 12, 14);
        StartMsg msg = new StartMsg(1L, fog, 3);
        assertEquals(1L, msg.matchId());
        assertEquals(fog, msg.fog());
        assertEquals(3, msg.countdown());
    }

    @Test
    public void testLobbySnapshot() {
        List<LobbyPlayer> players = List.of(
            new LobbyPlayer(100, "WANDERER", true)
        );
        LobbySnapshot snapshot = new LobbySnapshot(1L, MatchStatus.LOBBY, players);
        assertEquals(1L, snapshot.matchId());
        assertEquals(MatchStatus.LOBBY, snapshot.status());
        assertEquals(1, snapshot.players().size());
    }

    @Test
    public void testFog() {
        Fog fog = new Fog(10, 12, 14);
        assertEquals(10, fog.light());
        assertEquals(12, fog.wake());
        assertEquals(14, fog.sleep());
    }
}