package survivor.progress;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import survivor.accounts.Account;
import java.time.LocalDateTime;

/**
 * @author John Draa
 */

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(
        name = "user_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = "account_id")
)
@EqualsAndHashCode(of = "id")
@ToString(exclude = "account")
public class UserProgress
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // 1-1 relationship with account
    @OneToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    @JsonIgnore
    private Account account;

    @Column(nullable = false)
    @Builder.Default
    private int coins = 0;

    @Column(name = "total_score", nullable = false)
    @Builder.Default
    private int totalScore = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("accountId")
    public Integer getAccountId()
    {
        return account != null ? account.getId() : null;
    }

    @PrePersist
    void onCreate()
    {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate()
    {
        updatedAt = LocalDateTime.now();
    }
}
