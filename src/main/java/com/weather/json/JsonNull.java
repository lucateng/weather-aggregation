package com.weather.json;

public final class JsonNull implements JsonValue {
    public static final JsonNull INSTANCE = new JsonNull();
    private JsonNull() {}
    @Override
    public String toJson() { return "null"; }
}


