package survivor.characters;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import survivor.shared.AttackStyle;

/**
 * @author John Draa
 */

@Entity
@Table(name = "characters", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GameCharacter
{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Min(0) @Column(nullable = false)
    private int cost;

    @Column(name="health") @Min(100) @Max(500) private int health;
    @Column(name="move_speed")   @Min(10) @Max(100) private int moveSpeed;
    @Column(name="attack_speed") @Min(10) @Max(100) private int attackSpeed;
    @Column(name="damage_mult")  @Min(10) @Max(100) private int damageMult;
    @Column(name="crit_chance")  @Min(10) @Max(100) private int critChance;
    @Column(name = "range_units", nullable = false)@Min(0) private int rangeUnits;
    @Column(name = "attack_style", nullable = false) @Enumerated(EnumType.STRING) private AttackStyle attackStyle;

    @PrePersist @PreUpdate
    private void normalize()
    {
        if (code != null) code = code.trim().toUpperCase();
        if (name != null) name = name.trim();
    }
}
