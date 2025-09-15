package com.weather;

import com.weather.json.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JsonParserTest {
    @Test
    void parseSimpleObject() {
        JsonValue v = JsonParser.parse("{\"a\":1,\"b\":\"x\",\"c\":[true,null]}");
        assertTrue(v instanceof JsonObject);
        JsonObject o = (JsonObject)v;
        assertTrue(o.entries().get("a") instanceof JsonNumber);
        assertEquals("x", ((JsonString)o.entries().get("b")).value);
        assertTrue(o.entries().get("c") instanceof JsonArray);
    }

    @Test
    void serializeRoundtrip() {
        JsonObject obj = new JsonObject().put("id", new JsonString("X"));
        String s = obj.toJson();
        JsonObject obj2 = (JsonObject) JsonParser.parse(s);
        assertEquals("X", ((JsonString)obj2.get("id")).value);
    }
}


