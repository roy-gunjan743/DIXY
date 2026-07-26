package com.virtual_deadlock_detection.backend.config;
import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.entity.WaitEdgeEntity;
import com.virtual_deadlock_detection.backend.enums.ThreadState;
import com.virtual_deadlock_detection.backend.repository.LockRepository;
import com.virtual_deadlock_detection.backend.repository.ThreadRepository;
import com.virtual_deadlock_detection.backend.repository.WaitEdgeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DatabaseSeeder implements CommandLineRunner{

    private final ThreadRepository threadRepository;
    private final LockRepository lockRepository;
    private  final WaitEdgeRepository waitEdgeRepository;

    @Value("${app.seed-data.enabled}")
    private boolean seedDatabase;

    public DatabaseSeeder(ThreadRepository threadRepository,
                          LockRepository lockRepository,
                          WaitEdgeRepository waitEdgeRepository,
                          boolean seedDataEnabled){
        this.threadRepository=threadRepository;
        this.lockRepository=lockRepository;
        this.waitEdgeRepository=waitEdgeRepository;
    }

    @Override
    public void run(String... args){
        if (!seedDatabase)
        {
          return;
        }
        if (!isDatabaseEmpty())
        {
            return;
        }
        else {
            seedDatabase();
        }

    }
    public boolean isDatabaseEmpty(){
        long thredCount=threadRepository.count();
        long lockCount=lockRepository.count();
        long waitEdgeCount = waitEdgeRepository.count();

        if (thredCount == 0&&lockCount==0&&waitEdgeCount==0 )
        {
            return true;
        }
        else {
            return false;
        }
    }
    private ThreadEntity createThread(Long threadId, String threadName, ThreadState state, String carrierThread) {
        ThreadEntity thread = new ThreadEntity();
        thread.setThreadId(threadId);
        thread.setThreadName(threadName);
        thread.setState(state);
        thread.setCarrierThread(carrierThread);
        return threadRepository.save(thread);
    }

    private LockEntity createLock(String lockId, String lockType, ThreadEntity owner) {
        LockEntity lock = new LockEntity();
        lock.setLockId(lockId);
        lock.setLockType(lockType);
        lock.setOwnerThread(owner);
        return lockRepository.save(lock);
    }

    private void createWaitEdge(ThreadEntity waiting, LockEntity lock) {
        WaitEdgeEntity edge = new WaitEdgeEntity();
        edge.setWaitingThread(waiting);
        edge.setLock(lock);
        waitEdgeRepository.save(edge);
    }
    private void seedDatabase() {

        System.out.println("Seeding deadlock and pinning test data...");

        // 1. Create Deadlocked Threads
        ThreadEntity t1 = createThread(10023L, "JFR Event Stream 1", ThreadState.BLOCKED, "ForkJoinPool-1-worker-1");
        ThreadEntity t2 = createThread(10024L, "JFR Event Stream 2", ThreadState.BLOCKED, "ForkJoinPool-1-worker-2");

        // 2. Create Pinned Threads (Warnings)
        ThreadEntity p1 = createThread(2041L, "VirtualThread-2041", ThreadState.BLOCKED, "ForkJoinPool-1-worker-3");
        ThreadEntity p2 = createThread(2089L, "VirtualThread-2089", ThreadState.BLOCKED, "ForkJoinPool-1-worker-7");

        // 3. Create locks owned by deadlocked threads
        // Lock 1 owned by t2
        LockEntity lock1 = createLock("Monitor-2351897819504", "synchronized block", t2);
        // Lock 2 owned by t1
        LockEntity lock2 = createLock("Monitor-2351897819505", "synchronized block", t1);

        // 4. Create locks owned by other threads for pinning
        ThreadEntity carrierOwner1 = createThread(99991L, "CarrierOwner-1", ThreadState.RUNNABLE, null);
        ThreadEntity carrierOwner2 = createThread(99992L, "CarrierOwner-2", ThreadState.RUNNABLE, null);
        LockEntity pinLock1 = createLock("java.lang.Object@12abc3", "synchronized block", carrierOwner1);
        LockEntity pinLock2 = createLock("java.lang.Object@5f99ee", "synchronized block", carrierOwner2);

        // 5. Create Wait Edges to form the circular deadlock
        createWaitEdge(t1, lock1); // t1 waits for lock1 (held by t2)
        createWaitEdge(t2, lock2); // t2 waits for lock2 (held by t1)

        // 6. Create Wait Edges for pinning
        createWaitEdge(p1, pinLock1); // p1 waits on synchronized object 1
        createWaitEdge(p2, pinLock2); // p2 waits on synchronized object 2

        System.out.println("Seeding database complete.");

    }
}
