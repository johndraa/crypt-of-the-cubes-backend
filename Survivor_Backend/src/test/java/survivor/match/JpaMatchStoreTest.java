package survivor.match;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import survivor.characters.GameCharacter;
import survivor.characters.GameCharacterRepository;
import survivor.characters.UserCharacterUnlockRepository;
import survivor.progress.UserProgressRepository;
import survivor.ws.dto.LobbyPlayer;
import survivor.ws.dto.ParticipantResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class JpaMatchStoreTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchParticipantRepository participantRepository;

    @Mock
    private GameCharacterRepository characterRepository;

    @Mock
    private UserProgressRepository progressRepository;

    @Mock
    private UserCharacterUnlockRepository unlockRepository;

    @InjectMocks
    private JpaMatchStore matchStore;

    private Match testMatch;
    private GameCharacter wanderer;

    @Before
    public void setUp() {
        testMatch = Match.builder()
            .id(1L)
            .joinCode("ABC123")
            .status(MatchStatus.LOBBY)
            .build();

        wanderer = new GameCharacter();
        wanderer.setId(1);
        wanderer.setCode("WANDERER");
    }

    /**
     * Test: createLobby()
     * Coverage: JpaMatchStore.createLobby(), genCode()
     * Strategy: White-box, branch coverage
     * Equivalence: Valid creation
     */
    @Test
    public void testCreateLobby_GeneratesUniqueJoinCode() {
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> {
            Match m = invocation.getArgument(0);
            m.setId(1L);
            return m;
        });

        Match created = matchStore.createLobby();

        assertNotNull(created);
        assertNotNull(created.getJoinCode());
        assertEquals(6, created.getJoinCode().length());
        assertEquals(MatchStatus.LOBBY, created.getStatus());
        verify(matchRepository).save(any(Match.class));
    }

    /**
     * Test: markActive()
     * Coverage: JpaMatchStore.markActive()
     * Strategy: White-box, branch coverage
     * Equivalence: Valid match ID
     */
    @Test
    public void testMarkActive_UpdatesMatchStatus() {
        when(matchRepository.findById(1L)).thenReturn(Optional.of(testMatch));
        when(matchRepository.save(any(Match.class))).thenReturn(testMatch);

        Instant startTime = Instant.now();
        matchStore.markActive(1L, startTime);

        assertEquals(MatchStatus.ACTIVE, testMatch.getStatus());
        assertEquals(startTime, testMatch.getStartedAt());
        verify(matchRepository).save(testMatch);
    }

    /**
     * Test: markEnded()
     * Coverage: JpaMatchStore.markEnded() - with winner
     * Strategy: White-box, branch coverage
     * Equivalence: Match with winner
     */
    @Test
    public void testMarkEnded_WithWinner_UpdatesMatch() {
        when(matchRepository.findById(1L)).thenReturn(Optional.of(testMatch));
        when(matchRepository.save(any(Match.class))).thenReturn(testMatch);

        Instant endTime = Instant.now();
        Integer winnerId = 100;

        matchStore.markEnded(1L, winnerId, endTime);

        assertEquals(MatchStatus.ENDED, testMatch.getStatus());
        assertEquals(winnerId, testMatch.getWinnerAccountId());
        assertEquals(endTime, testMatch.getEndedAt());
        verify(matchRepository).save(testMatch);
    }

    /**
     * Test: markEnded()
     * Coverage: JpaMatchStore.markEnded() - without winner
     * Strategy: White-box, branch coverage (null winner)
     * Equivalence: Match without winner
     */
    @Test
    public void testMarkEnded_WithoutWinner_UpdatesMatch() {
        when(matchRepository.findById(1L)).thenReturn(Optional.of(testMatch));
        when(matchRepository.save(any(Match.class))).thenReturn(testMatch);

        Instant endTime = Instant.now();

        matchStore.markEnded(1L, null, endTime);

        assertEquals(MatchStatus.ENDED, testMatch.getStatus());
        assertNull(testMatch.getWinnerAccountId());
        assertEquals(endTime, testMatch.getEndedAt());
    }

    /**
     * Test: join()
     * Coverage: JpaMatchStore.join() - new participant
     * Strategy: White-box, branch coverage
     * Equivalence: Valid join, participant doesn't exist
     */
    @Test
    public void testJoin_NewParticipant_AddsParticipant() {
        when(participantRepository.existsByMatchIdAndAccountId(1L, 100)).thenReturn(false);
        when(participantRepository.findByMatchId(1L)).thenReturn(List.of());
        when(matchRepository.findById(1L)).thenReturn(Optional.of(testMatch));
        when(characterRepository.findByCodeIgnoreCase("WANDERER")).thenReturn(Optional.of(wanderer));
        when(participantRepository.save(any(MatchParticipant.class))).thenAnswer(invocation -> {
            MatchParticipant p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        matchStore.join(1L, 100);

        verify(participantRepository).save(any(MatchParticipant.class));
    }

    /**
     * Test: join()
     * Coverage: JpaMatchStore.join() - already exists branch
     * Strategy: White-box, branch coverage
     * Equivalence: Participant already exists
     */
    @Test
    public void testJoin_AlreadyExists_DoesNotAdd() {
        when(participantRepository.existsByMatchIdAndAccountId(1L, 100)).thenReturn(true);

        matchStore.join(1L, 100);

        verify(participantRepository, never()).save(any(MatchParticipant.class));
    }

    /**
     * Test: join()
     * Coverage: JpaMatchStore.join() - max 4 participants branch
     * Strategy: White-box, boundary (4 participants = max)
     * Equivalence: Match at capacity
     */
    @Test
    public void testJoin_MaxParticipants_DoesNotAdd() {
        when(participantRepository.existsByMatchIdAndAccountId(1L, 100)).thenReturn(false);
        // Create 4 active participants
        List<MatchParticipant> participants = List.of(
            createParticipant(1L, 1, null),
            createParticipant(1L, 2, null),
            createParticipant(1L, 3, null),
            createParticipant(1L, 4, null)
        );
        when(participantRepository.findByMatchId(1L)).thenReturn(participants);

        matchStore.join(1L, 100);

        verify(participantRepository, never()).save(any(MatchParticipant.class));
    }

    /**
     * Test: leave()
     * Coverage: JpaMatchStore.leave()
     * Strategy: White-box, branch coverage
     * Equivalence: Participant exists
     */
    @Test
    public void testLeave_UpdatesLeftAt() {
        MatchParticipant participant = createParticipant(1L, 100, null);
        when(participantRepository.findByMatchId(1L)).thenReturn(List.of(participant));
        when(participantRepository.save(any(MatchParticipant.class))).thenReturn(participant);

        matchStore.leave(1L, 100);

        assertNotNull(participant.getLeftAt());
        verify(participantRepository).save(participant);
    }

    /**
     * Test: setReady()
     * Coverage: JpaMatchStore.setReady() - true branch
     * Strategy: White-box, branch coverage
     * Equivalence: Active participant
     */
    @Test
    public void testSetReady_ActiveParticipant_UpdatesReady() {
        MatchParticipant participant = createParticipant(1L, 100, null);
        when(participantRepository.findByMatchId(1L)).thenReturn(List.of(participant));
        when(participantRepository.findById(participant.getId())).thenReturn(Optional.of(participant));
        when(participantRepository.save(any(MatchParticipant.class))).thenReturn(participant);

        matchStore.setReady(1L, 100, true);

        assertTrue(participant.getReady());
        verify(participantRepository).save(participant);
    }

    /**
     * Test: setReady()
     * Coverage: JpaMatchStore.setReady() - participant not found branch
     * Strategy: White-box, branch coverage
     * Equivalence: Participant doesn't exist
     */
    @Test
    public void testSetReady_ParticipantNotFound_NoUpdate() {
        when(participantRepository.findByMatchId(1L)).thenReturn(List.of());

        matchStore.setReady(1L, 100, true);

        verify(participantRepository, never()).save(any(MatchParticipant.class));
    }

    /**
     * Test: lockCharacter()
     * Coverage: JpaMatchStore.lockCharacter() - WANDERER branch
     * Strategy: White-box, branch coverage
     * Equivalence: WANDERER character (always allowed)
     */
    @Test
    public void testLockCharacter_Wanderer_AlwaysAllowed() {
        MatchParticipant participant = createParticipant(1L, 100, null);
        when(participantRepository.findByMatchId(1L)).thenReturn(List.of(participant));
        when(participantRepository.findById(participant.getId())).thenReturn(Optional.of(participant));
        when(characterRepository.findByCodeIgnoreCase("WANDERER")).thenReturn(Optional.of(wanderer));
        when(participantRepository.save(any(MatchParticipant.class))).thenReturn(participant);

        boolean result = matchStore.lockCharacter(1L, 100, "WANDERER");

        assertTrue(result);
        assertEquals("WANDERER", participant.getSelectedCharacterCode());
        verify(participantRepository).save(participant);
    }

    /**
     * Test: lockCharacter()
     * Coverage: JpaMatchStore.lockCharacter() - null/blank code branch
     * Strategy: White-box, branch coverage, boundary (null/blank)
     * Equivalence: Null or blank character code
     */
    @Test
    public void testLockCharacter_NullCode_ClearsSelection() {
        MatchParticipant participant = createParticipant(1L, 100, null);
        participant.setSelectedCharacterCode("WANDERER");
        when(participantRepository.findByMatchId(1L)).thenReturn(List.of(participant));
        when(participantRepository.findById(participant.getId())).thenReturn(Optional.of(participant));
        when(participantRepository.save(any(MatchParticipant.class))).thenReturn(participant);

        boolean result = matchStore.lockCharacter(1L, 100, null);

        assertTrue(result);
        assertNull(participant.getSelectedCharacterCode());
        assertFalse(participant.getReady());
    }

    /**
     * Test: lockCharacter()
     * Coverage: JpaMatchStore.lockCharacter() - character not owned branch
     * Strategy: White-box, branch coverage
     * Equivalence: Character exists but not owned
     */
    @Test
    public void testLockCharacter_NotOwned_ReturnsFalse() {
        MatchParticipant participant = createParticipant(1L, 100, null);
        GameCharacter character = new GameCharacter();
        character.setId(2);
        character.setCode("PREMIUM");

        when(participantRepository.findByMatchId(1L)).thenReturn(List.of(participant));
        when(participantRepository.findById(participant.getId())).thenReturn(Optional.of(participant));
        when(characterRepository.findByCodeIgnoreCase("PREMIUM")).thenReturn(Optional.of(character));
        when(unlockRepository.existsByAccount_IdAndCharacter_Id(100, 2)).thenReturn(false);

        boolean result = matchStore.lockCharacter(1L, 100, "PREMIUM");

        assertFalse(result);
        verify(participantRepository, never()).save(any(MatchParticipant.class));
    }

    /**
     * Test: snapshot()
     * Coverage: JpaMatchStore.snapshot()
     * Strategy: White-box, branch coverage
     * Equivalence: Match with active participants
     */
    @Test
    public void testSnapshot_ReturnsActiveParticipants() {
        MatchParticipant p1 = createParticipant(1L, 100, null);
        p1.setSelectedCharacterCode("WANDERER");
        p1.setReady(true);

        MatchParticipant p2 = createParticipant(1L, 200, null);
        p2.setSelectedCharacterCode("WANDERER");
        p2.setReady(false);

        MatchParticipant p3 = createParticipant(1L, 300, Instant.now()); // Left

        when(participantRepository.findByMatchId(1L)).thenReturn(List.of(p1, p2, p3));

        List<LobbyPlayer> snapshot = matchStore.snapshot(1L);

        assertEquals(2, snapshot.size());
        assertTrue(snapshot.stream().anyMatch(lp -> lp.accountId() == 100 && lp.ready()));
        assertTrue(snapshot.stream().anyMatch(lp -> lp.accountId() == 200 && !lp.ready()));
    }

    /**
     * Test: writeResults()
     * Coverage: JpaMatchStore.writeResults()
     * Strategy: White-box, branch coverage
     * Equivalence: Valid results list
     */
    @Test
    public void testWriteResults_UpdatesParticipantsAndProgress() {
        MatchParticipant participant = createParticipant(1L, 100, null);
        when(participantRepository.findByMatchId(1L)).thenReturn(List.of(participant));
        when(participantRepository.save(any(MatchParticipant.class))).thenReturn(participant);

        survivor.progress.UserProgress progress = new survivor.progress.UserProgress();
        progress.setCoins(0);
        progress.setTotalScore(0);
        when(progressRepository.findByAccount_Id(100)).thenReturn(Optional.of(progress));
        when(progressRepository.save(any(survivor.progress.UserProgress.class))).thenReturn(progress);

        List<ParticipantResult> results = List.of(
            new ParticipantResult(100, 1000, 50, 5, 120000L)
        );

        matchStore.writeResults(1L, results);

        assertEquals(Integer.valueOf(1000), participant.getScore());
        assertEquals(Integer.valueOf(50), participant.getCoinsEarned());
        assertEquals(Long.valueOf(120000L), participant.getTimeAliveMs());
        verify(participantRepository).save(participant);
        verify(progressRepository).save(progress);
    }

    private MatchParticipant createParticipant(long matchId, int accountId, Instant leftAt) {
        MatchParticipant p = MatchParticipant.builder()
            .match(testMatch)
            .accountId(accountId)
            .joinedAt(Instant.now())
            .ready(false)
            .leftAt(leftAt)
            .build();
        p.setId((long) accountId);
        return p;
    }
}