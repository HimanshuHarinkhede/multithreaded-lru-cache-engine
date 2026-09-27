package com.himanshu.cache;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * A minimal embedded HTTP server — built on the JDK's own
 * {@code com.sun.net.httpserver.HttpServer}, so no extra Maven
 * dependencies are needed — that serves the live cache dashboard.
 * <p>
 * Routes:
 * <ul>
 *   <li>{@code GET  /}                    — the dashboard HTML page</li>
 *   <li>{@code GET  /api/stats}           — current {@link CacheStats} as JSON</li>
 *   <li>{@code GET  /api/put?key=&value=} — put a key/value, returns updated stats</li>
 *   <li>{@code GET  /api/get?key=}        — get a key, returns its value + updated stats</li>
 *   <li>{@code GET  /api/clear}           — clears the cache, returns updated stats</li>
 *   <li>{@code GET  /api/traffic/start}   — starts the background traffic simulator</li>
 *   <li>{@code GET  /api/traffic/stop}    — stops the background traffic simulator</li>
 * </ul>
 * <p>
 * Note the mutating endpoints intentionally accept plain {@code GET}
 * requests with query parameters rather than following strict REST verb
 * conventions. That keeps the browser-side JavaScript trivial (plain
 * {@code fetch(url)} calls, no request bodies to serialize) for what is a
 * local single-user developer tool, not a public API — this is a
 * deliberate simplicity trade-off, not an oversight.
 */
public class DashboardServer {

    private final LRUCache<Integer, String> cache;
    private final TrafficSimulator simulator;
    private final int port;
    private HttpServer server;

    public DashboardServer(LRUCache<Integer, String> cache, TrafficSimulator simulator, int port) {
        this.cache = cache;
        this.simulator = simulator;
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handleIndex);
        server.createContext("/api/stats", this::handleStats);
        server.createContext("/api/put", this::handlePut);
        server.createContext("/api/get", this::handleGet);
        server.createContext("/api/clear", this::handleClear);
        server.createContext("/api/traffic/start", this::handleTrafficStart);
        server.createContext("/api/traffic/stop", this::handleTrafficStop);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ---------------------------------------------------------------
    // Route handlers
    // ---------------------------------------------------------------

    private void handleIndex(HttpExchange exchange) throws IOException {
        byte[] html = DashboardHtml.PAGE.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseCode(200);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(html);
        }
    }

    private void handleStats(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, statsJson());
    }

    private void handlePut(HttpExchange exchange) throws IOException {
        Map<String, String> params = queryParams(exchange.getRequestURI());
        String rawKey = params.get("key");
        String value = params.getOrDefault("value", "");
        if (rawKey == null || rawKey.isBlank()) {
            sendJson(exchange, 400, "{\"error\":\"key is required\"}");
            return;
        }
        try {
            int key = Integer.parseInt(rawKey.trim());
            cache.put(key, value);
            sendJson(exchange, 200, statsJson());
        } catch (NumberFormatException e) {
            sendJson(exchange, 400, "{\"error\":\"key must be an integer\"}");
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        Map<String, String> params = queryParams(exchange.getRequestURI());
        String rawKey = params.get("key");
        if (rawKey == null || rawKey.isBlank()) {
            sendJson(exchange, 400, "{\"error\":\"key is required\"}");
            return;
        }
        try {
            int key = Integer.parseInt(rawKey.trim());
            String value = cache.get(key);
            String valueJson = value == null ? "null" : "\"" + escape(value) + "\"";
            String body = "{\"key\":" + key + ",\"value\":" + valueJson + ",\"stats\":" + statsJson() + "}";
            sendJson(exchange, 200, body);
        } catch (NumberFormatException e) {
            sendJson(exchange, 400, "{\"error\":\"key must be an integer\"}");
        }
    }

    private void handleClear(HttpExchange exchange) throws IOException {
        cache.clear();
        sendJson(exchange, 200, statsJson());
    }

    private void handleTrafficStart(HttpExchange exchange) throws IOException {
        simulator.start();
        sendJson(exchange, 200, "{\"trafficRunning\":true}");
    }

    private void handleTrafficStop(HttpExchange exchange) throws IOException {
        simulator.stop();
        sendJson(exchange, 200, "{\"trafficRunning\":false}");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private String statsJson() {
        CacheStats<Integer> stats = cache.getStats();
        StringBuilder order = new StringBuilder("[");
        List<Integer> keys = stats.order();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                order.append(",");
            }
            order.append(keys.get(i));
        }
        order.append("]");

        return "{"
            + "\"capacity\":" + stats.capacity() + ","
            + "\"size\":" + stats.size() + ","
            + "\"hits\":" + stats.hits() + ","
            + "\"misses\":" + stats.misses() + ","
            + "\"puts\":" + stats.puts() + ","
            + "\"evictions\":" + stats.evictions() + ","
            + "\"hitRate\":" + stats.hitRate() + ","
            + "\"order\":" + order + ","
            + "\"trafficRunning\":" + simulator.isRunning()
            + "}";
    }

    private static Map<String, String> queryParams(URI uri) {
        Map<String, String> params = new HashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            return params;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx < 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseCode(status);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ");
    }
}
