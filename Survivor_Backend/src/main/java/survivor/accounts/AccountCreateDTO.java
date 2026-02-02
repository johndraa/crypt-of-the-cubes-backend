package survivor.accounts;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * @author John Draa
 */

public record AccountCreateDTO(
        @NotBlank(message = "email cannot be blank")
        @Email(message = "Invalid email")
        String email,

        @NotBlank(message = "username cannot be blank")
        String username,

        @NotBlank(message = "password cannot be blank")
        String password
) {}