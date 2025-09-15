package com.weather.json;

public final class JsonBoolean implements JsonValue {
    public static final JsonBoolean TRUE = new JsonBoolean(true);
    public static final JsonBoolean FALSE = new JsonBoolean(false);

    public final boolean value;

    private JsonBoolean(boolean value) { this.value = value; }

    public static JsonBoolean of(boolean v) { return v ? TRUE : FALSE; }

    @Override
    public String toJson() {
        return value ? "true" : "false";
    }
}


