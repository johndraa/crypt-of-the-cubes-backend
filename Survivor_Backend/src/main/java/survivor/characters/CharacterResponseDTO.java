package survivor.characters;

import survivor.shared.AttackStyle;

/**
 * @author John Draa
 */

public record CharacterResponseDTO(
        Integer characterId,
        String  code,
        String  name,
        int     cost,
        int     health,
        int     moveSpeed,
        int     attackSpeed,
        int     damageMult,
        int     critChance,
        int     rangeUnits,
        AttackStyle attackStyle
)
{
    public static CharacterResponseDTO from(GameCharacter c)
    {
        return new CharacterResponseDTO(
                c.getId(),
                c.getCode(),
                c.getName(),
                c.getCost(),
                c.getHealth(),
                c.getMoveSpeed(),
                c.getAttackSpeed(),
                c.getDamageMult(),
                c.getCritChance(),
                c.getRangeUnits(),
                c.getAttackStyle()
        );
    }
}
