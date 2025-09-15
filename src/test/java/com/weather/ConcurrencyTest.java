package com.weather;

import com.weather.http.HttpParser;
import com.weather.http.HttpResponseMessage;
import com.weather.http.HttpResponseParser;
import com.weather.util.LamportClock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

public class ConcurrencyTest {
    static int port;
    @BeforeAll
    static void ensureServerRunning() throws Exception {
        System.out.println("[SCENARIO] Concurrency: Many simultaneous PUTs, then GET sees all");
        try (ServerSocket ss = new ServerSocket(0)) { port = ss.getLocalPort(); }
        new Thread(() -> {
            try {
                java.io.File dir = new java.io.File("conc-data");
                if (dir.exists()) { for (java.io.File f : dir.listFiles()) { f.delete(); } }
                new com.weather.server.AggregationServer(port, dir).start();
            } catch (Exception e) { throw new RuntimeException(e); }
        }).start();
        Thread.sleep(1000);
        System.out.println("[STEP] Server started on port " + port);
    }

    @Test
    void manyConcurrentPutsMaintainOrder() throws Exception {
        int n = 10; // keep under capacity 20 to avoid eviction
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Thread t = new Thread(() -> {
                try (Socket s = new Socket("127.0.0.1", port)) {
                    s.setSoTimeout(5000);
                    String json = "{\"id\":\"C" + idx + "\",\"name\":\"N" + idx + "\"}";
                    Map<String,String> h = new LinkedHashMap<>();
                    h.put("Host","localhost");
                    h.put("Content-Type","application/json");
                    h.put("Content-Length", Integer.toString(json.getBytes(StandardCharsets.UTF_8).length));
                    h.put(LamportClock.HEADER_LAMPORT, Long.toString(idx + 1));
                    h.put(LamportClock.HEADER_NODE_ID, "node-" + idx);
                    h.put(LamportClock.HEADER_ROLE, "content");
                    byte[] req = HttpParser.buildSimpleRequest("PUT","/weather.json", h, json.getBytes(StandardCharsets.UTF_8));
                    start.await();
                    OutputStream out = s.getOutputStream(); out.write(req); out.flush();
                    HttpResponseMessage resp = HttpResponseParser.readResponse(s.getInputStream());
                    assertTrue(resp.statusCode == 200 || resp.statusCode == 201);
                } catch (Exception e) { fail(e); }
                finally { done.countDown(); }
            });
            threads.add(t);
            t.start();
        }
        start.countDown();
        done.await();

        // Poll GET until we see all ids
        boolean ok = false;
        for (int attempt = 0; attempt < 20 && !ok; attempt++) {
            try (Socket s = new Socket("127.0.0.1", port)) {
                Map<String,String> h = new LinkedHashMap<>();
                h.put("Host","localhost");
                h.put(LamportClock.HEADER_LAMPORT, "1000");
                h.put(LamportClock.HEADER_NODE_ID, "client");
                h.put(LamportClock.HEADER_ROLE, "client");
                byte[] req = HttpParser.buildSimpleRequest("GET","/weather.json", h, null);
                OutputStream out = s.getOutputStream(); out.write(req); out.flush();
                HttpResponseMessage r = HttpResponseParser.readResponse(s.getInputStream());
                String body = new String(r.body, StandardCharsets.UTF_8);
                ok = true;
                for (int i = 0; i < n; i++) {
                    if (!body.contains("\"id\":\"C"+i+"\"")) { ok = false; break; }
                }
                if (!ok) Thread.sleep(100);
            }
        }
        TestLog.log(
            "Concurrency",
            "start server → spawn " + n + " PUT threads → GET",
            "GET body contains all C0..C" + (n-1),
            ok ? "ALL FOUND" : "MISSING",
            ok
        );
        assertTrue(ok, "Not all records visible after concurrent PUTs");
    }
}


