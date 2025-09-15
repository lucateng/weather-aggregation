package com.weather;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class RecoveryTest {
    @Test
    void promotesTempFileOnStartup() throws Exception {
        System.out.println("[SCENARIO] Recovery: Promote store.json.tmp on startup");
        File dir = new File("recovery-test");
        if (!dir.exists()) dir.mkdirs();
        File tmp = new File(dir, "store.json.tmp");
        File main = new File(dir, "store.json");
        Files.writeString(tmp.toPath(), "{\"records\":[{\"id\":\"R1\"}]}", StandardCharsets.UTF_8);
        new com.weather.server.AggregationServer(0, dir); // constructor promotes tmp if present
        File promoted = new File(dir, "store.json");
        assertTrue(promoted.exists());
        String s = Files.readString(promoted.toPath(), StandardCharsets.UTF_8);
        assertTrue(s.contains("\"R1\""));
        com.weather.TestLog.log(
            "Recovery",
            "write tmp → start server",
            "tmp promoted to store.json with id R1",
            s.contains("\"R1\"") ? "PROMOTED" : "NOT PROMOTED",
            s.contains("\"R1\"")
        );
    }
}


