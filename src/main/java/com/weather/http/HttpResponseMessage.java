package com.weather.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpResponseMessage {
    public final int statusCode;
    public final String reason;
    public final Map<String, String> headers;
    public final byte[] body;

    public HttpResponseMessage(int statusCode, String reason, Map<String, String> headers, byte[] body) {
        this.statusCode = statusCode;
        this.reason = reason;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.body = body == null ? new byte[0] : body;
    }
}


