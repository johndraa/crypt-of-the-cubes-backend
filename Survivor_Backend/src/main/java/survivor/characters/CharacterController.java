package survivor.characters;

import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import survivor.accounts.AccountRepository;
import survivor.exceptions.NotFoundException;

import java.util.*;
/**
 * @author John Draa
 */

@RestController
@RequiredArgsConstructor
public class CharacterController
{

    private final AccountRepository accountRepository;
    private final GameCharacterRepository characters;
    private final UserCharacterUnlockRepository unlockRepo;
    private final CharacterPurchaseService purchaseSvc;

    private static final Comparator<GameCharacter> SHOP_ORDER =
            Comparator.comparingInt(GameCharacter::getCost)
                    .thenComparingInt(GameCharacter::getId);

    //-----Full catalog sorted by id-----
    @GetMapping("/characters")
    public ResponseEntity<?> listCharacters()
    {
        var dto = characters.findAllByOrderByIdAsc().stream()
                .map(CharacterResponseDTO::from)
                .toList();
        return ResponseEntity.ok(dto);
    }

    //-----Shop (unowned) sorted by cost, then id-----
    @GetMapping("/accounts/{accountId}/shop/characters")
    public ResponseEntity<?> listShopCharactersForAccount(@PathVariable Integer accountId)
    {
        if (!accountRepository.existsById(accountId))
        {
            throw new NotFoundException("Account not found");
        }

        var ownedIds = unlockRepo.findAllByAccount_Id(accountId).stream()
                .map(u -> u.getCharacter().getId())
                .collect(java.util.stream.Collectors.toSet());

        var purchasable = characters.findAll().stream()
                .filter(c -> c.getCost() > 0)
                .filter(c -> !ownedIds.contains(c.getId()))
                .sorted(SHOP_ORDER)
                .map(CharacterResponseDTO::from)
                .toList();

        return ResponseEntity.ok(purchasable);
    }

    //-----Get one-----
    @GetMapping("/characters/{id}")
    public ResponseEntity<?> getCharacter(@PathVariable Integer id)
    {
        var c = characters.findById(id)
                .orElseThrow(() -> new NotFoundException("Character not found"));
        return ResponseEntity.ok(CharacterResponseDTO.from(c));
    }

    //-----Owned list (sorted same as shop)-----
    @GetMapping("/accounts/{accountId}/characters")
    public ResponseEntity<?> owned(@PathVariable Integer accountId)
    {
        if (!accountRepository.existsById(accountId))
        {
            throw new NotFoundException("Account not found");
        }

        var owned = unlockRepo.findAllByAccount_Id(accountId).stream()
                .map(UserCharacterUnlock::getCharacter)
                .sorted(SHOP_ORDER)
                .map(OwnedCharacterDTO::from)
                .toList();

        return ResponseEntity.ok(owned);
    }

    //-----Purchase-----
    @PostMapping("/accounts/{accountId}/characters/{characterId}/purchase")
    public ResponseEntity<?> purchase(@PathVariable Integer accountId,
                                      @PathVariable Integer characterId)
    {
        if (!accountRepository.existsById(accountId))
        {
            throw new NotFoundException("Account not found");
        }

        var res = purchaseSvc.purchase(accountId, characterId);
        return ResponseEntity.ok(
                PurchaseResultDTO.purchased(res.characterId(), res.newCoinBalance())
        );
    }
}