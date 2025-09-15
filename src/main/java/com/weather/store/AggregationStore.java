package com.weather.store;

import com.weather.json.*;
import com.weather.model.WeatherRecord;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe store maintains latest up to 20 weather records across content servers with expiry.
 * Provides atomic snapshot and crash-safe persistence via write-rename strategy.
 */
public final class AggregationStore {
    private final int capacity;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Deque<WeatherRecord> ordered = new ArrayDeque<>(); // by lamport then arrival
    private final Map<String, Long> lastSeenBySource = new ConcurrentHashMap<>();
    private final File dataFile;
    private final File tempFile;
    private final long expiryMs;

    public AggregationStore(File dataDir, int capacity) throws IOException {
        this(dataDir, capacity, 30_000L);
    }

    public AggregationStore(File dataDir, int capacity, long expiryMs) throws IOException {
        this.capacity = capacity;
        this.expiryMs = expiryMs;
        if (!dataDir.exists()) {
            if (!dataDir.mkdirs()) {
                throw new IOException("Failed to create data dir: " + dataDir);
            }
        }
        this.dataFile = new File(dataDir, "store.json");
        this.tempFile = new File(dataDir, "store.json.tmp");
        if (dataFile.exists()) {
            try {
                loadFromDisk();
            } catch (Exception e) {
                // Try recover from temp
                if (tempFile.exists()) {
                    Files.move(tempFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    loadFromDisk();
                } else {
                    ordered.clear();
                }
            }
        } else if (tempFile.exists()) {
            // Promote temp if crash happened before final rename
            Files.move(tempFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            try { loadFromDisk(); } catch (Exception ignored) {}
        }
    }

    public void updateLastSeen(String sourceNodeId) { lastSeenBySource.put(sourceNodeId, System.currentTimeMillis()); }

    public boolean isActiveSource(String sourceNodeId) {
        Long last = lastSeenBySource.get(sourceNodeId);
        return last != null && System.currentTimeMillis() - last <= expiryMs;
    }

    public boolean markSeenAndIsFirst(String sourceNodeId) {
        long now = System.currentTimeMillis();
        Long last = lastSeenBySource.get(sourceNodeId);
        boolean first = (last == null) || (now - last > expiryMs);
        lastSeenBySource.put(sourceNodeId, now);
        return first;
    }

    public void addRecord(WeatherRecord record) throws IOException {
        lock.writeLock().lock();
        try {
            // Insert maintaining order by lamportTimestamp then arrival
            if (ordered.isEmpty()) {
                ordered.add(record);
            } else {
                List<WeatherRecord> list = new ArrayList<>(ordered);
                int pos = list.size();
                for (int i = 0; i < list.size(); i++) {
                    WeatherRecord r = list.get(i);
                    if (record.lamportTimestamp < r.lamportTimestamp ||
                            (record.lamportTimestamp == r.lamportTimestamp && record.arrivalEpochMs < r.arrivalEpochMs)) {
                        pos = i;
                        break;
                    }
                }
                list.add(pos, record);
                ordered.clear();
                ordered.addAll(list);
            }
            while (ordered.size() > capacity) {
                ordered.removeLast();
            }
            updateLastSeen(record.sourceNodeId);
            persistUnlocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public JsonArray snapshotJson(String filterStationIdOrNull) {
        lock.readLock().lock();
        try {
            long now = System.currentTimeMillis();
            JsonArray arr = new JsonArray();
            for (WeatherRecord r : ordered) {
                Long last = lastSeenBySource.get(r.sourceNodeId);
                if (last == null || now - last > expiryMs) continue; // expired
                if (filterStationIdOrNull != null) {
                    String id = r.fields.get("id");
                    if (id == null || !id.equals(filterStationIdOrNull)) continue;
                }
                arr.add(r.toJsonObject());
            }
            return arr;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void expireOldSources() throws IOException {
        lock.writeLock().lock();
        try {
            long now = System.currentTimeMillis();
            // Remove records from expired sources
            List<WeatherRecord> keep = new ArrayList<>();
            for (WeatherRecord r : ordered) {
                Long last = lastSeenBySource.get(r.sourceNodeId);
                if (last != null && now - last <= expiryMs) keep.add(r);
            }
            ordered.clear();
            ordered.addAll(keep);
            // prune lastSeen entries
            for (String k : new ArrayList<>(lastSeenBySource.keySet())) {
                Long last = lastSeenBySource.get(k);
                if (last == null || now - last > expiryMs) lastSeenBySource.remove(k);
            }
            persistUnlocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void persistUnlocked() throws IOException {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (WeatherRecord r : ordered) {
            arr.add(r.toJsonObject());
        }
        root.put("records", arr);
        root.put("generated_at", new JsonString(Instant.now().toString()));
        byte[] bytes = root.toJson().getBytes(StandardCharsets.UTF_8);
        Files.write(tempFile.toPath(), bytes);
        Files.move(tempFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void loadFromDisk() throws IOException {
        String data = Files.readString(dataFile.toPath(), StandardCharsets.UTF_8);
        JsonValue v = JsonParser.parse(data);
        if (!(v instanceof JsonObject)) return;
        JsonObject obj = (JsonObject) v;
        JsonValue recs = obj.entries().get("records");
        if (!(recs instanceof JsonArray)) return;
        JsonArray arr = (JsonArray) recs;
        ordered.clear();
        for (JsonValue item : arr.values()) {
            if (!(item instanceof JsonObject)) continue;
            JsonObject jo = (JsonObject) item;
            Map<String, String> fields = new HashMap<>();
            for (Map.Entry<String, JsonValue> e : jo.entries().entrySet()) {
                JsonValue val = e.getValue();
                String text;
                if (val instanceof JsonString) text = ((JsonString) val).value;
                else if (val instanceof JsonNumber) text = ((JsonNumber) val).value;
                else if (val instanceof JsonBoolean) text = ((JsonBoolean) val).toJson();
                else text = val.toJson();
                fields.put(e.getKey(), text);
            }
            WeatherRecord r = new WeatherRecord(fields, fields.getOrDefault("sourceNodeId", "unknown"), 0L, 0L);
            ordered.add(r);
        }
    }
}


