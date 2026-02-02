package survivor.accounts;

import lombok.*;
import jakarta.validation.Valid;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import survivor.characters.GameCharacterRepository;
import survivor.characters.UserCharacterUnlock;
import survivor.characters.UserCharacterUnlockRepository;
import survivor.exceptions.BadRequestException;
import survivor.exceptions.ConflictException;
import survivor.exceptions.NotFoundException;
import survivor.progress.UserProgress;
import survivor.progress.UserProgressRepository;

import java.time.LocalDateTime;
import java.util.*;

/**
 * @author John Draa
 */

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;
    private final UserProgressRepository progressRepository;
    private final GameCharacterRepository characterRepository;
    private final UserCharacterUnlockRepository unlockRepository;

    // -------- SIGNUP --------
    @PostMapping("/signup")
    @Transactional
    public ResponseEntity<?> signup(@Valid @RequestBody AccountCreateDTO dto)
    {
        String email = dto.email() == null ? null : dto.email().trim().toLowerCase();
        String username = dto.username() == null ? null : dto.username().trim();

        if (email != null && accountRepository.existsByEmailIgnoreCase(email))
        {
            throw new ConflictException("email already in use");
        }
        if (username != null && accountRepository.existsByUsernameIgnoreCase(username))
        {
            throw new ConflictException("username already in use");
        }

        var account = new Account();
        account.setEmail(email);
        account.setUsername(username);
        account.setPassword(dto.password()); // TODO: hash in #16

        var saved = accountRepository.save(account);

        // Auto-create progress
        if (!progressRepository.existsByAccount_Id(saved.getId()))
        {
            var progress = new UserProgress();
            progress.setAccount(saved);
            progress.setCoins(0);
            progress.setTotalScore(0);
            progressRepository.save(progress);
        }

        // Grant base character
        characterRepository.findByCodeIgnoreCase("WANDERER").ifPresent(wanderer ->
        {
            boolean alreadyGranted =
                    unlockRepository.existsByAccount_IdAndCharacter_Id(saved.getId(), wanderer.getId());
            if (!alreadyGranted)
            {
                unlockRepository.save(UserCharacterUnlock.builder()
                        .account(saved)
                        .character(wanderer)
                        .unlockedAt(LocalDateTime.now())
                        .build());
            }
        });

        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponseDTO.from(saved));
    }

    // -------- LOGIN --------
    @GetMapping("/login")
    public ResponseEntity<?> login(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password)
    {

        if (password == null || password.isBlank())
        {
            throw new BadRequestException("password is required");
        }

        boolean hasEmail = email != null && !email.isBlank();
        boolean hasUsername = username != null && !username.isBlank();
        if (!hasEmail && !hasUsername)
        {
            throw new BadRequestException("provide email or username");
        }

        if (hasEmail && !EmailValidator.getInstance().isValid(email))
        {
            throw new BadRequestException("Invalid email format");
        }

        Account found = hasEmail
                ? accountRepository.findByEmailIgnoreCase(email).orElse(null)
                : accountRepository.findByUsernameIgnoreCase(username).orElse(null);

        if (found == null || !found.getPassword().equals(password))
        {
            // keep 401 via ResponseStatusException (no custom Unauthorized class)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }

        return ResponseEntity.ok(Map.of(
                "id", found.getId(),
                "email", found.getEmail(),
                "username", found.getUsername()
        ));
    }

    // -------- UPDATE --------
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable Integer id, @Valid @RequestBody AccountUpdateDTO dto)
    {
        var existing = accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        if (dto.email() != null)
        {
            var email = dto.email().trim().toLowerCase();
            if (!email.equalsIgnoreCase(existing.getEmail())
                    && accountRepository.existsByEmailIgnoreCase(email))
            {
                throw new ConflictException("email already in use");
            }
            if (email.isBlank()) throw new BadRequestException("Email cannot be blank");
            existing.setEmail(email);
        }

        if (dto.username() != null)
        {
            var username = dto.username().trim();
            if (!username.equals(existing.getUsername())
                    && accountRepository.existsByUsernameIgnoreCase(username))
            {
                throw new ConflictException("username already in use");
            }
            if (username.isBlank()) throw new BadRequestException("Username cannot be blank");
            existing.setUsername(username);
        }

        if (dto.password() != null)
        {
            var pwd = dto.password();
            if (pwd.isBlank()) throw new BadRequestException("Password cannot be blank");
            existing.setPassword(pwd); // TODO: hash later
        }

        var saved = accountRepository.save(existing);
        return ResponseEntity.ok(AccountResponseDTO.from(saved));
    }

    // -------- GET ONE --------
    @GetMapping("/{id}")
    public ResponseEntity<?> read(@PathVariable Integer id)
    {
        var acc = accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        return ResponseEntity.ok(AccountResponseDTO.from(acc));
    }

    // -------- LIST ALL --------
    @GetMapping
    public ResponseEntity<?> listAll()
    {
        var list = accountRepository.findAll().stream()
                .map(AccountResponseDTO::from)
                .toList();
        return ResponseEntity.ok(list);
    }

    // -------- DELETE --------
    @Transactional
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id)
    {
        if (!accountRepository.existsById(id))
        {
            throw new NotFoundException("Account not found");
        }
        unlockRepository.deleteAllByAccount_Id(id);
        progressRepository.deleteByAccount_Id(id);
        accountRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
