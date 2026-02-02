package survivor.progress;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import survivor.accounts.AccountRepository;
import survivor.exceptions.BadRequestException;
import survivor.exceptions.ConflictException;
import survivor.exceptions.NotFoundException;

import java.util.stream.IntStream;

/**
 * @author John Draa
 */

@RestController
@RequestMapping("/progress")
@RequiredArgsConstructor
public class UserProgressController
{

    private final UserProgressRepository progressRepo;
    private final AccountRepository accountRepo;

    //-----CREATE (testing only)-----
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody ProgressCreateDTO body)
    {
        Integer accountId = body.accountId();

        var account = accountRepo.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        if (progressRepo.existsByAccount_Id(accountId))
        {
            throw new ConflictException("Progress already exists for this account");
        }

        var saved = progressRepo.save(UserProgress.builder()
                .account(account).coins(0).totalScore(0).build());

        long higher = progressRepo.countWithHigherScore(accountId);
        int rank = (int) higher + 1;

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProgressResponseDTO.from(saved, rank));
    }

    //-----UPDATE (add to totals)-----
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @Valid @RequestBody ProgressUpdateDTO req)
    {
        var p = progressRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Account progress not found"));

        if (req.getCoins() != null)
        {
            int c = req.getCoins();
            if (c < 0) throw new BadRequestException("coins must be >= 0");
            p.setCoins(p.getCoins() + c);
        }
        if (req.getTotalScore() != null)
        {
            int s = req.getTotalScore();
            if (s < 0) throw new BadRequestException("totalScore must be >= 0");
            p.setTotalScore(p.getTotalScore() + s);
        }

        var saved = progressRepo.save(p);

        var accountId = saved.getAccount().getId();
        long higher = progressRepo.countWithHigherScore(accountId);
        int rank = (int) higher + 1;

        return ResponseEntity.ok(ProgressResponseDTO.from(saved, rank));
    }

    //-----DELETE (testing)-----
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id)
    {
        if (!progressRepo.existsById(id))
        {
            throw new NotFoundException("Account progress not found");
        }
        progressRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    //-----READ by account id (includes rank)-----
    @GetMapping("/by-account/{accountId}")
    public ResponseEntity<?> getByAccount(@PathVariable Integer accountId)
    {
        var p = progressRepo.findByAccount_Id(accountId)
                .orElseThrow(() -> new NotFoundException("Progress not found"));

        long higher = progressRepo.countWithHigherScore(accountId);
        int rank = (int) higher + 1;

        return ResponseEntity.ok(ProgressResponseDTO.from(p, rank));
    }

    //-----TOP 50 leaderboard-----
    @GetMapping("/leaderboard")
    public ResponseEntity<?> top50()
    {
        var top = progressRepo.findTop50ByOrderByTotalScoreDesc(); // List<UserProgress>

        var rows = IntStream.range(0, top.size())
                .mapToObj(i ->
                {
                    var p = top.get(i);
                    return new LeaderboardRowDTO(
                            p.getAccount().getId(),
                            p.getAccount().getUsername(),
                            p.getTotalScore(),
                            i + 1
                    );
                })
                .toList();

        return ResponseEntity.ok(rows);
    }
}
