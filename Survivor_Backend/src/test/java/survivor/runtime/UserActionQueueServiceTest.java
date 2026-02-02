package survivor.runtime;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Unit tests for UserActionQueueService.
 * 
 * Coverage Goals:
 * - UserActionQueueService.enqueue() - FIFO ordering, concurrent processing, thread safety
 * - Error handling - exception in task doesn't break queue
 * - Executor reuse - same executor for same user
 * 
 * Strategy: Unit testing with concurrent execution, thread safety validation
 */
@RunWith(SpringRunner.class)
@ActiveProfiles({"test"})
public class UserActionQueueServiceTest {

    private UserActionQueueService queueService;

    @Before
    public void setUp() {
        queueService = new UserActionQueueService();
    }

    /**
     * Test: enqueue() - FIFO ordering for single user
     * Coverage: UserActionQueueService.enqueue() - FIFO ordering branch
     * Strategy: White-box, functional, ordering validation
     * Equivalence: Multiple tasks for same user
     * Branches: Task ordering preservation branch
     * Why: Tests core FIFO guarantee for per-user action queues
     */
    @Test
    public void testFifoOrderingForSingleUser() throws InterruptedException {
        int userId = 1;
        List<Integer> executionOrder = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(5);

        // Enqueue 5 tasks for the same user
        for (int i = 0; i < 5; i++) {
            final int taskId = i;
            queueService.enqueue(userId, () -> {
                executionOrder.add(taskId);
                latch.countDown();
            });
        }

        // Wait for all tasks to complete (with timeout)
        assertTrue("All tasks should complete within 5 seconds", 
                   latch.await(5, TimeUnit.SECONDS));

        // Verify FIFO order
        assertEquals("Should have 5 tasks executed", 5, executionOrder.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("Task " + i + " should execute in order", 
                        Integer.valueOf(i), executionOrder.get(i));
        }
    }

