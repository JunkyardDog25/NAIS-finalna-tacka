# PUBLISH_SONG saga — demo / verification

Two scenarios: the happy path (saga reaches `COMPLETED`, data lands in both stores) and the
forced-failure path (graph step throws, orchestrator compensates, saga ends `FAILED`).

## Prerequisites

```bash
docker compose up -d mongo neo4j rabbitmq      # wait until all are healthy
./mvnw spring-boot:run
```

Seed the artist that the graph step looks up by `artistId` (so the `:Artist` node gets a name):

```bash
docker exec -i musicapp-mongo mongosh musicapp --quiet --eval \
  'db.artists.updateOne({_id:"artist-1"},{$set:{name:"The Testers",country:"RS"}},{upsert:true})'
```

---

## Scenario 1 — happy path (COMPLETED)

```bash
# 1) Publish a song -> returns {"sagaId":"..."}
SAGA=$(curl -s -X POST http://localhost:8080/api/songs \
  -H 'Content-Type: application/json' \
  -d '{"title":"Test Song","artist":{"artistId":"artist-1","artistName":"The Testers"},"albumId":"album-1","genre":"rock","durationSeconds":200}' \
  | sed -E 's/.*"sagaId":"([^"]+)".*/\1/')
echo "sagaId=$SAGA"

# 2) Poll the saga until COMPLETED
curl -s http://localhost:8080/api/sagas/$SAGA      # repeat; status: STARTED -> MONGO_DONE -> COMPLETED
```

Assert in **Mongo** (the song document exists, id == the shared id in the saga payload):

```bash
docker exec -i musicapp-mongo mongosh musicapp --quiet --eval \
  'db.songs.find({title:"Test Song"}).pretty()'
```

Assert in **Neo4j** (the `BY` + `IN_GENRE` pattern exists) — Browser at http://localhost:7474 or:

```bash
docker exec -i musicapp-neo4j cypher-shell -u neo4j -p database \
  "MATCH (s:Song)-[:BY]->(a:Artist), (s)-[:IN_GENRE]->(g:Genre) \
   WHERE s.title='Test Song' RETURN s.songId, a.name, g.name;"
```

Expect one row; `s.songId` equals the song `id` in Mongo (shared id rule).

---

## Scenario 2 — forced graph failure (compensation -> FAILED)

Restart the app with the demo toggle on so `SongGraphService.createSong` throws:

```bash
# stop the running app, then:
SAGA_GRAPH_FAIL_CREATE=true ./mvnw spring-boot:run
# (equivalently: ./mvnw spring-boot:run -Dspring-boot.run.arguments=--saga.graph.fail-create=true)
```

```bash
SAGA=$(curl -s -X POST http://localhost:8080/api/songs \
  -H 'Content-Type: application/json' \
  -d '{"title":"Rollback Song","artist":{"artistId":"artist-1","artistName":"The Testers"},"genre":"rock","durationSeconds":150}' \
  | sed -E 's/.*"sagaId":"([^"]+)".*/\1/')

curl -s http://localhost:8080/api/sagas/$SAGA   # STARTED -> MONGO_DONE -> COMPENSATING -> FAILED
```

What to observe:
- App log shows `GraphCreateSong failed: Forced graph failure...`, then the orchestrator sends the
  compensating `MongoDeleteSong`.
- Saga ends at **FAILED**.
- The Mongo document was rolled back (compensation deleted it):

```bash
docker exec -i musicapp-mongo mongosh musicapp --quiet --eval \
  'db.songs.countDocuments({title:"Rollback Song"})'   # -> 0
```

- No `:Song` node for it in Neo4j (the graph step never committed).

> The `MongoDeleteSong` compensation is implemented in `SongMongoService.deleteSong`: it
> deletes the song document and `$pull`s the id from every playlist (idempotent), so the
> Mongo write is genuinely rolled back when the graph step fails.

---

## PowerShell equivalents

```powershell
$r = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/songs `
  -ContentType application/json `
  -Body '{"title":"Test Song","artist":{"artistId":"artist-1","artistName":"The Testers"},"genre":"rock","durationSeconds":200}'
Invoke-RestMethod -Uri "http://localhost:8080/api/sagas/$($r.sagaId)"
```
