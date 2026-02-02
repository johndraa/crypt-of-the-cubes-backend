package survivor.characters;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * @author John Draa
 */

public interface GameCharacterRepository extends JpaRepository<GameCharacter, Integer>
{
    Optional<GameCharacter> findByCodeIgnoreCase(String code);

    List<GameCharacter> findAllByOrderByIdAsc();
}