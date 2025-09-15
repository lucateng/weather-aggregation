package com.weather.client;

import com.weather.http.HttpParser;
import com.weather.http.HttpResponseMessage;
import com.weather.http.HttpResponseParser;
import com.weather.util.LamportClock;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ContentServer {
    private static final LamportClock clock = new LamportClock();
    private static final String nodeId = java.util.UUID.randomUUID().toString();

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: ContentServer <server[:port]|url> <inputFile>");
            System.exit(2);
        }
        String target = args[0];
        String file = args[1];
        String json = convertFileToJson(Path.of(file));
        URI uri = normalize(target);
        InetSocketAddress addr = new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 4567 : uri.getPort());

        try (Socket socket = new Socket(addr.getHostName(), addr.getPort())) {
            socket.setSoTimeout(10000);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Host", uri.getHost());
            headers.put("Connection", "keep-alive");
            headers.put("Content-Type", "application/json");
            headers.put("Content-Length", Integer.toString(json.getBytes(StandardCharsets.UTF_8).length));
            headers.put("User-Agent", "ATOMClient/1/0");
            headers.put("Accept", "application/json");
            headers.put(LamportClock.HEADER_LAMPORT, Long.toString(clock.tick()));
            headers.put(LamportClock.HEADER_NODE_ID, nodeId);
            headers.put(LamportClock.HEADER_ROLE, "content");
            byte[] req = HttpParser.buildSimpleRequest("PUT", "/weather.json", headers, json.getBytes(StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();
            out.write(req);
            out.flush();

            HttpResponseMessage resp = HttpResponseParser.readResponse(socket.getInputStream());
            clock.receive(parseLamport(resp.headers.get(LamportClock.HEADER_LAMPORT)));
            if (resp.statusCode != 200 && resp.statusCode != 201) {
                throw new IOException("PUT failed: " + resp.statusCode + " " + resp.reason);
            }

            // Keep-alive ping to refresh last-seen (empty PUT => 204)
            Map<String, String> ping = new LinkedHashMap<>();
            ping.put("Host", uri.getHost());
            ping.put("Connection", "close");
            ping.put("Content-Length", "0");
            ping.put("User-Agent", "ATOMClient/1/0");
            ping.put(LamportClock.HEADER_LAMPORT, Long.toString(clock.tick()));
            ping.put(LamportClock.HEADER_NODE_ID, nodeId);
            ping.put(LamportClock.HEADER_ROLE, "content");
            byte[] req2 = HttpParser.buildSimpleRequest("PUT", "/weather.json", ping, new byte[0]);
            out.write(req2); out.flush();
            HttpResponseMessage resp2 = HttpResponseParser.readResponse(socket.getInputStream());
            if (resp2.statusCode != 204) throw new IOException("Ping failed: " + resp2.statusCode);

            // Verify via GET on a fresh connection (previous request indicated close)
        }

        try (Socket s2 = new Socket(addr.getHostName(), addr.getPort())) {
            s2.setSoTimeout(10000);
            Map<String,String> getH = new LinkedHashMap<>();
            getH.put("Host", uri.getHost());
            getH.put("Connection", "close");
            getH.put("Accept", "application/json");
            getH.put(LamportClock.HEADER_LAMPORT, Long.toString(clock.tick()));
            getH.put(LamportClock.HEADER_NODE_ID, nodeId);
            getH.put(LamportClock.HEADER_ROLE, "content");
            byte[] getReq = HttpParser.buildSimpleRequest("GET", "/weather.json", getH, null);
            OutputStream out2 = s2.getOutputStream();
            out2.write(getReq); out2.flush();
            HttpResponseMessage getResp = HttpResponseParser.readResponse(s2.getInputStream());
            if (getResp.statusCode != 200) throw new IOException("Verify GET failed: " + getResp.statusCode);
            String body = new String(getResp.body, StandardCharsets.UTF_8);
            if (!body.contains("\"id\":")) throw new IOException("Verify GET missing id");
            System.out.println("PUT verified via GET");
        }
    }

    private static long parseLamport(String s) {
        if (s == null) return 0L;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0L; }
    }

    private static String convertFileToJson(Path path) throws IOException {
        Map<String, String> map = new HashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) continue;
            int i = line.indexOf(':');
            if (i <= 0) continue;
            String key = line.substring(0, i).trim();
            String value = line.substring(i + 1).trim();
            map.put(key, value);
        }
        if (!map.containsKey("id") || map.get("id").isEmpty()) throw new IOException("Input missing id field");
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        int count = 0;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (count++ > 0) sb.append(',');
            sb.append('"').append(escape(e.getKey())).append('"').append(':');
            if (isNumeric(e.getKey())) sb.append(e.getValue()); else sb.append('"').append(escape(e.getValue())).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static boolean isNumeric(String key) {
        switch (key) {
            case "lat": case "lon": case "air_temp": case "apparent_t": case "dewpt": case "press": case "rel_hum": case "wind_spd_kmh": case "wind_spd_kt":
                return true;
            default: return false;
        }
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private static URI normalize(String input) throws Exception {
        String u = input;
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
        URI uri = new URI(u);
        return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort() == -1 ? 4567 : uri.getPort(), "/weather.json", null, null);
    }
}


