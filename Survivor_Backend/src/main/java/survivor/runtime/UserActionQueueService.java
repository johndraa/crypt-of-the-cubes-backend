package survivor.runtime;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simplest possible per-user queue:
 * - One single-thread executor per userId.
 * - enqueue(userId, task) runs tasks FIFO for that user.
 * - No cleanup (fine for <= 4 players).
 */
@Component
public class UserActionQueueService {

    private final Map<Integer, ExecutorService> executors = new ConcurrentHashMap<>();

    public void enqueue(int userId, Runnable task) {
        ExecutorService exec = executors.computeIfAbsent(
                userId,
                id -> Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r);
                    t.setName("user-" + id + "-actions");
                    t.setDaemon(true);
                    return t;
                })
        );
        exec.submit(task);
    }
}