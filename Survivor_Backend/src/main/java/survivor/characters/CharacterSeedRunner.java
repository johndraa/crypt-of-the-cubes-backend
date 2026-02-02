package survivor.characters;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import survivor.shared.AttackStyle;

/**
 * @author John Draa
 * Seeds the character catalog on startup.
 * Organized into small helper methods to keep things readable as the catalog grows.
 * Safe to run repeatedly (checks existence by unique code).
 */

@Component
@Order(0)
@RequiredArgsConstructor
public class CharacterSeedRunner implements CommandLineRunner
{

    private final GameCharacterRepository repo;

    @Override
    @Transactional
    public void run(String... args) throws Exception
    {
        upsert(GameCharacter.builder().code("WANDERER").name("Wanderer").cost(0)
                .health(133).moveSpeed(20).attackSpeed(20).damageMult(20).critChance(20).attackStyle(AttackStyle.AOE).rangeUnits(2).build());

        upsert(GameCharacter.builder().code("WARRIOR").name("Warrior").cost(100)
                .health(300).moveSpeed(35).attackSpeed(25).damageMult(30).critChance(20).attackStyle(AttackStyle.AOE).rangeUnits(2).build());

        upsert(GameCharacter.builder().code("ROGUE").name("Rogue").cost(100)
                .health(133).moveSpeed(50).attackSpeed(50).damageMult(20).critChance(50).attackStyle(AttackStyle.AOE).rangeUnits(2).build());

        upsert(GameCharacter.builder().code("MAGE").name("Mage").cost(100)
                        .health(100).moveSpeed(35).attackSpeed(30).damageMult(60).critChance(45).attackStyle(AttackStyle.AOE).rangeUnits(2).build());

        upsert(GameCharacter.builder().code("PALADIN").name("Paladin").cost(100)
                .health(400).moveSpeed(25).attackSpeed(20).damageMult(25).critChance(10).attackStyle(AttackStyle.AOE).rangeUnits(2).build());

        upsert(GameCharacter.builder().code("HUNTER").name("Hunter").cost(100)
                .health(200).moveSpeed(40).attackSpeed(35).damageMult(30).critChance(40).attackStyle(AttackStyle.AOE).rangeUnits(2).build());

        upsert(GameCharacter.builder().code("WARLOCK").name("Warlock").cost(100)
                .health(233).moveSpeed(25).attackSpeed(30).damageMult(45).critChance(20).attackStyle(AttackStyle.AOE).rangeUnits(2).build());
    }

    private void upsert(GameCharacter spec)
    {
        var existing = repo.findByCodeIgnoreCase(spec.getCode()).orElse(null);
        if (existing == null) { repo.save(spec); return; }

        // sync fields you want controlled by seed
        existing.setName(spec.getName());
        existing.setHealth(spec.getHealth());
        existing.setMoveSpeed(spec.getMoveSpeed());
        existing.setAttackSpeed(spec.getAttackSpeed());
        existing.setDamageMult(spec.getDamageMult());
        existing.setCritChance(spec.getCritChance());
        existing.setRangeUnits(spec.getRangeUnits());
        existing.setAttackStyle(spec.getAttackStyle());
        existing.setCost(spec.getCost());
        repo.save(existing);
    }
}