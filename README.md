# Multi-Threaded In-Memory LRU Cache Engine

A thread-safe **Least Recently Used (LRU) cache** built from scratch in Core Java — no external caching libraries — achieving **O(1) time complexity** for both `get` and `put`. Includes a live web dashboard that visualizes cache state, hit/miss rates, and evictions in real time.

## Why this exists

Most real systems (CDNs, database query caches, session stores, Linux's own page cache) rely on some form of LRU eviction: keep what's used often, discard what's gone untouched the longest. A HashMap alone gives you O(1) lookup but no sense of recency. A linked list alone tracks recency but needs a full scan to find anything. Combining the two — the core idea in this repo — gets you O(1) on both operations.

## Features

- **O(1) `get`/`put`** via a `HashMap<K, Node>` + custom doubly linked list (no scanning, ever)
- **Thread-safe** under concurrent access using `ReentrantReadWriteLock`
- **Live web dashboard** — an embedded HTTP server (the JDK's own `HttpServer`, zero extra dependencies) serves a real-time view of cache order, hits, misses, hit rate, and evictions
- **Background traffic simulator** — 3 concurrent threads generate random reads/writes so you can watch eviction happen live
- **JUnit 5 test suite** covering eviction correctness and thread-safety under heavy concurrent load

## How it works

| Component | Role |
|---|---|
| `Node<K,V>` | One cache entry: key, value, and pointers to its neighbors |
| `DoublyLinkedList` | Tracks recency order — head = most recently used, tail = least recently used. Moving an entry to the front or evicting the tail is O(1) |
| `HashMap<K, Node>` | Instant lookup from key → node, so `get` never scans the list |
| `ReentrantReadWriteLock` | Guards every mutation so concurrent threads can't corrupt the linked list |

`get(key)` finds the node via the map, then slides it to the front of the list (marking it most-recently-used).
`put(key, value)` inserts at the front; if the cache is over capacity, the tail node — the one nobody's touched in the longest time — is evicted.

## Project structure

```
multithreaded-lru-cache/
├── pom.xml
├── src/
│   ├── main/java/com/himanshu/cache/
│   │   ├── Node.java              # cache entry + linked-list pointers
│   │   ├── DoublyLinkedList.java  # O(1) recency-ordered list
│   │   ├── Cache.java             # generic cache interface
│   │   ├── LRUCache.java          # thread-safe engine (the core)
│   │   ├── CacheStats.java        # immutable stats snapshot
│   │   ├── TrafficSimulator.java  # background concurrent load generator
│   │   ├── DashboardHtml.java     # dashboard page, embedded as a Java string
│   │   ├── DashboardServer.java   # embedded HTTP server + JSON API
│   │   ├── DashboardApp.java      # entry point: launches the live dashboard
│   │   └── Main.java              # console-only demo (no web server)
│   └── test/java/com/himanshu/cache/
│       └── CacheTest.java         # JUnit 5: eviction + concurrency tests
```

## Getting started

**Prerequisites:** JDK 17+, Maven 3.6+

```bash
git clone <your-repo-url>
cd multithreaded-lru-cache
mvn test          # run the test suite
```

### Run the console demo

```bash
mvn compile exec:java -Dexec.mainClass=com.himanshu.cache.Main
```

### Run the live dashboard

```bash
mvn compile exec:java -Dexec.mainClass=com.himanshu.cache.DashboardApp
```

Then open **http://localhost:8080**. Optionally pass a capacity and port:

```bash
mvn compile exec:java -Dexec.mainClass=com.himanshu.cache.DashboardApp -Dexec.args="20 9090"
```

## Dashboard

The dashboard shows the cache's actual internal state, not a mock — the row of "memory cells" mirrors the real doubly linked list order, updating live as the background traffic simulator (or your own manual puts/gets) run.

| Endpoint | Description |
|---|---|
| `GET /` | Dashboard page |
| `GET /api/stats` | Current cache stats as JSON |
| `GET /api/put?key=&value=` | Insert/update a key |
| `GET /api/get?key=` | Read a key |
| `GET /api/clear` | Clear the cache |
| `GET /api/traffic/start` / `/stop` | Toggle the background traffic simulator |

## Tech stack

- Java 17
- Maven
- JUnit 5 (Jupiter)
- JDK's built-in `com.sun.net.httpserver.HttpServer` — no web framework dependency

## Use cases

This pattern underlies real infrastructure: CDN edge caches, database query result caches, in-memory session stores, and OS-level page caches (like Linux's) all use LRU or an LRU variant to decide what to evict when memory is limited.

## License

MIT — feel free to use this for learning, interviews, or as a starting point for your own caching layer.
