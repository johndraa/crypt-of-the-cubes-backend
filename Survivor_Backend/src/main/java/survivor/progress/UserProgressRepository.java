package survivor.progress;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * @author John Draa
 */

public interface UserProgressRepository extends JpaRepository<UserProgress, Integer>
{
    Optional<UserProgress> findByAccount_Id(Integer accountId);
    boolean existsByAccount_Id(Integer accountId);
    // Top N by totalScore

    @EntityGraph(attributePaths = "account")
    List<UserProgress> findTop50ByOrderByTotalScoreDesc();

    // For rank calculation (count how many have higher score)
    @org.springframework.data.jpa.repository.Query("""
  select count(p) from UserProgress p
  where p.totalScore > (select p2.totalScore from UserProgress p2 where p2.account.id = :accountId)
""")
    long countWithHigherScore(@org.springframework.data.repository.query.Param("accountId") Integer accountId);

    // Deletes the UserProgress row whose foreign key account.id == :accountId
    void deleteByAccount_Id(Integer accountId);
}
