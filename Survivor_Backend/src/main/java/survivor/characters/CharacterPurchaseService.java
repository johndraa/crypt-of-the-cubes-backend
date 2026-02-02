package survivor.characters;

import lombok.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import survivor.exceptions.BadRequestException;
import survivor.exceptions.ConflictException;
import survivor.exceptions.NotFoundException;
import survivor.progress.UserProgressRepository;
import java.time.LocalDateTime;

/**
 * @author John Draa
 * Server-authoritative purchase flow:
 * - verify character exists
 * - check if already owned
 * - verify coin balance (from user_progress)
 * - deduct coins and insert ownership atomically
 */
@Service
@RequiredArgsConstructor
public class CharacterPurchaseService
{
    private final GameCharacterRepository charRepo;
    private final UserCharacterUnlockRepository unlockRepo;
    private final UserProgressRepository progressRepo;

    @Transactional
    public PurchaseResult purchase(Integer accountId, Integer characterId)
    {
        var character = charRepo.findById(characterId)
                .orElseThrow(() -> new NotFoundException("Character not found"));

        if (character.getCost() == 0)
        {
            throw new BadRequestException("Base character is already owned by default");
        }

        if (unlockRepo.existsByAccount_IdAndCharacter_Id(accountId, characterId))
        {
            throw new ConflictException("Already owned");
        }

        var progress = progressRepo.findByAccount_Id(accountId)
                .orElseThrow(() -> new NotFoundException("Progress not found"));

        if (progress.getCoins() < character.getCost())
        {
            throw new BadRequestException("Not enough coins");
        }

        progress.setCoins(progress.getCoins() - character.getCost());
        progressRepo.save(progress);

        unlockRepo.save(UserCharacterUnlock.builder()
                .account(progress.getAccount())
                .character(character)
                .unlockedAt(LocalDateTime.now())
                .build());

        return PurchaseResult.success(character.getId(), progress.getCoins());
    }

    // ----- Result -----
    public static record PurchaseResult(Integer characterId, Integer newCoinBalance)
    {
        public static PurchaseResult success(Integer id, Integer coins)
        {
            return new PurchaseResult(id, coins);
        }
    }
}
