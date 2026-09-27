package com.himanshu.cache;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Console demo of the {@link LRUCache}: first a simple single-threaded walk
 * through showing eviction order, then a concurrent multi-threaded
 * read/write workload driven by an {@link ExecutorService} to show the
 * cache surviving contention without corruption or exceptions.
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Multi-Threaded In-Memory LRU Cache Engine Demo ===\n");

        runSingleThreadedDemo();
        System.out.println();
        runConcurrentWorkloadDemo();

        System.out.println("\n=== Demo complete ===");
    }

    private static void runSingleThreadedDemo() {
        int capacity = 5;
        LRUCache<Integer, String> cache = new LRUCache<>(capacity);

        System.out.println("--- Single-threaded basic demo (capacity=" + capacity + ") ---");
        for (int i = 1; i <= 7; i++) {
            cache.put(i, "value-" + i);
            System.out.println("PUT " + i + " -> order (MRU..LRU): " + cache.keysInOrder());
        }

        System.out.println("GET 3 -> " + cache.get(3));
        System.out.println("Order after GET 3 (MRU..LRU): " + cache.keysInOrder());

        cache.put(8, "value-8");
        System.out.println("PUT 8 -> order (MRU..LRU): " + cache.keysInOrder());
        System.out.println("(Key 4 was evicted here, not 3, because GET 3 refreshed its recency.)");
    }

    private static void runConcurrentWorkloadDemo() throws InterruptedException {
        System.out.println("--- Multi-threaded concurrent workload demo ---");

        int capacity = 10;
        LRUCache<Integer, String> cache = new LRUCache<>(capacity);

        int threadCount = 8;
        int opsPerThread = 2000;
        int keySpace = 25;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Random seedRandom = new Random();

        System.out.println(
            "Launching " + threadCount + " threads, " + opsPerThread
                + " ops/thread, capacity=" + capacity + ", keySpace=[0," + keySpace + ")"
        );

        long start = System.nanoTime();
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            final long seed = seedRandom.nextLong();
            executor.submit(() -> {
                try {
                    Random random = new Random(seed);
                    for (int i = 0; i < opsPerThread; i++) {
                        int key = random.nextInt(keySpace);
                        if (random.nextBoolean()) {
                            cache.put(key, "t" + threadId + "-op" + i);
                        } else {
                            cache.get(key);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finishedInTime = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("All threads finished within timeout: " + finishedInTime);
        System.out.println("Elapsed time: " + elapsedMs + " ms");
        System.out.println("Final cache size: " + cache.size() + " (capacity=" + capacity + ")");
        System.out.println("Final order (MRU..LRU): " + cache.keysInOrder());

        // A quick sanity demo using ThreadLocalRandom too, showing the API
        // is safe to call from arbitrary threads without extra setup.
        System.out.println("Sample GET after workload, key="
            + keySpaceSample(keySpace) + " -> " + cache.get(keySpaceSample(keySpace)));
    }

    private static int keySpaceSample(int keySpace) {
        return ThreadLocalRandom.current().nextInt(keySpace);
    }
}