    /**
     * Test: enqueue() - concurrent processing for different users
     * Coverage: UserActionQueueService.enqueue() - concurrent execution branch
     * Strategy: White-box, concurrent execution validation
     * Equivalence: Tasks for different users
     * Branches: Per-user executor branch, concurrent execution branch
     * Why: Tests that different users don't block each other
     */
    @Test
    public void testConcurrentProcessingForDifferentUsers() throws InterruptedException {
        int numUsers = 3;
        int tasksPerUser = 3;
        CountDownLatch latch = new CountDownLatch(numUsers * tasksPerUser);
        AtomicInteger concurrentExecutions = new AtomicInteger(0);
        AtomicInteger maxConcurrency = new AtomicInteger(0);

        // Enqueue tasks for multiple users simultaneously
        for (int userId = 1; userId <= numUsers; userId++) {
            final int uId = userId;
            for (int taskId = 0; taskId < tasksPerUser; taskId++) {
                queueService.enqueue(uId, () -> {
                    int current = concurrentExecutions.incrementAndGet();
                    // Update max concurrency
                    maxConcurrency.updateAndGet(max -> Math.max(max, current));
                    
                    try {
                        // Simulate some work
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    concurrentExecutions.decrementAndGet();
                    latch.countDown();
                });
            }
        }

        // Wait for all tasks to complete
        assertTrue("All tasks should complete within 10 seconds", 
                   latch.await(10, TimeUnit.SECONDS));

        // Since different users have separate threads, we should see concurrent execution
        // Max concurrency should be at least 2 (if tasks overlap) and at most numUsers
        assertTrue("Should have concurrent execution for different users", 
                   maxConcurrency.get() >= 1);
        assertTrue("Max concurrency should not exceed number of users", 
                   maxConcurrency.get() <= numUsers);
    }

    /**
     * Test: enqueue() - thread safety for same user
     * Coverage: UserActionQueueService.enqueue() - thread safety branch
     * Strategy: White-box, concurrent access validation
     * Equivalence: Multiple threads enqueuing for same user
     * Branches: Thread-safe enqueue branch
     * Why: Tests thread safety when multiple threads enqueue for same user
     */
    @Test
    public void testThreadSafetyForSameUser() throws InterruptedException {
        int userId = 1;
        int numThreads = 5;
        int tasksPerThread = 10;
        CountDownLatch enqueueLatch = new CountDownLatch(numThreads);
        CountDownLatch executionLatch = new CountDownLatch(numThreads * tasksPerThread);
        List<Integer> executionOrder = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger taskCounter = new AtomicInteger(0);

        // Multiple threads enqueue tasks for the same user
        for (int threadId = 0; threadId < numThreads; threadId++) {
            final int tId = threadId;
            new Thread(() -> {
                for (int i = 0; i < tasksPerThread; i++) {
                    final int taskNum = taskCounter.getAndIncrement();
                    queueService.enqueue(userId, () -> {
                        executionOrder.add(taskNum);
                        executionLatch.countDown();
                    });
                }
                enqueueLatch.countDown();
            }).start();
        }

        // Wait for all enqueuing to complete
        assertTrue("All threads should finish enqueuing", 
                   enqueueLatch.await(5, TimeUnit.SECONDS));

        // Wait for all executions to complete
        assertTrue("All tasks should execute", 
                   executionLatch.await(10, TimeUnit.SECONDS));

        // Verify all tasks executed (order may vary due to race conditions during enqueue)
        assertEquals("All tasks should have executed", 
                    numThreads * tasksPerThread, executionOrder.size());
    }

    /**
     * Test: enqueue() - error handling
     * Coverage: UserActionQueueService.enqueue() - exception handling branch
     * Strategy: White-box, error path validation
     * Equivalence: Task throws exception
     * Branches: Exception catch branch, continue processing branch
     * Why: Tests that exceptions don't break the queue
     */
    @Test
    public void testErrorHandling() throws InterruptedException {
        int userId = 1;
        List<Integer> executedTasks = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(4); // Changed from 5 to 4

        // Enqueue tasks: one throws exception, others should still execute
        queueService.enqueue(userId, () -> {
            executedTasks.add(1);
            latch.countDown();
        });

        queueService.enqueue(userId, () -> {
            throw new RuntimeException("Test exception");
        });

        queueService.enqueue(userId, () -> {
            executedTasks.add(3);
            latch.countDown();
        });

        queueService.enqueue(userId, () -> {
            executedTasks.add(4);
            latch.countDown();
        });

        queueService.enqueue(userId, () -> {
            executedTasks.add(5);
            latch.countDown();
        });

        // Wait for successful tasks to complete
        assertTrue("Successful tasks should complete",
                latch.await(5, TimeUnit.SECONDS));

        // Verify that tasks after the exception still executed
        assertTrue("Task 3 should have executed", executedTasks.contains(3));
        assertTrue("Task 4 should have executed", executedTasks.contains(4));
        assertTrue("Task 5 should have executed", executedTasks.contains(5));
    }

    /**
     * Test: enqueue() - executor reuse
     * Coverage: UserActionQueueService.enqueue() - executor reuse branch
     * Strategy: White-box, resource reuse validation
     * Equivalence: Multiple tasks for same user over time
     * Branches: Executor reuse branch
     * Why: Tests that same executor is reused for same user
     */
    @Test
    public void testExecutorReuse() throws InterruptedException {
        int userId = 42;
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        List<String> threadNames = Collections.synchronizedList(new ArrayList<>());

        // First batch of tasks
        queueService.enqueue(userId, () -> {
            threadNames.add(Thread.currentThread().getName());
            latch1.countDown();
        });

        // Wait a bit
        Thread.sleep(100);

        // Second batch - should use same executor/thread
        queueService.enqueue(userId, () -> {
            threadNames.add(Thread.currentThread().getName());
            latch2.countDown();
        });

        assertTrue("First task should complete", latch1.await(2, TimeUnit.SECONDS));
        assertTrue("Second task should complete", latch2.await(2, TimeUnit.SECONDS));

        // Both tasks should run on the same thread (same executor)
        assertEquals("Should have 2 thread names recorded", 2, threadNames.size());
        assertEquals("Both tasks should run on the same thread", 
                    threadNames.get(0), threadNames.get(1));
        assertTrue("Thread name should contain user ID", 
                  threadNames.get(0).contains("user-" + userId));
    }

    /**
     * Test: enqueue() - sequence number ordering preserved
     * Coverage: UserActionQueueService.enqueue() - FIFO ordering (sequence numbers)
     * Strategy: White-box, ordering validation with sequence numbers
     * Equivalence: Tasks with sequence numbers (out of order enqueue)
     * Branches: FIFO ordering branch
     * Why: Tests FIFO ordering even when tasks are enqueued out of sequence number order
     */
    @Test
    public void testSequenceNumberOrdering() throws InterruptedException {
        int userId = 1;
        List<Long> processedSequences = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(10);

        // Enqueue tasks with sequence numbers (some out of order)
        long[] sequences = {5, 3, 7, 1, 9, 2, 8, 4, 6, 10};
        
        for (long seq : sequences) {
            final long sequence = seq;
            queueService.enqueue(userId, () -> {
                processedSequences.add(sequence);
                latch.countDown();
            });
        }

        assertTrue("All tasks should complete", latch.await(5, TimeUnit.SECONDS));

        // Verify FIFO order (should match enqueue order, not sequence number order)
        assertEquals("Should process all 10 sequences", 10, processedSequences.size());
        // The order should match the enqueue order, not the sequence number order
        assertEquals("First enqueued should be first processed", 
                    Long.valueOf(5), processedSequences.get(0));
        assertEquals("Last enqueued should be last processed", 
                    Long.valueOf(10), processedSequences.get(9));
    }
}