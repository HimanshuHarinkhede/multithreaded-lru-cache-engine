package com.himanshu.cache;

import java.util.List;

/**
 * An immutable snapshot of an {@link LRUCache}'s state at one point in
 * time: its capacity, current size, running hit/miss/put/eviction counts,
 * derived hit rate, and the current recency order (Most Recently Used
 * first, Least Recently Used last).
 * <p>
 * Produced by {@link LRUCache#getStats()} and serialized to JSON by
 * {@link DashboardServer} for the live web dashboard.
 *
 * @param <K> the type of the cache key
 */
public record CacheStats<K>(
    int capacity,
    int size,
    long hits,
    long misses,
    long puts,
    long evictions,
    double hitRate,
    List<K> order
) {
}
