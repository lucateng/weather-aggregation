package com.weather.http;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpResponse {
    public final HttpStatus status;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private byte[] body = new byte[0];

    public HttpResponse(HttpStatus status) {
        this.status = status;
    }

    public HttpResponse header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public HttpResponse body(byte[] body) {
        this.body = body == null ? new byte[0] : body;
        return this;
    }

    public HttpResponse bodyText(String text, String contentType) {
        byte[] data = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        this.body = data;
        headers.put("Content-Type", contentType + "; charset=utf-8");
        headers.put("Content-Length", Integer.toString(data.length));
        return this;
    }

    public byte[] serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(status.code).append(' ').append(status.reason).append("\r\n");
        if (body != null && !headers.containsKey("Content-Length")) {
            headers.put("Content-Length", Integer.toString(body.length));
        }
        for (Map.Entry<String, String> e : headers.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
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


