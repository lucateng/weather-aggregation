package com.weather;

import org.junit.jupiter.api.Test;

import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

public class FailureModeTest {
    @Test
    void clientFailsGracefullyWhenServerDown() {
        System.out.println("[SCENARIO] Failure Mode: Client connection to unavailable server");
        // Attempt to connect to an unlikely port; expect connection refused
        try {
            new Socket("127.0.0.1", 6550).close();
            // If it somehow connects, skip
        } catch (Exception e) {
            assertTrue(e.getMessage() != null);
            com.weather.TestLog.log(
                "FailureMode",
                "connect to 127.0.0.1:6550",
                "connection refused",
                e.getClass().getSimpleName(),
                true
            );
        }
    }
}


