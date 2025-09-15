package com.weather.json;

public final class JsonNumber implements JsonValue {
    public final String value; // keep as string to preserve formatting

    public JsonNumber(String value) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("number empty");
        this.value = value;
    }

    @Override
    public String toJson() {
        return value;
    }
}


