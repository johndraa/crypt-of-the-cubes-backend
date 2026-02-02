package survivor.accounts;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import survivor.characters.UserCharacterUnlock;
import survivor.progress.UserProgress;

import java.util.ArrayList;
import java.util.List;

/**
 * @author John Draa
 */

@Entity
@Table(name = "accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_accounts_email",    columnNames = "email"),
                @UniqueConstraint(name = "uq_accounts_username", columnNames = "username")
        })
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"progress","unlocks"})
public class Account
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "username cannot be blank")
    @Column(nullable = false)
    private String username;

    @NotBlank(message = "email cannot be blank")
    @Email(message = "Invalid email format")
    @Column(nullable = false)
    private String email;

    @NotBlank(message = "password cannot be blank")
    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) // accept in requests, never return in responses
    private String password;

    @PrePersist
    @PreUpdate
    private void normalize()
    {
        if (email != null)    email = email.trim().toLowerCase();
        if (username != null) username = username.trim();
    }

    @OneToOne(mappedBy = "account", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private UserProgress progress;

    @OneToMany(mappedBy = "account", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<UserCharacterUnlock> unlocks = new ArrayList<>();
}