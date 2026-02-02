package survivor.match;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * @author John Draa
 */

public interface MatchRepository extends JpaRepository<Match, Long>
{
    Optional<Match> findByJoinCode(String joinCode);
}
