package com.himanshu.cache;

/**
 * A generic key-value cache contract. Implementations decide the eviction
 * policy (e.g. LRU, LFU, FIFO) and the concurrency guarantees.
 *
 * @param <K> the type of the cache key
 * @param <V> the type of the cache value
 */
public interface Cache<K, V> {

    /**
     * Returns the value associated with {@code key}, or {@code null} if no
     * mapping exists. Accessing a key typically refreshes its recency in
     * eviction-aware caches such as LRU.
     */
    V get(K key);

    /**
     * Associates {@code value} with {@code key}. If the key already exists,
     * its value is updated and its recency refreshed. If the cache is at
     * capacity and a new key is inserted, an eviction is triggered.
     */
    void put(K key, V value);

    /** Removes the mapping for {@code key}, if present. */
    void remove(K key);

    /** Returns the number of entries currently held in the cache. */
    int size();

    /** Removes all entries from the cache. */
    void clear();
}
