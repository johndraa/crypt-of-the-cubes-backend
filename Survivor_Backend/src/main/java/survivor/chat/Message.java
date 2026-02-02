package survivor.chat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * @author John Draa
 */

@Entity
@Table(name = "messages",
        indexes = {
                @Index(name="ix_messages_match", columnList = "match_id, sent_at"),
                @Index(name="ix_messages_user",  columnList = "user_name, sent_at")
        })
@Getter @Setter @NoArgsConstructor
public class Message {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "user_name", nullable = false, length = 50)
    private String userName;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    public Message(Long matchId, String userName, String content) {
        this.matchId = matchId;
        this.userName = userName;
        this.content = content;
        this.sentAt = Instant.now();
    }
}
