package com.himanshu.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LRUCache} covering:
 * <ul>
 *   <li>Basic get/put/remove/clear semantics</li>
 *   <li>LRU eviction order, including how {@code get} refreshes recency</li>
 *   <li>Thread-safety under concurrent, mixed read/write load using
 *       {@link ExecutorService}</li>
 * </ul>
 */
class CacheTest {

    private LRUCache<Integer, String> cache;

    @BeforeEach
    void setUp() {
        cache = new LRUCache<>(3);
    }

    // ---------------------------------------------------------------
    // Basic single-threaded correctness
    // ---------------------------------------------------------------

    @Test
    void testBasicPutAndGet() {
        cache.put(1, "one");
        cache.put(2, "two");

        assertEquals("one", cache.get(1));
        assertEquals("two", cache.get(2));
        assertEquals(2, cache.size());
    }

    @Test
    void testGetOnMissingKeyReturnsNull() {
        assertNull(cache.get(42));
    }

    @Test
    void testEvictionOfLeastRecentlyUsed() {
        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");
        assertEquals(3, cache.size());

        // Capacity is 3 and nothing has been accessed yet, so key 1 is LRU.
        cache.put(4, "four");

        assertNull(cache.get(1));
        assertEquals("two", cache.get(2));
        assertEquals("three", cache.get(3));
        assertEquals("four", cache.get(4));
        assertEquals(3, cache.size());
    }

    @Test
    void testAccessUpdatesRecencyAndChangesEvictionCandidate() {
        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");

        cache.get(1); // 1 is now Most Recently Used; 2 becomes LRU
        cache.put(4, "four"); // should evict 2, not 1

        assertNull(cache.get(2));
        assertEquals("one", cache.get(1));
        assertEquals("three", cache.get(3));
        assertEquals("four", cache.get(4));
    }

    @Test
    void testUpdatingExistingKeyDoesNotEvictAndRefreshesRecency() {
        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");

        cache.put(2, "TWO-updated"); // update, not insert -> no eviction
        assertEquals(3, cache.size());
        assertEquals("TWO-updated", cache.get(2));

        // 1 is now LRU (never touched again since initial put), so it goes next.
        cache.put(4, "four");
        assertNull(cache.get(1));
        assertEquals("TWO-updated", cache.get(2));
        assertEquals("three", cache.get(3));
        assertEquals("four", cache.get(4));
    }

    @Test
    void testRemoveAndClear() {
        cache.put(1, "one");
        cache.put(2, "two");

        cache.remove(1);
        assertNull(cache.get(1));
        assertEquals(1, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
        assertNull(cache.get(2));
    }

    @Test
    void testConstructorRejectsNonPositiveCapacity() {
        try {
            new LRUCache<Integer, String>(0);
            assertTrue(false, "Expected IllegalArgumentException for capacity 0");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // ---------------------------------------------------------------
    // Concurrency / thread-safety
    // ---------------------------------------------------------------

    @Test
    @Timeout(30)
    void testThreadSafetyUnderConcurrentReadWriteLoad() throws InterruptedException {
        int capacity = 50;
        LRUCache<Integer, Integer> concurrentCache = new LRUCache<>(capacity);

        int threadCount = 16;
        int opsPerThread = 2000;
        int keySpace = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        int key = ThreadLocalRandom.current().nextInt(keySpace);
                        if (ThreadLocalRandom.current().nextBoolean()) {
                            concurrentCache.put(key, key);
                        } else {
                            concurrentCache.get(key);
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(20, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(completed, "All threads should complete within timeout");
        assertEquals(0, errors.get(), "No exceptions should occur during concurrent access");
        assertTrue(
            concurrentCache.size() <= capacity,
            "Cache size (" + concurrentCache.size() + ") should never exceed capacity (" + capacity + ")"
        );
        assertEquals(
            concurrentCache.size(),
            concurrentCache.keysInOrder().size(),
            "HashMap size and linked-list length must always agree"
        );
    }

    @Test
    @Timeout(30)
    void testNoDataCorruptionWithDisjointKeysAcrossThreads() throws InterruptedException {
        // Each thread writes to its own disjoint key range, large enough
        // capacity that nothing gets evicted -> every write must be
        // durably and correctly visible afterward with no cross-thread
        // corruption of the shared HashMap / DoublyLinkedList state.
        int threadCount = 10;
        int keysPerThread = 100;
        int capacity = threadCount * keysPerThread;

        LRUCache<Integer, Integer> concurrentCache = new LRUCache<>(capacity);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int base = t * keysPerThread;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < keysPerThread; i++) {
                        int key = base + i;
                        concurrentCache.put(key, key * 10);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(20, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(capacity, concurrentCache.size());
        for (int i = 0; i < capacity; i++) {
            assertEquals(i * 10, concurrentCache.get(i));
        }
    }

    @Test
    @Timeout(30)
    void testEvictionRespectsCapacityUnderHeavyConcurrentPuts() throws InterruptedException {
        int capacity = 20;
        LRUCache<Integer, Integer> concurrentCache = new LRUCache<>(capacity);

        int threadCount = 12;
        int opsPerThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        // Unique, ever-increasing keys guarantee constant eviction pressure.
                        int key = threadId * opsPerThread + i;
                        concurrentCache.put(key, key);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(20, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(
            capacity,
            concurrentCache.size(),
            "Cache should settle at exactly its capacity after heavy eviction pressure"
        );
        assertEquals(capacity, concurrentCache.keysInOrder().size());
    }
}
