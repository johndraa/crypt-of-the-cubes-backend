package survivor.match;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import survivor.accounts.Account;
import survivor.accounts.AccountRepository;
import survivor.exceptions.NotFoundException;
import survivor.runtime.MatchRuntimeRegistry;
import survivor.ws.dto.LobbyPlayer;
import survivor.ws.dto.ParticipantResult;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test"})
public class MatchControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchParticipantRepository participantRepository;

    @Autowired
    private MatchStore matchStore;

    @Autowired
    private MatchRuntimeRegistry registry;

    @Autowired
    private AccountRepository accountRepository;

    @Before
    public void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        // Clean up test data
        participantRepository.deleteAll();
        matchRepository.deleteAll();
        accountRepository.deleteAll();
    }

    /**
     * Test: POST /matches/create
     * Coverage: MatchController.create()
     * Strategy: Black-box, functional
     * Equivalence: Valid request
     */
    @Test
    public void testCreateMatch_ReturnsMatchWithJoinCode() {
        given()
            .when()
                .post("/matches/create")
            .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("joinCode", notNullValue())
                .body("joinCode", matchesPattern("[A-Z0-9]{6}"))
                .body("status", equalTo("LOBBY"));
    }

    /**
     * Test: POST /matches/join/{joinCode}/{accountId}
     * Coverage: MatchController.join() - success path
     * Strategy: Black-box, functional
     * Equivalence: Valid join code and account ID
     */
    @Test
    public void testJoinMatch_WithValidJoinCode_ReturnsMatchInfo() {
        // Create account
        Account account = new Account();
        account.setEmail("test@example.com");
        account.setUsername("testuser");
        account.setPassword("password");
        account = accountRepository.save(account);

        // Create match
        Match match = matchStore.createLobby();
        String joinCode = match.getJoinCode();

        given()
            .pathParam("joinCode", joinCode)
            .pathParam("accountId", account.getId())
        .when()
            .post("/matches/join/{joinCode}/{accountId}")
        .then()
            .statusCode(200)
            .body("matchId", equalTo(match.getId().intValue()))
            .body("joinCode", equalTo(joinCode));
    }

    /**
     * Test: POST /matches/join/{joinCode}/{accountId}
     * Coverage: MatchController.join() - NotFoundException branch
     * Strategy: Black-box, error path, boundary (invalid join code)
     * Equivalence: Invalid join code
     */
    @Test
    public void testJoinMatch_WithInvalidJoinCode_Returns404() {
        Account account = new Account();
        account.setEmail("test@example.com");
        account.setUsername("testuser");
        account.setPassword("password");
        account = accountRepository.save(account);

        given()
            .pathParam("joinCode", "INVALID")
            .pathParam("accountId", account.getId())
        .when()
            .post("/matches/join/{joinCode}/{accountId}")
        .then()
            .statusCode(404)
            .body("message", equalTo("match not found"));
    }

    /**
     * Test: POST /matches/{matchId}/end
     * Coverage: MatchController.end() - with winner
     * Strategy: Black-box, functional
     * Equivalence: Valid match ID with winner
     */
    @Test
    public void testEndMatch_WithWinner_ReturnsOk() {
        Match match = matchStore.createLobby();
        Account account = new Account();
        account.setEmail("test@example.com");
        account.setUsername("testuser");
        account.setPassword("password");
        account = accountRepository.save(account);

        matchStore.join(match.getId(), account.getId());

        given()
            .pathParam("matchId", match.getId())
            .contentType(ContentType.JSON)
            .body(Map.of("winnerAccountId", account.getId()))
        .when()
            .post("/matches/{matchId}/end")
        .then()
            .statusCode(200)
            .body("ok", equalTo(true));

        Match ended = matchRepository.findById(match.getId()).orElseThrow();
        assertEquals(MatchStatus.ENDED, ended.getStatus());
        assertEquals(account.getId(), ended.getWinnerAccountId());
        assertNotNull(ended.getEndedAt());
    }

    /**
     * Test: POST /matches/{matchId}/end
     * Coverage: MatchController.end() - without winner (null req)
     * Strategy: Black-box, boundary (null request body)
     * Equivalence: Valid match ID without winner
     */
    @Test
    public void testEndMatch_WithoutWinner_ReturnsOk() {
        Match match = matchStore.createLobby();

        given()
            .pathParam("matchId", match.getId())
            .contentType(ContentType.JSON)
            .body(Map.of())  // Empty map instead of empty string
        .when()
            .post("/matches/{matchId}/end")
        .then()
            .statusCode(200)
            .body("ok", equalTo(true));

        Match ended = matchRepository.findById(match.getId()).orElseThrow();
        assertEquals(MatchStatus.ENDED, ended.getStatus());
        assertNull(ended.getWinnerAccountId());
    }

    /**
     * Test: POST /matches/{matchId}/stop
     * Coverage: MatchController.stop() - success path
     * Strategy: White-box, branch coverage
     * Equivalence: Match with active runtime
     */
    @Test
    public void testStopMatch_WithActiveRuntime_StopsAndRemoves() {
        Match match = matchStore.createLobby();
        // Create runtime
        survivor.config.FogConfig fog = new survivor.config.FogConfig();
        fog.setLight(10);
        fog.setWake(12);
        fog.setSleep(14);
        registry.createRuntime(match.getId(), fog);

        assertTrue("Runtime should exist", registry.get(match.getId()).isPresent());

        given()
            .pathParam("matchId", match.getId())
        .when()
            .post("/matches/{matchId}/stop")
        .then()
            .statusCode(200)
            .body("ok", equalTo(true))
            .body("message", containsString("stopped"));

        assertFalse("Runtime should be removed", registry.get(match.getId()).isPresent());
        Match stopped = matchRepository.findById(match.getId()).orElseThrow();
        assertEquals(MatchStatus.ENDED, stopped.getStatus());
    }

    /**
     * Test: POST /matches/{matchId}/stop
     * Coverage: MatchController.stop() - runtime not found branch
     * Strategy: White-box, branch coverage, error path
     * Equivalence: Match without runtime
     */
    @Test
    public void testStopMatch_WithoutRuntime_ReturnsOkButMessageIndicatesNotFound() {
        Match match = matchStore.createLobby();

        given()
            .pathParam("matchId", match.getId())
        .when()
            .post("/matches/{matchId}/stop")
        .then()
            .statusCode(200)
            .body("ok", equalTo(false))
            .body("message", containsString("not found"));
    }

    /**
     * Test: POST /matches/{matchId}/results
     * Coverage: MatchController.results()
     * Strategy: Black-box, functional
     * Equivalence: Valid results list
     */
    @Test
    public void testWriteResults_WithValidResults_ReturnsOk() {
        Match match = matchStore.createLobby();
        Account account = new Account();
        account.setEmail("test@example.com");
        account.setUsername("testuser");
        account.setPassword("password");
        account = accountRepository.save(account);
        matchStore.join(match.getId(), account.getId());

        List<ParticipantResult> results = List.of(
            new ParticipantResult(account.getId(), 1000, 50, 5, 120000L)
        );

        given()
            .pathParam("matchId", match.getId())
            .contentType(ContentType.JSON)
            .body(results)
        .when()
            .post("/matches/{matchId}/results")
        .then()
            .statusCode(200)
            .body("ok", equalTo(true));
    }

    /**
     * Test: GET /matches/{matchId}/lobby
     * Coverage: MatchController.lobby()
     * Strategy: Black-box, functional
     * Equivalence: Match with participants
     */
    @Test
    public void testGetLobby_WithParticipants_ReturnsLobbyPlayers() {
        Match match = matchStore.createLobby();
        Account account1 = new Account();
        account1.setEmail("test1@example.com");
        account1.setUsername("testuser1");
        account1.setPassword("password");
        account1 = accountRepository.save(account1);

        Account account2 = new Account();
        account2.setEmail("test2@example.com");
        account2.setUsername("testuser2");
        account2.setPassword("password");
        account2 = accountRepository.save(account2);

        matchStore.join(match.getId(), account1.getId());
        matchStore.join(match.getId(), account2.getId());

        given()
            .pathParam("matchId", match.getId())
        .when()
            .get("/matches/{matchId}/lobby")
        .then()
            .statusCode(200)
            .body("size()", equalTo(2))
            .body("accountId", hasItems(account1.getId(), account2.getId()));
    }
}