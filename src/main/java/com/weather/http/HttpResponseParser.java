package com.weather.http;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpResponseParser {
    public static HttpResponseMessage readResponse(InputStream in) throws IOException {
        BufferedInputStream bin = new BufferedInputStream(in);
        String statusLine = readLine(bin);
        if (statusLine == null || statusLine.isEmpty()) throw new IOException("Empty response");
        String[] parts = statusLine.split(" ", 3);
        if (parts.length < 3) throw new IOException("Malformed status line: " + statusLine);
        int code = Integer.parseInt(parts[1]);
        String reason = parts[2];
        Map<String, String> headers = new LinkedHashMap<>();
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
        try { contentLength = Integer.parseInt(cl); } catch (Exception ignored) {}
        byte[] body = new byte[contentLength];
        int read = 0;
        while (read < contentLength) {
            int r = bin.read(body, read, contentLength - read);
            if (r < 0) break;
            read += r;
        }
        return new HttpResponseMessage(code, reason, headers, body);
    }

    private static String readLine(BufferedInputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int prev = -1;
        while (true) {
            int b = in.read();
            if (b == -1) break;
            if (b == '\n' && prev == '\r') { sb.setLength(sb.length() - 1); break; }
            sb.append((char)b);
            prev = b;
            if (sb.length() > 8192) throw new IOException("Header too long");
        }
        String line = sb.toString();
        if (line.endsWith("\r")) return line.substring(0, line.length()-1);
        return line;
    }
}


