package com.weather.server;

import com.weather.http.*;
import com.weather.json.*;
import com.weather.model.WeatherRecord;
import com.weather.store.AggregationStore;
import com.weather.util.LamportClock;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregation server with Lamport ordering, 30s expiry, GET and PUT minimal HTTP over raw sockets.
 */
public final class AggregationServer {
    private final int port;
    private final String nodeId;
    private final LamportClock clock = new LamportClock();
    private final AggregationStore store;
    private final PriorityBlockingQueue<SequencedEvent> eventQueue = new PriorityBlockingQueue<>(1024);
    private final AtomicLong arrivalSeq = new AtomicLong(0L);
    private final ExecutorService workers = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));

    public AggregationServer(int port, File dataDir) throws IOException {
        this.port = port;
        this.nodeId = UUID.randomUUID().toString();
        this.store = new AggregationStore(dataDir, 20);
        // Single-threaded sequencer to serialize GETs and PUTs by Lamport order (then arrival)
        Thread sequencer = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    SequencedEvent ev = eventQueue.take();
                    ev.process();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception ignored) {}
            }
        }, "lamport-sequencer");
        sequencer.setDaemon(true);
        sequencer.start();

        Thread reaper = new Thread(() -> {
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(5);
                    store.expireOldSources();
                } catch (Exception ignored) {}
            }
        }, "expiry-reaper");
        reaper.setDaemon(true);
        reaper.start();
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket client = serverSocket.accept();
                workers.submit(() -> handle(client));
            }
        }
    }

    private void handle(Socket socket) {
        try (socket) {
            socket.setSoTimeout(15000);
            InputStream in = socket.getInputStream();
            ConnectionContext ctx = new ConnectionContext();
            while (true) {
                HttpRequest req = HttpParser.readRequest(in);
                if (req == null) break;
                long remoteTs = parseLamport(req.header(LamportClock.HEADER_LAMPORT));
                clock.receive(remoteTs);
                long serverSequence = arrivalSeq.incrementAndGet();
                boolean close = wantsClose(req);
                CountDownLatch done = new CountDownLatch(1);
                String senderNodeId = headerCaseInsensitive(req, LamportClock.HEADER_NODE_ID);
                if (senderNodeId == null) senderNodeId = "unknown";
                long senderLamport = remoteTs;
                SequencedEvent ev = new SequencedEvent(senderLamport, senderNodeId, serverSequence, () -> {
                    try {
                        HttpResponse resp;
                        switch (req.method) {
                            case "GET":
                                resp = handleGet(req);
                                break;
                            case "PUT":
                                resp = handlePut(req, ctx);
                                break;
                            default:
                                resp = new HttpResponse(HttpStatus.BAD_REQUEST).bodyText("Unsupported method", "text/plain");
                        }
                        if (close) resp.header("Connection", "close"); else resp.header("Connection", "keep-alive");
                        resp.header(LamportClock.HEADER_LAMPORT, Long.toString(clock.tick()));
                        resp.header(LamportClock.HEADER_NODE_ID, nodeId);
                        resp.header(LamportClock.HEADER_ROLE, "server");
                        socket.getOutputStream().write(resp.serialize());
                        socket.getOutputStream().flush();
                    } catch (IOException ignored) {
                    } finally {
                        done.countDown();
                    }
                });
                eventQueue.put(ev);
                done.await();
                if (close) break;
            }
        } catch (Exception e) {
            try {
                HttpResponse resp = new HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR).bodyText("Internal error", "text/plain");
                resp.header(LamportClock.HEADER_LAMPORT, Long.toString(clock.tick()));
                resp.header(LamportClock.HEADER_NODE_ID, nodeId);
                resp.header(LamportClock.HEADER_ROLE, "server");
                socket.getOutputStream().write(resp.serialize());
            } catch (IOException ignored) {}
        }
    }

    private HttpResponse handleGet(HttpRequest req) {
        String stationId = null;
        if (req.query != null && !req.query.isEmpty()) {
            for (String p : req.query.split("&")) {
                int i = p.indexOf('=');
                String k = i >= 0 ? p.substring(0, i) : p;
                String v = i >= 0 ? p.substring(i + 1) : "";
                if (k.equals("id")) stationId = decode(v);
            }
        }
        JsonArray arr = store.snapshotJson(stationId);
        JsonObject root = new JsonObject().put("data", arr);
        return new HttpResponse(HttpStatus.OK)
                .header("Content-Type", "application/json; charset=utf-8")
                .body(root.toJson().getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse handlePut(HttpRequest req, ConnectionContext ctx) {
        if (req.body.length == 0) {
            String node = headerCaseInsensitive(req, LamportClock.HEADER_NODE_ID);
            if (node == null || node.isEmpty()) node = "unknown";
            store.updateLastSeen(node);
            return new HttpResponse(HttpStatus.NO_CONTENT);
        }
        String contentType = headerCaseInsensitive(req, "Content-Type");
        if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
            return new HttpResponse(HttpStatus.BAD_REQUEST).bodyText("Content-Type must be application/json", "text/plain");
        }
        String node = headerCaseInsensitive(req, LamportClock.HEADER_NODE_ID);
        if (node == null || node.isEmpty()) node = "unknown";
        long remoteTs = parseLamport(req.header(LamportClock.HEADER_LAMPORT));
        long arrivalMs = System.currentTimeMillis();
        String text = new String(req.body, StandardCharsets.UTF_8);
        try {
            JsonValue parsed = JsonParser.parse(text);
            if (!(parsed instanceof JsonObject)) {
                return new HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR).bodyText("Expected JSON object", "text/plain");
            }
            JsonObject obj = (JsonObject) parsed;
            Map<String, String> fields = new LinkedHashMap<>();
            for (Map.Entry<String, JsonValue> e : obj.entries().entrySet()) {
                JsonValue v = e.getValue();
                String repr;
                if (v instanceof JsonString) repr = ((JsonString) v).value;
                else if (v instanceof JsonNumber) repr = ((JsonNumber) v).value;
                else repr = v.toJson();
                fields.put(e.getKey(), repr);
            }
            if (!fields.containsKey("id") || fields.get("id").isEmpty()) {
                return new HttpResponse(HttpStatus.BAD_REQUEST).bodyText("Missing id", "text/plain");
            }
            String finalNode = node;
            WeatherRecord record = new WeatherRecord(fields, finalNode, remoteTs, arrivalMs);
            boolean created = !ctx.seenSuccessfulPut;
            try {
                // Apply before responding to guarantee visibility for subsequent GETs
                store.addRecord(record);
                store.updateLastSeen(node);
                ctx.seenSuccessfulPut = true;
                return new HttpResponse(created ? HttpStatus.CREATED : HttpStatus.OK).bodyText("OK", "text/plain");
            } catch (IOException io) {
                return new HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR).bodyText("Persist failed", "text/plain");
            }
        } catch (IllegalArgumentException ex) {
            return new HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR).bodyText("Invalid JSON", "text/plain");
        }
    }

    private static long parseLamport(String s) {
        if (s == null) return 0L;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0L; }
    }

    private static String decode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    private static String headerCaseInsensitive(HttpRequest req, String name) {
        String v = req.header(name);
        if (v != null) return v;
        for (Map.Entry<String, String> e : req.headers().entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        int port = 4567;
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }
        File dataDir = new File("data");
        AggregationServer server = new AggregationServer(port, dataDir);
        server.start();
    }

    private static boolean wantsClose(HttpRequest req) {
        String conn = headerCaseInsensitive(req, "Connection");
        if (conn != null && conn.equalsIgnoreCase("close")) return true;
        if (req.httpVersion != null && req.httpVersion.equalsIgnoreCase("HTTP/1.0")) {
            return conn == null || !conn.equalsIgnoreCase("keep-alive");
        }
        return false;
    }
}

final class ConnectionContext {
    volatile boolean seenSuccessfulPut = false;
}

final class SequencedEvent implements Comparable<SequencedEvent> {
    final long senderLamport;
    final String senderNodeId;
    final long serverSeq;
    final Runnable task;
    SequencedEvent(long senderLamport, String senderNodeId, long serverSeq, Runnable task) {
        this.senderLamport = senderLamport;
        this.senderNodeId = senderNodeId;
        this.serverSeq = serverSeq;
        this.task = task;
    }
    void process() { task.run(); }
    @Override
    public int compareTo(SequencedEvent o) {
        int c = Long.compare(this.senderLamport, o.senderLamport);
        if (c != 0) return c;
        c = this.senderNodeId.compareTo(o.senderNodeId);
        if (c != 0) return c;
        return Long.compare(this.serverSeq, o.serverSeq);
    }
}


