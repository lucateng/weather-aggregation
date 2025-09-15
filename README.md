## Distributed Systems Assignment 2 - Weather Aggregation System

### Purpose
Build a small distributed system that aggregates and serves weather data via a RESTful API, accepting PUTs from multiple content servers and answering GETs from clients. The aggregation server enforces consistent views under concurrency using Lamport clocks, provides crash-safe persistence and 30-second source expiry, and uses custom HTTP and JSON over raw sockets.

### Project structure
- `com.weather.server.AggregationServer`: main server (default port 4567; configurable via CLI)
- `com.weather.client.ContentServer`: reads a plain-text file, converts to JSON, PUTs to aggregation server
- `com.weather.client.GETClient`: fetches via GET and pretty-prints key: value lines (optional station id filter)
- `com.weather.json.*`: custom JSON values and parser
- `com.weather.http.*`: minimal HTTP request/response models and parsers
- `com.weather.store.AggregationStore`: thread-safe store, persistence, expiry
- `com.weather.util.LamportClock`: thread-safe Lamport clock and header names

### Build
```bash
mvn clean package
```
Artifact: `target/weather-aggregation-1.0-SNAPSHOT.jar`

### Run
#### 1) Start the Aggregation Server
Default port 4567 (pass a port as the first argument to override):
```bash
java -cp target/weather-aggregation-1.0-SNAPSHOT.jar com.weather.server.AggregationServer
# or custom port
java -cp target/weather-aggregation-1.0-SNAPSHOT.jar com.weather.server.AggregationServer 4567
```
Data is persisted under `data/store.json` using an atomic temp file `data/store.json.tmp`.

#### 2) Send updates with a Content Server (PUT)
Usage:
```bash
java -cp target/weather-aggregation-1.0-SNAPSHOT.jar com.weather.client.ContentServer <server[:port]|url> <inputFile>
```
Example:
```bash
java -cp target/weather-aggregation-1.0-SNAPSHOT.jar com.weather.client.ContentServer http://localhost:4567 data/sample.txt
```
Input file format:
```
id:IDS60901
name:Adelaide (West Terrace /  ngayirdapira)
state:SA
time_zone:CST
lat:-34.9
lon:138.6
local_date_time:15/04:00pm
local_date_time_full:20230715160000
air_temp:13.3
apparent_t:9.5
cloud:Partly cloudy
dewpt:5.7
press:1023.9
rel_hum:60
wind_dir:S
wind_spd_kmh:15
wind_spd_kt:8
```
Notes:
- The `id` field is required.
- Numeric fields are sent as numbers; other fields are sent as strings.

#### 3) Fetch data with a GET client
Usage:
```bash
java -cp target/weather-aggregation-1.0-SNAPSHOT.jar com.weather.client.GETClient <server[:port]|url> [stationId]
```
Examples:
```bash
java -cp target/weather-aggregation-1.0-SNAPSHOT.jar com.weather.client.GETClient http://localhost:4567
java -cp target/weather-aggregation-1.0-SNAPSHOT.jar com.weather.client.GETClient http://localhost:4567 IDS60901
```
Response is a JSON document of the form (the client pretty-prints it as key: value lines):
```json
{"data": [{ ... weather record ... }, ...]}
```

### API overview
- Endpoint: `/weather.json`
- Methods:
  - PUT: body must be a JSON object with the weather record fields. On the first successful PUT on a given TCP connection: 201 Created; subsequent successful PUTs on the same connection: 200 OK. Empty body: 204 No Content (keeps source “last-seen” fresh). Malformed JSON: 500 Internal Server Error. Unsupported method: 400 Bad Request.
  - GET: returns `200 OK` with JSON body `{ "data": [...] }`. Optional query `?id=<stationId>` filters by station id.
- Headers for Lamport clocks (all components include these):
  - `X-Lamport`: local Lamport time
  - `X-Node-Id`: unique node id
  - `X-Role`: `server` | `content` | `client`

### Consistency, ordering, and expiry
- Lamport clocks: each component maintains a local Lamport clock; clocks are sent in headers and merged on receipt.
- Update serialization: server enqueues every request (PUT and GET) and processes them in a single sequence ordered by sender Lamport timestamp (ascending), then `X-Node-Id` (lexicographic), then a server-side arrival sequence. This preserves Lamport order and ensures GETs do not overtake prior PUTs.
- GET returns a snapshot of currently applied updates filtered by active sources.
- Expiry: data from a content server is considered active for 30 seconds since last contact; expired sources are pruned regularly.
- Capacity: only the most recent 20 updates are kept.

### Persistence and crash recovery
- The store is written to `data/store.json` using atomic write-rename via a temporary file `data/store.json.tmp`. The temp file exists only during write; on success it is atomically moved over `store.json`. You may not see it unless a crash occurs mid-write, in which case the server promotes the temp file on restart.
- On restart, the server attempts to recover using the complete file; if a crash occurred during a write, the temp file is promoted.

### Tests
Run all tests:
```bash
mvn test
```
What you will see (example):
```
[SCENARIO] Golden Path: ContentServer PUT then GETClient retrieves same record
[STEP] Server started on port 54629
[STEP] Sending PUT for id=TEST1
[RESULT] PUT response: 201
[STEP] Polling GET until record appears (max 2s)
[INFO] GET attempt 1: {"data":[{"id":"TEST1","name":"X","lat":1.0,"lon":2.0}]}
[TEST] Golden Path | Steps: start server → PUT id=TEST1 → poll GET | Expected: GET body contains TEST1 | Actual: FOUND | RESULT: PASS
```
Notable tests:
- `JsonParserTest`: custom JSON parser correctness and roundtrips
- `LamportClockTest`: Lamport tick/receive behavior
- `SequencedEventTest`: orders events by (senderLamport, nodeId, serverSeq)
- `IntegrationTest`: Golden Path (PUT→GET)
- `ConcurrencyTest`: Many parallel PUTs then GET shows all
- `ExpiryTest`: expiry behavior with a short TTL
- `RecoveryTest`: promotes `store.json.tmp` on startup
- `FailureModeTest`: graceful behavior when server unavailable

Run a single test class:
```bash
mvn -Dtest=IntegrationTest test
```

