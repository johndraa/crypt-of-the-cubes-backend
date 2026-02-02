package survivor.accounts;

/**
 * @author John Draa
 */

public record AccountUpdateDTO(
        @jakarta.validation.constraints.Email(message = "Invalid email")
        String email,
        String username,
        String password
) {}
