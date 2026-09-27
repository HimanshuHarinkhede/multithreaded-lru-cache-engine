package com.himanshu.cache;

/**
 * A generic node used inside the custom {@link DoublyLinkedList} that backs
 * the LRU cache. Each node stores the cache entry's key and value, plus
 * pointers to its neighbours so that removal / insertion can be done in
 * O(1) time without scanning the list.
 * <p>
 * The key is stored on the node (not just the value) so that when a node is
 * evicted from the tail of the list, the cache can remove the corresponding
 * entry from its backing {@code HashMap} in O(1) time as well.
 *
 * @param <K> the type of the cache key
 * @param <V> the type of the cache value
 */
final class Node<K, V> {

    K key;
    V value;
    Node<K, V> prev;
    Node<K, V> next;

    Node(K key, V value) {
        this.key = key;
        this.value = value;
    }
}
