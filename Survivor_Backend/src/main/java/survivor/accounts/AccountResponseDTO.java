package survivor.accounts;

/**
 * @author John Draa
 */

public record AccountResponseDTO(
        Integer accountId,
        String email,
        String username
) {
    public static AccountResponseDTO from(Account a)
    {
        return new AccountResponseDTO(a.getId(), a.getEmail(), a.getUsername());
    }
}