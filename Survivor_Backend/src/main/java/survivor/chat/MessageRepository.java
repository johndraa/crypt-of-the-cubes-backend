package survivor.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * @author John Draa
 */

public interface MessageRepository extends JpaRepository<Message, Long>
{

    // last N messages in a match, newest first
    @Query("""
           select m from Message m
           where m.matchId = :matchId
           order by m.sentAt desc
           """)
    List<Message> findRecentByMatchId(Long matchId, org.springframework.data.domain.Pageable pageable);
}
