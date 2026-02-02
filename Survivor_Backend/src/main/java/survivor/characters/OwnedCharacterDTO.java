package survivor.characters;

/**
 * @author John Draa
 * @param characterId
 * @param code code for tracking characters in dev
 * @param name
 */

public record OwnedCharacterDTO(
        Integer characterId,
        String  code,
        String  name
)
{
    public static OwnedCharacterDTO from(GameCharacter c)
    {
        return new OwnedCharacterDTO(c.getId(), c.getCode(), c.getName());
    }
}