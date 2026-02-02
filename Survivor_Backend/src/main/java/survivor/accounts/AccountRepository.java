package survivor.accounts;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * @author John Draa
 */

public interface AccountRepository extends JpaRepository<Account, Integer>
{
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);

    Optional<Account> findByEmailIgnoreCase(String email);
    Optional<Account> findByUsernameIgnoreCase(String username);

}