package com.himanshu.cache;

import java.io.IOException;

/**
 * Launches the live web dashboard: an {@link LRUCache}, a background
 * {@link TrafficSimulator} generating realistic concurrent load, and a
 * {@link DashboardServer} exposing both over HTTP.
 * <p>
 * Usage: {@code java -cp <classes> com.himanshu.cache.DashboardApp [capacity] [port]}
 * <br>Defaults: capacity 12, port 8080.
 */
public class DashboardApp {

    public static void main(String[] args) throws IOException {
        int capacity = args.length > 0 ? Integer.parseInt(args[0]) : 12;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;

        LRUCache<Integer, String> cache = new LRUCache<>(capacity);
        TrafficSimulator simulator = new TrafficSimulator(cache, 3, capacity + 8);
        DashboardServer server = new DashboardServer(cache, simulator, port);

        server.start();
        simulator.start();

        System.out.println("=== LRU Cache — Live Dashboard ===");
        System.out.println("Cache capacity : " + capacity);
        System.out.println("Dashboard URL  : http://localhost:" + port);
        System.out.println("Background traffic simulator: running (pause it from the dashboard)");
        System.out.println("Press Ctrl+C to stop.");
    }
}
