package survivor.match;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * @author John Draa
 */

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(
        uniqueConstraints = @UniqueConstraint(columnNames={"match_id","account_id"}),
        indexes = {
                @Index(name="idx_mp_match",   columnList="match_id"),
                @Index(name="idx_mp_account", columnList="account_id")
        }
)
public class MatchParticipant
{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name="match_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mp_match"))
    private Match match;

    private Integer accountId;

    // character pick (nullable until lock-in)
    private Integer characterId;
    private String  selectedCharacterCode;

    // lobby state
    @Builder.Default
    private Boolean ready = false;
    private Instant joinedAt;
    private Instant leftAt;

    // results (filled on ENDED)
    private Integer score;
    private Integer coinsEarned;
    private Long    timeAliveMs;
}
