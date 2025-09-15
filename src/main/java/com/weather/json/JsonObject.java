package com.weather.json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

public final class JsonObject implements JsonValue {
    private final LinkedHashMap<String, JsonValue> map = new LinkedHashMap<>();

    public JsonObject put(String key, JsonValue value) {
        map.put(Objects.requireNonNull(key), value == null ? JsonNull.INSTANCE : value);
        return this;
    }

    public JsonValue get(String key) {
        return map.get(key);
    }

    public Map<String, JsonValue> entries() {
        return java.util.Collections.unmodifiableMap(map);
    }

    @Override
    public String toJson() {
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        for (Map.Entry<String, JsonValue> e : map.entrySet()) {
            joiner.add(new JsonString(e.getKey()).toJson() + ":" + (e.getValue() == null ? JsonNull.INSTANCE.toJson() : e.getValue().toJson()));
        }
        return joiner.toString();
    }
}


