package survivor.match;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

/**
 * @author John Draa
 */

@Entity
@Table(name = "matches")
@Getter
@Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Match
{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="join_code", nullable = false, unique = true, length = 8)
    private String joinCode;  // e.g. "A9KXQZ"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @jakarta.validation.constraints.NotNull
    @Builder.Default
    private MatchStatus status = MatchStatus.LOBBY;      // LOBBY, ACTIVE, ENDED, ABORTED

    private Integer winnerAccountId; // nullable until end

    @CreationTimestamp private Instant createdAt;
    private Instant startedAt;
    private Instant endedAt;
}
