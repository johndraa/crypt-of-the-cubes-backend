package survivor.match;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * @author John Draa
 */

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long>
{
    boolean existsByMatchIdAndAccountId(Long matchId, Integer accountId);
    List<MatchParticipant> findByMatchId(Long matchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM MatchParticipant p WHERE p.match.id = :matchId")
    List<MatchParticipant> findByMatchIdWithLock(@Param("matchId") long matchId);
}
