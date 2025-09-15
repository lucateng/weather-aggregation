package com.weather;

import com.weather.util.LamportClock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LamportClockTest {
    @Test
    void tickIncrements() {
        LamportClock c = new LamportClock();
        assertEquals(0L, c.get());
        assertEquals(1L, c.tick());
        assertEquals(2L, c.tick());
    }

    @Test
    void receiveOrders() {
        LamportClock a = new LamportClock();
        LamportClock b = new LamportClock();
        long a1 = a.tick(); // 1
        long b1 = b.receive(a1); // 2
        assertTrue(b1 > a1);
        long a2 = a.receive(b1); // 3
        assertTrue(a2 > b1);
    }
}
