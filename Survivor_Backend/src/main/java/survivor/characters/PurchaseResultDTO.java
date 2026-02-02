package survivor.characters;

/**
 * @author John Draa
 * @param message response for purchase result; "purchased" or "already owned"
 * @param characterId
 * @param newCoinBalance
 */

public record PurchaseResultDTO(
        String  message,
        Integer characterId,
        Integer newCoinBalance
) {
    public static PurchaseResultDTO purchased(Integer id, Integer coins) {
        return new PurchaseResultDTO("purchased", id, coins);
    }
    public static PurchaseResultDTO alreadyOwned(Integer id) {
        return new PurchaseResultDTO("already owned", id, null);
    }
}