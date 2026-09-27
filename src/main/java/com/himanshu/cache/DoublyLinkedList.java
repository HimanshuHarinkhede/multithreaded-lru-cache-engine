package com.himanshu.cache;

import java.util.ArrayList;
import java.util.List;

/**
 * A minimal intrusive doubly linked list purpose-built for the LRU cache.
 * <p>
 * Two sentinel (dummy) nodes — {@code head} and {@code tail} — bound the
 * list so that every real insertion/removal is branch-free and O(1), with
 * no null-checks needed at the boundaries.
 * <p>
 * Ordering convention: {@code head.next} is the Most Recently Used (MRU)
 * entry, and {@code tail.prev} is the Least Recently Used (LRU) entry —
 * i.e. the next candidate for eviction.
 * <p>
 * This class is <b>not</b> thread-safe by itself; thread-safety is the
 * responsibility of the caller ({@link LRUCache}), which guards every call
 * into this list with a {@code ReentrantReadWriteLock}.
 *
 * @param <K> the type of the cache key
 * @param <V> the type of the cache value
 */
final class DoublyLinkedList<K, V> {

    private final Node<K, V> head;
    private final Node<K, V> tail;

    DoublyLinkedList() {
        head = new Node<>(null, null);
        tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }

    /** Inserts {@code node} immediately after the head sentinel. O(1). */
    void addToHead(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    /** Unlinks {@code node} from wherever it currently sits. O(1). */
    void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.prev = null;
        node.next = null;
    }

    /** Moves an already-linked {@code node} to the head (marks it MRU). O(1). */
    void moveToHead(Node<K, V> node) {
        removeNode(node);
        addToHead(node);
    }

    /**
     * Removes and returns the node just before the tail sentinel (the LRU
     * entry), or {@code null} if the list is empty. O(1).
     */
    Node<K, V> removeTail() {
        if (isEmpty()) {
            return null;
        }
        Node<K, V> lru = tail.prev;
        removeNode(lru);
        return lru;
    }

    boolean isEmpty() {
        return head.next == tail;
    }

    /**
     * Returns the keys ordered from Most Recently Used to Least Recently
     * Used. Intended for debugging, logging, and tests — not part of the
     * O(1) hot path.
     */
    List<K> keysInOrder() {
        List<K> keys = new ArrayList<>();
        Node<K, V> current = head.next;
        while (current != tail) {
            keys.add(current.key);
            current = current.next;
        }
        return keys;
    }
}
