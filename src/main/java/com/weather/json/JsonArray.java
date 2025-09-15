package com.weather.json;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public final class JsonArray implements JsonValue {
    private final List<JsonValue> values = new ArrayList<>();

    public JsonArray add(JsonValue value) {
        values.add(value == null ? JsonNull.INSTANCE : value);
        return this;
    }

    public List<JsonValue> values() {
        return java.util.Collections.unmodifiableList(values);
    }

    @Override
    public String toJson() {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (JsonValue v : values) {
            joiner.add(v == null ? JsonNull.INSTANCE.toJson() : v.toJson());
        }
        return joiner.toString();
    }
}


