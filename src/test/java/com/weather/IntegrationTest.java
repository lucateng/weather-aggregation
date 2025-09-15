package com.weather;

import com.weather.http.HttpParser;
import com.weather.http.HttpResponseMessage;
import com.weather.http.HttpResponseParser;
import com.weather.server.AggregationServer;
import com.weather.util.LamportClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class IntegrationTest {
    static Thread serverThread;
    static int port;

    @BeforeAll
    static void startServer() throws Exception {
        System.out.println("[SCENARIO] Golden Path: ContentServer PUT then GETClient retrieves same record");
        try (ServerSocket ss = new ServerSocket(0)) { port = ss.getLocalPort(); }
        serverThread = new Thread(() -> {
            try {
                java.io.File dir = new java.io.File("it-data");
                if (dir.exists()) { for (java.io.File f : dir.listFiles()) { f.delete(); } }
                new com.weather.server.AggregationServer(port, dir).start();
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(1000);
        System.out.println("[STEP] Server started on port " + port);
    }

    @AfterAll
    static void stopServer() {
        // Server currently runs indefinitely; tests don't stop it. In a real system we'd add a shutdown hook.
    }

    @Test
    void testPutThenGet() throws Exception {
        String json = "{\"id\":\"TEST1\",\"name\":\"X\",\"lat\":1.0,\"lon\":2.0}";
        LamportClock clock = new LamportClock();
        System.out.println("[STEP] Sending PUT for id=TEST1");
        try (Socket s = new Socket("127.0.0.1", port)) {
            Map<String,String> h = new HashMap<>();
            h.put("Host","localhost");
            h.put("Content-Type","application/json");
            h.put("Content-Length", Integer.toString(json.getBytes(StandardCharsets.UTF_8).length));
            h.put(LamportClock.HEADER_LAMPORT, Long.toString(clock.tick()));
            h.put(LamportClock.HEADER_NODE_ID, "cs-1");
            h.put(LamportClock.HEADER_ROLE, "content");
            byte[] req = HttpParser.buildSimpleRequest("PUT","/weather.json", h, json.getBytes(StandardCharsets.UTF_8));
            OutputStream out = s.getOutputStream();
            out.write(req); out.flush();
            HttpResponseMessage resp = HttpResponseParser.readResponse(s.getInputStream());
            assertTrue(resp.statusCode == 200 || resp.statusCode == 201);
            System.out.println("[RESULT] PUT response: " + resp.statusCode);
        }
        boolean seen = false;
        System.out.println("[STEP] Polling GET until record appears (max 2s)");
        for (int attempt = 0; attempt < 20 && !seen; attempt++) {
            try (Socket s = new Socket("127.0.0.1", port)) {
                Map<String,String> h = new HashMap<>();
                h.put("Host","localhost");
                h.put(LamportClock.HEADER_LAMPORT, Long.toString(clock.tick()));
                h.put(LamportClock.HEADER_NODE_ID, "client-1");
                h.put(LamportClock.HEADER_ROLE, "client");
                byte[] req = HttpParser.buildSimpleRequest("GET","/weather.json", h, null);
                OutputStream out = s.getOutputStream();
                out.write(req); out.flush();
                HttpResponseMessage resp = HttpResponseParser.readResponse(s.getInputStream());
                String body = new String(resp.body, StandardCharsets.UTF_8);
                assertEquals(200, resp.statusCode);
                seen = body.contains("\"TEST1\"") || body.contains("TEST1");
                System.out.println("[INFO] GET attempt " + (attempt+1) + ": " + body);
                if (!seen) Thread.sleep(100);
            }
        }
        TestLog.log(
            "Golden Path",
            "start server → PUT id=TEST1 → poll GET",
            "GET body contains TEST1",
            seen ? "FOUND" : "NOT FOUND",
            seen
        );
        assertTrue(seen, "Record TEST1 not visible after PUT");
    }
}


