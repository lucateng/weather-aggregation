package com.weather.http;

public enum HttpStatus {
    OK(200, "OK"),
    CREATED(201, "Created"),
    NO_CONTENT(204, "No Content"),
    BAD_REQUEST(400, "Bad Request"),
    INTERNAL_SERVER_ERROR(500, "Internal Server Error");

    public final int code;
    public final String reason;

    HttpStatus(int code, String reason) {
        this.code = code;
        this.reason = reason;
    }
}


