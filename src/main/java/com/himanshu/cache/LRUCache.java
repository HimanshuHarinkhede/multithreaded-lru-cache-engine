package com.himanshu.cache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A thread-safe, fixed-capacity Least Recently Used (LRU) cache.
 * <p>
 * <b>Design</b>
 * <ul>
 *   <li>A {@link HashMap}{@code <K, Node<K,V>>} gives O(1) key lookup.</li>
 *   <li>A {@link DoublyLinkedList} maintains recency order so the
 *       most-recently-used entry sits at the head and the
 *       least-recently-used entry sits at the tail, giving O(1)
 *       promotion (move-to-head) and O(1) eviction (remove-tail).</li>
 *   <li>A {@link ReentrantReadWriteLock} guards all access to the shared
 *       {@code map} and {@code list} state.</li>
 * </ul>
 *
 * <b>Why {@code get()} takes the write lock, not the read lock</b>
 * <p>
 * A textbook LRU cache mutates its internal ordering on every successful
 * {@code get} (the accessed node is moved to the head of the list). That
 * means {@code get} is not a pure "read" from the data structure's point of
 * view — two threads calling {@code get} concurrently could otherwise race
 * on the doubly linked list pointers and corrupt it. To keep the O(1)
 * move-to-head behaviour correct under concurrency, this implementation
 * acquires the <i>write</i> lock for both {@code get} and {@code put}.
 * <p>
 * The read lock is still put to good use for genuinely read-only
 * operations that don't touch recency ordering, such as {@link #size()}
 * and the diagnostic {@link #keysInOrder()}, allowing those to run
 * concurrently with each other (but not with a write).
 * <p>
 * If you need higher read concurrency for {@code get}, the standard
 * approaches are: (a) shard the cache into several independently-locked
 * segments (like {@code ConcurrentHashMap} does internally), or (b) decouple
 * recency tracking from the read path entirely (e.g. approximate LRU via
 * a CLOCK/second-chance algorithm, or async recency updates). Those are
 * intentionally out of scope here to keep the reference implementation
 * simple and provably correct.
 *
 * @param <K> the type of the cache key
 * @param <V> the type of the cache value
 */
public class LRUCache<K, V> implements Cache<K, V> {

    private final int capacity;
    private final Map<K, Node<K, V>> map;
    private final DoublyLinkedList<K, V> list;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Plain counters, not protected by the read/write lock above — they're
    // independently atomic and only used for reporting (e.g. the dashboard),
    // so they never need to be consistent with map/list state at the same
    // instant, only eventually accurate.
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong putCount = new AtomicLong();
    private final AtomicLong evictionCount = new AtomicLong();

    public LRUCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive, got: " + capacity);
        }
        this.capacity = capacity;
        this.map = new HashMap<>(capacity);
        this.list = new DoublyLinkedList<>();
    }

    /**
     * O(1) lookup. Returns the value for {@code key}, or {@code null} if
     * absent, and promotes the entry to Most Recently Used.
     */
    @Override
    public V get(K key) {
        lock.writeLock().lock();
        try {
            Node<K, V> node = map.get(key);
            if (node == null) {
                missCount.incrementAndGet();
                return null;
            }
            list.moveToHead(node);
            hitCount.incrementAndGet();
            return node.value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * O(1) insert/update. If {@code key} already exists its value is
     * overwritten and it is promoted to Most Recently Used. Otherwise a new
     * entry is inserted at the head; if this pushes the cache over
     * capacity, the Least Recently Used entry (tail) is evicted.
     */
    @Override
    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            putCount.incrementAndGet();
            Node<K, V> existing = map.get(key);
            if (existing != null) {
                existing.value = value;
                list.moveToHead(existing);
                return;
            }

            Node<K, V> node = new Node<>(key, value);
            map.put(key, node);
            list.addToHead(node);

            if (map.size() > capacity) {
                Node<K, V> evicted = list.removeTail();
                if (evicted != null) {
                    map.remove(evicted.key);
                    evictionCount.incrementAndGet();
                    System.out.println(
                        "[EVICTION] capacity(" + capacity + ") exceeded -> evicted key: " + evicted.key
                    );
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** O(1) removal of {@code key}, if present. */
    @Override
    public void remove(K key) {
        lock.writeLock().lock();
        try {
            Node<K, V> node = map.remove(key);
            if (node != null) {
                list.removeNode(node);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Read-only; does not mutate recency, so the read lock suffices. */
    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return map.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Clears all entries from both the map and the linked list. */
    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            map.clear();
            while (!list.isEmpty()) {
                list.removeTail();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Diagnostic snapshot of keys ordered Most-Recently-Used to
     * Least-Recently-Used. Intended for logging/tests, not the hot path.
     */
    public List<K> keysInOrder() {
        lock.readLock().lock();
        try {
            return list.keysInOrder();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int capacity() {
        return capacity;
    }

    /**
     * A consistent, read-locked snapshot of the cache's current size and
     * recency order, alongside the running hit/miss/put/eviction counters.
     * Used by {@link DashboardServer} to render live stats; not part of the
     * {@link Cache} interface since it's reporting, not cache behaviour.
     */
    public CacheStats<K> getStats() {
        lock.readLock().lock();
        try {
            long hits = hitCount.get();
            long misses = missCount.get();
            double hitRate = (hits + misses) == 0 ? 0.0 : (double) hits / (hits + misses);
            return new CacheStats<>(
                capacity,
                map.size(),
                hits,
                misses,
                putCount.get(),
                evictionCount.get(),
                hitRate,
                list.keysInOrder()
            );
        } finally {
            lock.readLock().unlock();
        }
    }
}
