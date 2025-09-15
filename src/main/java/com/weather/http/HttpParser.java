package com.weather.http;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class HttpParser {
    public static HttpRequest readRequest(InputStream in) throws IOException {
        BufferedInputStream bin = new BufferedInputStream(in);
        String requestLine = readLine(bin);
        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }
        String[] parts = requestLine.split(" ", 3);
        if (parts.length < 3) {
            throw new IOException("Malformed request line: " + requestLine);
        }
        String method = parts[0].trim();
        String target = parts[1].trim();
        String httpVersion = parts[2].trim();
        String path = target;
        String query = "";
        int idx = target.indexOf('?');
        if (idx >= 0) {
            path = target.substring(0, idx);
            query = idx + 1 < target.length() ? target.substring(idx + 1) : "";
        }

        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = readLine(bin)) != null) {
            if (line.isEmpty()) break;
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                headers.put(name, value);
            }
        }
        int contentLength = 0;
        String cl = headers.getOrDefault("Content-Length", headers.getOrDefault("content-length", "0"));
        try {
            contentLength = Integer.parseInt(cl);
        } catch (NumberFormatException ignored) {}
        byte[] body = new byte[contentLength];
        int read = 0;
        while (read < contentLength) {
            int r = bin.read(body, read, contentLength - read);
            if (r < 0) break;
            read += r;
        }
        return new HttpRequest(method, path, query, httpVersion, headers, body);
    }

    private static String readLine(BufferedInputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int prev = -1;
        while (true) {
            int b = in.read();
            if (b == -1) {
                break;
            }
            if (b == '\n' && prev == '\r') {
                sb.setLength(sb.length() - 1); // remove CR
                break;
            }
            sb.append((char) b);
            prev = b;
            if (sb.length() > 8192) {
                throw new IOException("Header line too long");
            }
        }
        String line = sb.toString();
        if (line.equals("")) {
            return "";
        }
        // Remove a final CR if there's no LF
        if (line.endsWith("\r")) {
            return line.substring(0, line.length() - 1);
        }
        return line;
    }

    public static byte[] buildSimpleRequest(String method, String pathWithQuery, Map<String, String> headers, byte[] body) {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(' ').append(pathWithQuery).append(" HTTP/1.1\r\n");
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
        }
        sb.append("\r\n");
        byte[] head = sb.toString().getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[head.length + (body == null ? 0 : body.length)];
        System.arraycopy(head, 0, result, 0, head.length);
        if (body != null) {
            System.arraycopy(body, 0, result, head.length, body.length);
        }
        return result;
    }
}


