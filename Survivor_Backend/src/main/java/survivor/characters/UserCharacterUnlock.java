package survivor.characters;

import jakarta.persistence.*;
import lombok.*;
import survivor.accounts.Account;
import java.time.LocalDateTime;

/**
 * @author John Draa
 */

@Entity
@Table(name = "user_character_unlocks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"account_id","character_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"account"})
public class UserCharacterUnlock
{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Account account;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", nullable = false)
    private GameCharacter character;

    @Column(name="unlocked_at", nullable = false)
    @Builder.Default
    private LocalDateTime unlockedAt = LocalDateTime.now();

    @PrePersist
    void onCreate()
    {
        if (unlockedAt == null)
        {
            unlockedAt = LocalDateTime.now();
        }
    }
}
