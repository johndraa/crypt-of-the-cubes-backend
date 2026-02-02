package survivor.accounts;

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

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertFalse;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test"})
public class AccountControllerAdditionalTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AccountRepository accountRepository;

    @Before
    public void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        accountRepository.deleteAll();
    }

    /**
     * Test: PUT /accounts/{id} - update email
     * Coverage: AccountController.update() - email branch
     * Strategy: Black-box, functional
     * Equivalence: Valid email update
     */
    @Test
    public void testUpdateAccount_Email_UpdatesSuccessfully() {
        Account account = createTestAccount("old@example.com", "user1", "pass");
        account = accountRepository.save(account);

        given()
            .pathParam("id", account.getId())
            .contentType(ContentType.JSON)
            .body(Map.of("email", "new@example.com"))
        .when()
            .put("/accounts/{id}")
        .then()
            .statusCode(200)
            .body("email", equalTo("new@example.com"))
            .body("username", equalTo("user1"));
    }

    /**
     * Test: PUT /accounts/{id} - duplicate email
     * Coverage: AccountController.update() - ConflictException branch
     * Strategy: Black-box, error path
     * Equivalence: Email already in use
     */
    @Test
    public void testUpdateAccount_DuplicateEmail_Returns409() {
        Account account1 = createTestAccount("email1@example.com", "user1", "pass");
        account1 = accountRepository.save(account1);
        Account account2 = createTestAccount("email2@example.com", "user2", "pass");
        account2 = accountRepository.save(account2);

        given()
            .pathParam("id", account2.getId())
            .contentType(ContentType.JSON)
            .body(Map.of("email", "email1@example.com"))
        .when()
            .put("/accounts/{id}")
        .then()
            .statusCode(409)
            .body("message", equalTo("email already in use"));
    }

    /**
     * Test: PUT /accounts/{id} - blank email
     * Coverage: AccountController.update() - BadRequestException branch
     * Strategy: Black-box, boundary (blank string)
     * Equivalence: Blank email
     */
    @Test
    public void testUpdateAccount_BlankEmail_Returns400() {
        Account account = createTestAccount("test@example.com", "user1", "pass");
        account = accountRepository.save(account);

        given()
            .pathParam("id", account.getId())
            .contentType(ContentType.JSON)
            .body(Map.of("email", "   "))
        .when()
            .put("/accounts/{id}")
        .then()
            .statusCode(400)
            .body("message", equalTo("validation_error"))  // Changed from "Email cannot be blank"
            .body("errors.email", equalTo("Invalid email"));  // Added: Check nested error structure
    }

    /**
     * Test: PUT /accounts/{id} - update username
     * Coverage: AccountController.update() - username branch
     * Strategy: Black-box, functional
     * Equivalence: Valid username update
     */
    @Test
    public void testUpdateAccount_Username_UpdatesSuccessfully() {
        Account account = createTestAccount("test@example.com", "olduser", "pass");
        account = accountRepository.save(account);

        given()
            .pathParam("id", account.getId())
            .contentType(ContentType.JSON)
            .body(Map.of("username", "newuser"))
        .when()
            .put("/accounts/{id}")
        .then()
            .statusCode(200)
            .body("username", equalTo("newuser"));
    }

    /**
     * Test: PUT /accounts/{id} - blank username
     * Coverage: AccountController.update() - BadRequestException branch
     * Strategy: Black-box, boundary (blank string)
     * Equivalence: Blank username
     */
    @Test
    public void testUpdateAccount_BlankUsername_Returns400() {
        Account account = createTestAccount("test@example.com", "user1", "pass");
        account = accountRepository.save(account);

        given()
            .pathParam("id", account.getId())
            .contentType(ContentType.JSON)
            .body(Map.of("username", "   "))
        .when()
            .put("/accounts/{id}")
        .then()
            .statusCode(400)
            .body("message", equalTo("Username cannot be blank"));
    }

    /**
     * Test: PUT /accounts/{id} - update password
     * Coverage: AccountController.update() - password branch
     * Strategy: Black-box, functional
     * Equivalence: Valid password update
     */
    @Test
    public void testUpdateAccount_Password_UpdatesSuccessfully() {
        Account account = createTestAccount("test@example.com", "user1", "oldpass");
        account = accountRepository.save(account);

        given()
            .pathParam("id", account.getId())
            .contentType(ContentType.JSON)
            .body(Map.of("password", "newpass"))
        .when()
            .put("/accounts/{id}")
        .then()
            .statusCode(200);

        // Verify password was updated by logging in
        given()
            .queryParam("username", "user1")
            .queryParam("password", "newpass")
        .when()
            .get("/accounts/login")
        .then()
            .statusCode(200);
    }

    /**
     * Test: GET /accounts/{id}
     * Coverage: AccountController.read()
     * Strategy: Black-box, functional
     * Equivalence: Valid account ID
     */
    @Test
    public void testGetAccount_ValidId_ReturnsAccount() {
        Account account = createTestAccount("test@example.com", "user1", "pass");
        account = accountRepository.save(account);

        given()
            .pathParam("id", account.getId())
        .when()
            .get("/accounts/{id}")
        .then()
            .statusCode(200)
            .body("accountId", equalTo(account.getId()))
            .body("email", equalTo("test@example.com"))
            .body("username", equalTo("user1"));
    }

    /**
     * Test: GET /accounts/{id}
     * Coverage: AccountController.read() - NotFoundException branch
     * Strategy: Black-box, error path
     * Equivalence: Invalid account ID
     */
    @Test
    public void testGetAccount_InvalidId_Returns404() {
        given()
            .pathParam("id", 99999)
        .when()
            .get("/accounts/{id}")
        .then()
            .statusCode(404)
            .body("message", equalTo("Account not found"));
    }

    /**
     * Test: GET /accounts
     * Coverage: AccountController.listAll()
     * Strategy: Black-box, functional
     * Equivalence: Multiple accounts
     */
    @Test
    public void testListAllAccounts_ReturnsAllAccounts() {
        Account account1 = createTestAccount("test1@example.com", "user1", "pass");
        account1 = accountRepository.save(account1);
        Account account2 = createTestAccount("test2@example.com", "user2", "pass");
        account2 = accountRepository.save(account2);

        given()
        .when()
            .get("/accounts")
        .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(2))
            .body("accountId", hasItems(account1.getId(), account2.getId()));
    }

    /**
     * Test: DELETE /accounts/{id}
     * Coverage: AccountController.delete()
     * Strategy: Black-box, functional
     * Equivalence: Valid account ID
     */
    @Test
    public void testDeleteAccount_ValidId_DeletesAccount() {
        Account account = createTestAccount("test@example.com", "user1", "pass");
        account = accountRepository.save(account);

        given()
            .pathParam("id", account.getId())
        .when()
            .delete("/accounts/{id}")
        .then()
            .statusCode(204);

        assertFalse(accountRepository.existsById(account.getId()));
    }

    /**
     * Test: DELETE /accounts/{id}
     * Coverage: AccountController.delete() - NotFoundException branch
     * Strategy: Black-box, error path
     * Equivalence: Invalid account ID
     */
    @Test
    public void testDeleteAccount_InvalidId_Returns404() {
        given()
            .pathParam("id", 99999)
        .when()
            .delete("/accounts/{id}")
        .then()
            .statusCode(404)
            .body("message", equalTo("Account not found"));
    }

    /**
     * Test: GET /accounts/login - with email
     * Coverage: AccountController.login() - email branch
     * Strategy: Black-box, functional
     * Equivalence: Valid email login
     */
    @Test
    public void testLogin_WithEmail_ReturnsAccount() {
        Account account = createTestAccount("test@example.com", "user1", "pass");
        account = accountRepository.save(account);

        given()
            .queryParam("email", "test@example.com")
            .queryParam("password", "pass")
        .when()
            .get("/accounts/login")
        .then()
            .statusCode(200)
            .body("id", equalTo(account.getId()))
            .body("email", equalTo("test@example.com"));
    }

    /**
     * Test: GET /accounts/login - invalid email format
     * Coverage: AccountController.login() - BadRequestException branch
     * Strategy: Black-box, boundary (invalid format)
     * Equivalence: Invalid email format
     */
    @Test
    public void testLogin_InvalidEmailFormat_Returns400() {
        given()
            .queryParam("email", "notanemail")
            .queryParam("password", "pass")
        .when()
            .get("/accounts/login")
        .then()
            .statusCode(400)
            .body("message", equalTo("Invalid email format"));
    }

    /**
     * Test: GET /accounts/login - missing password
     * Coverage: AccountController.login() - BadRequestException branch
     * Strategy: Black-box, boundary (null/blank password)
     * Equivalence: Missing password
     */
    @Test
    public void testLogin_MissingPassword_Returns400() {
        given()
            .queryParam("username", "user1")
        .when()
            .get("/accounts/login")
        .then()
            .statusCode(400)
            .body("message", equalTo("password is required"));
    }

    /**
     * Test: GET /accounts/login - neither email nor username
     * Coverage: AccountController.login() - BadRequestException branch
     * Strategy: Black-box, boundary (both missing)
     * Equivalence: Missing both email and username
     */
    @Test
    public void testLogin_NeitherEmailNorUsername_Returns400() {
        given()
            .queryParam("password", "pass")
        .when()
            .get("/accounts/login")
        .then()
            .statusCode(400)
            .body("message", equalTo("provide email or username"));
    }

    private Account createTestAccount(String email, String username, String password) {
        Account account = new Account();
        account.setEmail(email);
        account.setUsername(username);
        account.setPassword(password);
        return account;
    }
}