package com.weather;

import com.weather.model.WeatherRecord;
import com.weather.store.AggregationStore;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ExpiryTest {
    @Test
    void expiresAfterThreshold() throws Exception {
        System.out.println("[SCENARIO] Expiry: Records from sources inactive > TTL are pruned");
        File dir = new File("expiry-test");
        if (dir.exists()) {
            for (File f : dir.listFiles()) { f.delete(); }
            dir.delete();
        }
        AggregationStore store = new AggregationStore(dir, 20, 200L);
        Map<String,String> fields = new HashMap<>();
        fields.put("id","E1");
        WeatherRecord r = new WeatherRecord(fields, "source-1", 1L, System.currentTimeMillis());
        store.addRecord(r);
        store.updateLastSeen("source-1");
        assertTrue(store.snapshotJson(null).values().size() >= 1);
        Thread.sleep(300);
        store.expireOldSources();
        assertEquals(0, store.snapshotJson(null).values().size());
        com.weather.TestLog.log(
            "Expiry",
            "add record → wait > TTL → expire",
            "snapshot empty",
            store.snapshotJson(null).values().isEmpty() ? "EMPTY" : "NOT EMPTY",
            store.snapshotJson(null).values().isEmpty()
        );
    }
}
