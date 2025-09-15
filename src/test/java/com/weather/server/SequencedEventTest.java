package com.weather.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SequencedEventTest {
    @Test
    void ordersBySenderLamportThenNodeThenSeq() {
        System.out.println("[SCENARIO] Critical Logic: SequencedEvent.compareTo ordering");
        SequencedEvent a = new SequencedEvent(5L, "A", 1L, () -> {});
        SequencedEvent b = new SequencedEvent(6L, "A", 1L, () -> {});
        assertTrue(a.compareTo(b) < 0);

        SequencedEvent c = new SequencedEvent(6L, "A", 1L, () -> {});
        SequencedEvent d = new SequencedEvent(6L, "B", 0L, () -> {});
        assertTrue(c.compareTo(d) < 0); // A < B when lamport equal

        SequencedEvent e = new SequencedEvent(6L, "A", 1L, () -> {});
        SequencedEvent f = new SequencedEvent(6L, "A", 2L, () -> {});
        assertTrue(e.compareTo(f) < 0); // seq 1 < 2 when lamport and node equal

        assertEquals(0, e.compareTo(new SequencedEvent(6L, "A", 1L, () -> {})));
        com.weather.TestLog.log(
            "compareTo",
            "build six events → compare across keys",
            "order by lamport, then node, then seq",
            "OK",
            true
        );
    }
}


