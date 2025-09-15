package com.weather.client;

import com.weather.http.HttpParser;
import com.weather.util.LamportClock;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class GETClient {
    private static final LamportClock clock = new LamportClock();
    private static String nodeId = java.util.UUID.randomUUID().toString();

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: GETClient <server[:port]|url> [stationId]");
            System.exit(2);
        }
        String target = args[0];
        String station = args.length >= 2 ? args[1] : null;
        URI uri = normalize(target, station);

        InetSocketAddress addr = new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 4567 : uri.getPort());
        try (Socket socket = new Socket(uri.getHost(), addr.getPort())) {
            socket.setSoTimeout(10000);
            Map<String, String> headers = new HashMap<>();
            headers.put("Host", uri.getHost());
            headers.put("Connection", "close");
            headers.put("Accept", "application/json");
            headers.put("User-Agent", "GETClient/1.0");
            headers.put(LamportClock.HEADER_LAMPORT, Long.toString(clock.tick()));
            headers.put(LamportClock.HEADER_NODE_ID, nodeId);
            headers.put(LamportClock.HEADER_ROLE, "client");
            byte[] req = HttpParser.buildSimpleRequest("GET", uri.getRawPath() + (uri.getRawQuery() == null ? "" : ("?" + uri.getRawQuery())), headers, null);
            OutputStream out = socket.getOutputStream();
            out.write(req);
            out.flush();
            com.weather.http.HttpResponseMessage response = com.weather.http.HttpResponseParser.readResponse(socket.getInputStream());
            clock.receive(parseLamport(response.headers.get(LamportClock.HEADER_LAMPORT)));
            String json = new String(response.body, StandardCharsets.UTF_8);
            prettyPrintJson(json);
        }
    }

    private static URI normalize(String input, String station) throws Exception {
        String u = input;
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://" + u;
        }
        URI uri = new URI(u);
        String path = "/weather.json";
        String query = null;
        if (station != null) query = "id=" + java.net.URLEncoder.encode(station, "UTF-8");
        return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort() == -1 ? 4567 : uri.getPort(), path, query, null);
    }

    private static long parseLamport(String s) {
        if (s == null) return 0L;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0L; }
    }

    private static void prettyPrintJson(String json) {
        // Minimal pretty printer to "key: value" lines from {"data":[{...},...]}
        try {
            com.weather.json.JsonValue v = com.weather.json.JsonParser.parse(json);
            if (!(v instanceof com.weather.json.JsonObject)) { System.out.println(json); return; }
            com.weather.json.JsonObject obj = (com.weather.json.JsonObject) v;
            com.weather.json.JsonValue data = obj.entries().get("data");
            if (data instanceof com.weather.json.JsonArray) {
                for (com.weather.json.JsonValue item : ((com.weather.json.JsonArray) data).values()) {
                    if (item instanceof com.weather.json.JsonObject) {
                        for (java.util.Map.Entry<String, com.weather.json.JsonValue> e : ((com.weather.json.JsonObject) item).entries().entrySet()) {
                            String k = e.getKey();
                            String val = e.getValue().toJson();
                            if (val.startsWith("\"") && val.endsWith("\"")) {
                                val = val.substring(1, val.length()-1);
                            }
                            System.out.println(k + ": " + val);
                        }
                        System.out.println();
                    }
                }
            } else {
                System.out.println(json);
            }
        } catch (IllegalArgumentException ex) {
            System.out.println(json);
        }
    }
}


