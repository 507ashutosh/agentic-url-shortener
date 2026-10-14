package com.agentic.shortener.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limiter: each key (client IP) gets {@code limit} requests per window.
 * State is in memory, so it applies per application instance.
 */
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(long startMillis, int count) {
    }

    public RateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /** Returns true if this request is within the limit for the key. */
    public boolean tryAcquire(String key) {
        long now = clock.millis();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> isExpired(w, now));
        }
        // compute() is atomic per key, so concurrent requests from one client are counted correctly.
        Window window = windows.compute(key, (k, current) ->
                current == null || isExpired(current, now)
                        ? new Window(now, 1)
                        : new Window(current.startMillis(), current.count() + 1));
        return window.count() <= limit;
    }

    public long windowSeconds() {
        return Math.max(1, windowMillis / 1000);
    }

    private boolean isExpired(Window window, long now) {
        return now - window.startMillis() >= windowMillis;
    }
}
