package com.himanshu.cache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drives a small pool of background threads that issue random {@code get}
 * and {@code put} calls against a shared {@link LRUCache}, so the web
 * dashboard has real concurrent activity to visualize instead of sitting
 * static. Intended purely as a demo/dev tool — start and stop are exposed
 * so a person can toggle it from the dashboard UI.
 * <p>
 * Each worker sleeps a randomized interval (120–400ms) between operations,
 * deliberately slow enough to watch individual evictions happen in the UI
 * rather than a tight loop that would just show a blur of numbers.
 */
public class TrafficSimulator {

    private final LRUCache<Integer, String> cache;
    private final int threadCount;
    private final int keySpace;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;

    public TrafficSimulator(LRUCache<Integer, String> cache, int threadCount, int keySpace) {
        this.cache = cache;
        this.threadCount = threadCount;
        this.keySpace = Math.max(keySpace, 1);
    }

    public synchronized void start() {
        if (running.get()) {
            return;
        }
        running.set(true);
        executor = Executors.newFixedThreadPool(threadCount);
        for (int t = 0; t < threadCount; t++) {
            final int workerId = t;
            executor.submit(() -> workerLoop(workerId));
        }
    }

    public synchronized void stop() {
        running.set(false);
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    private void workerLoop(int workerId) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int opCounter = 0;
        while (running.get()) {
            int key = random.nextInt(keySpace);
            // Roughly 1-in-3 ops is a write; the rest are reads, which is a
            // more realistic cache-traffic shape than an even split.
            if (random.nextInt(3) == 0) {
                cache.put(key, "sim-" + workerId + "-" + (opCounter++));
            } else {
                cache.get(key);
            }
            try {
                Thread.sleep(120 + random.nextInt(280));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
