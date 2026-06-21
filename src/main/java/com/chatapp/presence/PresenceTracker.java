package com.chatapp.presence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class PresenceTracker {
    private final ConcurrentHashMap<String, Long> heartbeats = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor();
    private final long ttlMs;
    private final Consumer<String> onEvict; // callback → ChatService.disconnect()

    public PresenceTracker(long ttlMs, Consumer<String> onEvict) {
        this.ttlMs   = ttlMs;
        this.onEvict = onEvict;
    }

    public void start() {
        sweeper.scheduleAtFixedRate(this::sweepStale, 10, 10, TimeUnit.SECONDS);
    }

    public void heartbeat(String userId) {
        heartbeats.put(userId, System.currentTimeMillis());
    }

    public void remove(String userId) { heartbeats.remove(userId); }

    public boolean isOnline(String userId) {
        Long last = heartbeats.get(userId);
        return last != null && (System.currentTimeMillis() - last) < ttlMs;
    }

    public Set<String> onlineUsers() {
        return Collections.unmodifiableSet(heartbeats.keySet());
    }

    private void sweepStale() {
        long cutoff = System.currentTimeMillis() - ttlMs;
        // Collect first, then evict — avoids calling onEvict inside removeIf iteration
        List<String> toEvict = new ArrayList<>();
        heartbeats.entrySet().removeIf(e -> {
            if (e.getValue() < cutoff) { toEvict.add(e.getKey()); return true; }
            return false;
        });
        toEvict.forEach(userId -> {
            System.out.printf("  ⏱ evicting stale session: %s%n", userId);
            onEvict.accept(userId);
        });
    }

    public void stop() { sweeper.shutdown(); }
}