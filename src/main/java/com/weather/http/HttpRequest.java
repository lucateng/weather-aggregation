package com.weather.http;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class HttpRequest {
    public final String method;
    public final String path;
    public final String query;
    public final String httpVersion;
    private final Map<String, String> headers;
    public final byte[] body;

    public HttpRequest(String method, String path, String query, String httpVersion, Map<String, String> headers, byte[] body) {
        this.method = method;
        this.path = path;
        this.query = query == null ? "" : query;
        this.httpVersion = httpVersion;
        this.headers = new HashMap<>();
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                this.headers.put(e.getKey().toLowerCase(), e.getValue());
            }
        }
        this.body = body == null ? new byte[0] : body;
    }

    public Map<String, String> headers() {
        return Collections.unmodifiableMap(headers);
    }

    public String header(String name) {
        if (name == null) return null;
        return headers.get(name.toLowerCase());
    }
}


