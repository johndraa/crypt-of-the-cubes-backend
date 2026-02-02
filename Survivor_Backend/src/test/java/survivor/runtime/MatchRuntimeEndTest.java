package survivor.runtime;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import survivor.combat.Stats;
import survivor.combat.Vec2;
import survivor.match.Match;
import survivor.match.MatchRepository;
import survivor.match.MatchStore;
import survivor.match.MatchStatus;
import survivor.model.PlayerState;
import survivor.shared.AttackStyle;

import java.time.Instant;

import static org.junit.Assert.*;

/**
 * Integration tests for match ending when all players die.
 * 
 * Coverage Goals:
 * - MatchRuntime.tick() - match end condition (alivePlayers < 1)
 * - TickService.handleMatchEnd() - match end handling
 * - MatchDelta.shouldEnd() - end flag propagation
 * 
 * Strategy: Integration testing with MatchRuntime, validating end condition
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles({"test"})
public class MatchRuntimeEndTest {

    @Autowired
    private MatchRuntimeRegistry registry;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchStore matchStore;

    private MatchRuntime matchRuntime;
    private Match match;
    private long matchId;

    @Before
    public void setUp() {
        // Create Match entity in database using MatchStore (generates valid join code)
        // DO NOT set ID manually - let Hibernate generate it
        match = matchStore.createLobby();
        
        // Mark as ACTIVE (required for match runtime)
        matchStore.markActive(match.getId(), Instant.now());
        
        // Refresh from database to get updated status
        match = matchRepository.findById(match.getId()).orElseThrow();
        
        // Use the generated ID for the runtime
        matchId = match.getId();

        // Create runtime with the generated match ID
        survivor.config.FogConfig fogConfig = new survivor.config.FogConfig();
        fogConfig.setLight(10);
        fogConfig.setWake(12);
        fogConfig.setSleep(14);
        registry.createRuntime(matchId, fogConfig);
        matchRuntime = registry.get(matchId).orElseThrow();
        matchRuntime.start();
    }

    /**
     * Test: Match ends when all players die
     * Coverage: MatchRuntime.tick() - shouldEnd branch, TickService.handleMatchEnd()
     * Strategy: Integration, end-to-end flow
     * Equivalence: All players dead
     * Branches: alivePlayers < 1 branch, match end handling branch
     * Why: Tests critical match end condition when all players eliminated
     */
    @Test
    public void testMatchEndsWhenAllPlayersDie() {
        // Create and add players
        Stats baseStats = new Stats(100, 50, 30, 20, 5, 10);
        PlayerState player1 = new PlayerState(1, new Vec2(100.0, 100.0), baseStats, AttackStyle.AOE, baseStats.health());
        PlayerState player2 = new PlayerState(2, new Vec2(200.0, 200.0), baseStats, AttackStyle.AOE, baseStats.health());
        
        matchRuntime.addPlayer(player1);
        matchRuntime.addPlayer(player2);

        // Verify match is active
        assertFalse("Match should not be ended initially", matchRuntime.isEnded());
        assertTrue("Match should be started", matchRuntime.isStarted());

        // Kill all players
        player1.applyDamage(1000);
        player2.applyDamage(1000);

        assertTrue("Player 1 should be dead", player1.isDead());
        assertTrue("Player 2 should be dead", player2.isDead());

        // Tick should detect match end
        MatchDelta delta = matchRuntime.tick();
        
        assertTrue("MatchDelta should indicate match should end", delta.shouldEnd());
        assertTrue("Match should be marked as ended", matchRuntime.isEnded());
    }

    /**
     * Test: Match continues when at least one player alive
     * Coverage: MatchRuntime.tick() - shouldEnd false branch
     * Strategy: Integration, negative case
     * Equivalence: At least one player alive
     * Branches: alivePlayers >= 1 branch (no end)
     * Why: Tests match continues when players remain alive
     */
    @Test
    public void testMatchContinuesWhenPlayersAlive() {
        Stats baseStats = new Stats(100, 50, 30, 20, 5, 10);
        PlayerState player1 = new PlayerState(1, new Vec2(100.0, 100.0), baseStats, AttackStyle.AOE, baseStats.health());
        PlayerState player2 = new PlayerState(2, new Vec2(200.0, 200.0), baseStats, AttackStyle.AOE, baseStats.health());
        
        matchRuntime.addPlayer(player1);
        matchRuntime.addPlayer(player2);

        // Kill only one player
        player1.applyDamage(1000);
        assertTrue("Player 1 should be dead", player1.isDead());
        assertFalse("Player 2 should be alive", player2.isDead());

        // Tick should NOT end match
        MatchDelta delta = matchRuntime.tick();
        
        assertFalse("MatchDelta should NOT indicate match should end", delta.shouldEnd());
        assertFalse("Match should NOT be marked as ended", matchRuntime.isEnded());
    }

    /**
     * Test: Match ends when last player dies
     * Coverage: MatchRuntime.tick() - boundary case (exactly 0 alive)
     * Strategy: Integration, boundary value (0 alive players)
     * Equivalence: Last player dies
     * Branches: alivePlayers == 0 branch
     * Why: Tests boundary condition for match end
     */
    @Test
    public void testMatchEndsWhenLastPlayerDies() {
        Stats baseStats = new Stats(100, 50, 30, 20, 5, 10);
        PlayerState player = new PlayerState(1, new Vec2(100.0, 100.0), baseStats, AttackStyle.AOE, baseStats.health());
        
        matchRuntime.addPlayer(player);

        // Verify player is alive
        assertFalse("Player should be alive", player.isDead());

        // Kill the last player
        player.applyDamage(1000);
        assertTrue("Player should be dead", player.isDead());

        // Tick should detect match end
        MatchDelta delta = matchRuntime.tick();
        
        assertTrue("MatchDelta should indicate match should end", delta.shouldEnd());
        assertTrue("Match should be marked as ended", matchRuntime.isEnded());
    }
}
