package survivor.characters;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * @author John Draa
 */

public interface UserCharacterUnlockRepository extends JpaRepository<UserCharacterUnlock, Integer>
{
    boolean existsByAccount_IdAndCharacter_Id(Integer accountId, Integer characterId);

    @EntityGraph(attributePaths = "character")
    List<UserCharacterUnlock> findAllByAccount_Id(Integer accountId);

    void deleteAllByAccount_Id(Integer accountId);
    List<UserCharacterUnlock> findAllByAccount_IdOrderByCharacter_IdAsc(Integer accountId);
}
