package com.weather.model;

import com.weather.json.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured weather record.
 */
public final class WeatherRecord {
    public final Map<String, String> fields = new LinkedHashMap<>();
    public final String sourceNodeId; // content server id
    public final long lamportTimestamp;
    public final long arrivalEpochMs;

    public WeatherRecord(Map<String, String> fields, String sourceNodeId, long lamportTimestamp, long arrivalEpochMs) {
        this.fields.putAll(fields);
        this.sourceNodeId = sourceNodeId;
        this.lamportTimestamp = lamportTimestamp;
        this.arrivalEpochMs = arrivalEpochMs;
    }

    public JsonObject toJsonObject() {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            String key = e.getKey();
            String val = e.getValue();
            if (isNumericField(key)) {
                obj.put(key, new JsonNumber(val));
            } else {
                obj.put(key, new JsonString(val));
            }
        }
        return obj;
    }

    private static boolean isNumericField(String key) {
        switch (key) {
            case "lat":
            case "lon":
            case "air_temp":
            case "apparent_t":
            case "dewpt":
            case "press":
            case "rel_hum":
            case "wind_spd_kmh":
            case "wind_spd_kt":
                return true;
            default:
                return false;
        }
    }
}


