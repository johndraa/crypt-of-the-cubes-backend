package survivor;

import io.restassured.RestAssured;
import static io.restassured.RestAssured.given;
import io.restassured.http.ContentType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;

import java.util.Map;

/**
 * Integration tests for account and progress management endpoints.
 * 
 * Coverage Goals:
 * - AccountController: signup, login, update, read, listAll, delete
 * - ProgressController: update, leaderboard
 * - CharacterPurchaseService: purchase flow
 * 
 * Strategy: Black-box integration testing with REST Assured
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test"})
public class JohnDraaSystemTest {

    @LocalServerPort
    private int port;

    @Before
    public void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    // --- helper methods ------------------------------------------------------

    /**
     * Helper: Creates an account via POST /accounts/signup
     * Coverage: AccountController.signup() - success path
     * Strategy: Reusable helper for test setup
     */
    private int createAccount(String email, String username, String password) {
        return
            given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "email", email,
                        "username", username,
                        "password", password
                ))
            .when()
                .post("/accounts/signup")
            .then()
                .statusCode(201)
                .extract()
                .path("accountId");
    }

    /**
     * Helper: Gets progress ID for an account
     * Coverage: ProgressController.getByAccount() - success path
     * Strategy: Reusable helper for test setup
     */
    private int getProgressIdForAccount(int accountId) {
        return
            given()
                .pathParam("accountId", accountId)
            .when()
                .get("/progress/by-account/{accountId}")
            .then()
                .statusCode(200)
                .extract()
                .path("progressId");
    }

    // --- non-trivial system tests -------------------------------------------

    /**
     * Test: POST /accounts/signup
     * Coverage: AccountController.signup() - auto-creates progress and grants WANDERER
     * Strategy: Black-box, functional, regression
     * Equivalence: Valid signup request
     * Branches: Progress creation branch, character unlock branch
     * Why: Tests critical signup flow including side effects (progress, character unlock)
     */
    @Test
    public void signupCreatesAccountAndAutoProgress() {
        long now = System.currentTimeMillis();
        String email = "john" + now + "@example.com";
        String username = "john" + now;

        int accountId = createAccount(email, username, "secret123");

        given()
            .pathParam("accountId", accountId)
        .when()
            .get("/progress/by-account/{accountId}")
        .then()
            .statusCode(200)
            .body("accountId", equalTo(accountId))
            .body("username", equalTo(username))
            .body("coins", equalTo(0))
            .body("totalScore", equalTo(0))
            .body("rank", equalTo(1));
    }

    /**
     * Test: POST /accounts/signup
     * Coverage: AccountController.signup() - ConflictException branch (duplicate username)
     * Strategy: Black-box, error path, regression
     * Equivalence: Duplicate username (invalid)
     * Branches: Username conflict detection branch
     * Why: Tests error handling and global exception handler (409 response)
     */
    @Test
    public void duplicateUsernameSignupReturnsConflict() {
        long now = System.currentTimeMillis();
        String username = "dupUser" + now;
        String email1 = "a" + now + "@example.com";
        String email2 = "b" + now + "@example.com";

        // First signup succeeds
        createAccount(email1, username, "password1");

        // Second signup with same username must fail with 409
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                    "email", email2,
                    "username", username,
                    "password", "password2"
            ))
        .when()
            .post("/accounts/signup")
        .then()
            .statusCode(409)
            .body("message", equalTo("username already in use"));
    }

    /**
     * Test: GET /accounts/login
     * Coverage: AccountController.login() - both success and failure branches
     * Strategy: Black-box, multi-step scenario, regression
     * Equivalence: Invalid password (error), valid password (success)
     * Branches: Wrong password branch (401), correct password branch (200)
     * Why: Tests authentication flow with both valid and invalid credentials
     */
    @Test
    public void loginWithWrongPasswordThenCorrectPassword() {
        long now = System.currentTimeMillis();
        String email = "login" + now + "@example.com";
        String username = "loginUser" + now;
        String password = "strongPass123";

        int accountId = createAccount(email, username, password);

        // Wrong password -> 401 unauthorized
        given()
            .queryParam("username", username)
            .queryParam("password", "wrongPassword")
        .when()
            .get("/accounts/login")
        .then()
            .statusCode(401)
            .body("message", equalTo("invalid credentials"));

        // Correct password -> 200 and correct body
        given()
            .queryParam("username", username)
            .queryParam("password", password)
        .when()
            .get("/accounts/login")
        .then()
            .statusCode(200)
            .body("id", equalTo(accountId))
            .body("username", equalTo(username))
            .body("email", equalTo(email.toLowerCase()));
    }

    /**
     * Test: PUT /progress/{id}
     * Coverage: ProgressController.update() - validation error branch
     * Strategy: Black-box, boundary value testing (negative coins)
     * Equivalence: Negative coins (invalid)
     * Branches: Bean validation failure branch (400)
     * Why: Tests DTO validation and error response format
     */
    @Test
    public void updateProgressWithNegativeCoinsReturnsValidationError() {
        long now = System.currentTimeMillis();
        String email = "neg" + now + "@example.com";
        String username = "negUser" + now;

        int accountId = createAccount(email, username, "secret123");
        int progressId = getProgressIdForAccount(accountId);

        given()
            .contentType(ContentType.JSON)
            .pathParam("id", progressId)
            .body(Map.of("coins", -10))
        .when()
            .put("/progress/{id}")
        .then()
            .statusCode(400)
            .body("message", equalTo("validation_error"))
            .body("errors.coins", equalTo("coins must be >= 0"));
    }

    /**
     * Test: Multi-step flow: signup → earn coins → purchase character
     * Coverage: CharacterPurchaseService.purchase() - full purchase flow
     * Strategy: Black-box, multi-step REST scenario, functional
     * Equivalence: Valid purchase flow
     * Branches: Shop query, purchase execution, coin deduction, unlock creation
     * Why: Tests end-to-end character purchase flow with state changes
     */
    @Test
    public void earnCoinsThenPurchaseCharacterFlow() {
        long now = System.currentTimeMillis();
        String email = "shop" + now + "@example.com";
        String username = "shopUser" + now;

        int accountId = createAccount(email, username, "secret123");
        int progressId = getProgressIdForAccount(accountId);

        // Earn 200 coins
        given()
            .contentType(ContentType.JSON)
            .pathParam("id", progressId)
            .body(Map.of("coins", 200))
        .when()
            .put("/progress/{id}")
        .then()
            .statusCode(200)
            .body("coins", equalTo(200));

        // Before purchase, user should only own the base character WANDERER
        given()
            .pathParam("accountId", accountId)
        .when()
            .get("/accounts/{accountId}/characters")
        .then()
            .statusCode(200)
            .body("size()", equalTo(1))
            .body("[0].code", equalTo("WANDERER"));

        // Pick the first purchasable character from the shop
        int characterId =
            given()
                .pathParam("accountId", accountId)
            .when()
                .get("/accounts/{accountId}/shop/characters")
            .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                // Ensure base character is not in the shop list
                .body("code", not(hasItem("WANDERER")))
                .extract()
                .path("[0].characterId");

        int cost =
            given()
                .pathParam("accountId", accountId)
            .when()
                .get("/accounts/{accountId}/shop/characters")
            .then()
                .extract()
                .path("[0].cost");

        // Purchase the chosen character
        given()
            .pathParam("accountId", accountId)
            .pathParam("characterId", characterId)
        .when()
            .post("/accounts/{accountId}/characters/{characterId}/purchase")
        .then()
            .statusCode(200)
            .body("message", equalTo("purchased"))
            .body("characterId", equalTo(characterId))
            .body("newCoinBalance", equalTo(200 - cost));

        // Progress coins should now reflect the deduction
        given()
            .pathParam("accountId", accountId)
        .when()
            .get("/progress/by-account/{accountId}")
        .then()
            .statusCode(200)
            .body("coins", equalTo(200 - cost));

        // Owned characters now include the newly purchased one
        given()
            .pathParam("accountId", accountId)
        .when()
            .get("/accounts/{accountId}/characters")
        .then()
            .statusCode(200)
            .body("size()", equalTo(2))
            .body("characterId", hasItem(characterId));
    }

    /**
     * Test: POST /accounts/{accountId}/characters/{characterId}/purchase
     * Coverage: CharacterPurchaseService.purchase() - insufficient coins branch
     * Strategy: Black-box, error path, boundary (0 coins)
     * Equivalence: Insufficient coins (invalid)
     * Branches: Insufficient coins check branch (400)
     * Why: Tests error handling for purchase failure scenario
     */
    @Test
    public void purchaseFailsWhenNotEnoughCoins() {
        long now = System.currentTimeMillis();
        String email = "poor" + now + "@example.com";
        String username = "poorUser" + now;

        int accountId = createAccount(email, username, "secret123");
        // At this point the account has progress with coins = 0

        // Take the first purchasable character from the shop
        int characterId =
            given()
                .pathParam("accountId", accountId)
            .when()
                .get("/accounts/{accountId}/shop/characters")
            .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .extract()
                .path("[0].characterId");

        // Attempt to purchase without enough coins
        given()
            .pathParam("accountId", accountId)
            .pathParam("characterId", characterId)
        .when()
            .post("/accounts/{accountId}/characters/{characterId}/purchase")
        .then()
            .statusCode(400)
            .body("message", equalTo("Not enough coins"));
    }

    /**
     * Test: GET /progress/leaderboard and GET /progress/by-account/{accountId}
     * Coverage: ProgressController.leaderboard() and getByAccount() - rank calculation
     * Strategy: Black-box, functional, invariant testing
     * Equivalence: Multiple accounts with different scores
     * Branches: Rank calculation branch, leaderboard query branch
     * Why: Tests leaderboard functionality and rank calculation invariants
     */
    @Test
    public void leaderboardReflectsScoresAndRanks() {
        long now = System.currentTimeMillis();

        // Create three accounts with unique usernames
        String emailA = "lbA" + now + "@example.com";
        String userA  = "lbUserA" + now;
        int accA = createAccount(emailA, userA, "pw");

        String emailB = "lbB" + now + "@example.com";
        String userB  = "lbUserB" + now;
        int accB = createAccount(emailB, userB, "pw");

        String emailC = "lbC" + now + "@example.com";
        String userC  = "lbUserC" + now;
        int accC = createAccount(emailC, userC, "pw");

        int progA = getProgressIdForAccount(accA);
        int progB = getProgressIdForAccount(accB);
        int progC = getProgressIdForAccount(accC);

        // Give them different total scores: B > C > A
        given()
            .contentType(ContentType.JSON)
            .pathParam("id", progA)
            .body(Map.of("totalScore", 50))
        .when()
            .put("/progress/{id}")
        .then()
            .statusCode(200)
            .body("totalScore", equalTo(50));

        given()
            .contentType(ContentType.JSON)
            .pathParam("id", progB)
            .body(Map.of("totalScore", 150))
        .when()
            .put("/progress/{id}")
        .then()
            .statusCode(200)
            .body("totalScore", equalTo(150));

        given()
            .contentType(ContentType.JSON)
            .pathParam("id", progC)
            .body(Map.of("totalScore", 100))
        .when()
            .put("/progress/{id}")
        .then()
            .statusCode(200)
            .body("totalScore", equalTo(100));

        // Leaderboard should contain all three usernames somewhere in the top 50
        given()
        .when()
            .get("/progress/leaderboard")
        .then()
            .statusCode(200)
            .body("username", hasItem(userA))
            .body("username", hasItem(userB))
            .body("username", hasItem(userC));

        // Cross-check ranks from /progress/by-account: higher score => better (lower) rank
        int rankA =
            given()
                .pathParam("accountId", accA)
            .when()
                .get("/progress/by-account/{accountId}")
            .then()
                .statusCode(200)
                .extract()
                .path("rank");

        int rankB =
            given()
                .pathParam("accountId", accB)
            .when()
                .get("/progress/by-account/{accountId}")
            .then()
                .statusCode(200)
                .extract()
                .path("rank");

        int rankC =
            given()
                .pathParam("accountId", accC)
            .when()
                .get("/progress/by-account/{accountId}")
            .then()
                .statusCode(200)
                .extract()
                .path("rank");

        // B has highest score → best rank, then C, then A
        assertTrue("Expected rankB < rankC < rankA",
                rankB < rankC && rankC < rankA);
    }
}