package com.weather.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe Lamport clock implementation.
 * Each event should call tick(); receiving a message with a remote timestamp should call receive(remoteTs).
 */
public final class LamportClock {
    public static final String HEADER_LAMPORT = "X-Lamport";
    public static final String HEADER_NODE_ID = "X-Node-Id";
    public static final String HEADER_ROLE = "X-Role"; // content | client | server

    private final AtomicLong counter = new AtomicLong(0L);

    public long get() {
        return counter.get();
    }

    public long tick() {
        return counter.incrementAndGet();
    }

    public long receive(long remoteTimestamp) {
        while (true) {
            long current = counter.get();
            long next = Math.max(current, remoteTimestamp) + 1L;
            if (counter.compareAndSet(current, next)) {
                return next;
            }
        }
    }
}


