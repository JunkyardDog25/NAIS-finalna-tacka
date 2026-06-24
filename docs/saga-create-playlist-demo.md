# CREATE_PLAYLIST saga — demo / verification

The playlist is **authoritative in MongoDB** (id, ownerId, name, songIds). Neo4j stores ONLY the
ownership relationship `(:Playlist)-[:OWNED_BY]->(:User)` — there are **no** song-containment
(`CONTAINS`) edges.

Two scenarios: the happy path (saga reaches `COMPLETED`, the doc lands in Mongo and ownership in
Neo4j) and the forced-failure path (graph step throws, orchestrator compensates by deleting the
Mongo doc, saga ends `FAILED`).

Flow: `STARTED -> MONGO_DONE -> COMPLETED` (compensation on graph failure deletes the Mongo doc).

## Prerequisites

```bash
docker compose up -d mongo neo4j rabbitmq      # wait until all are healthy
./mvnw spring-boot:run
```

The Mongo step validates that the owner exists as a `User` **and** every `songId` exists as a
`Song`. Seed an owner and a couple of songs first (otherwise the Mongo step replies success=false
and the saga ends `FAILED` with the graph untouched):

```bash
docker exec -i musicapp-mongo mongosh musicapp --quiet --eval \
  'db.users.updateOne({_id:"user-1"},{$set:{username:"Owner One"}},{upsert:true});
   db.songs.updateOne({_id:"song-1"},{$set:{title:"First"}},{upsert:true});
   db.songs.updateOne({_id:"song-2"},{$set:{title:"Second"}},{upsert:true});'
```

---

## Scenario 1 — happy path (COMPLETED)

```bash
# 1) Create a playlist -> returns {"sagaId":"..."}
SAGA=$(curl -s -X POST http://localhost:8080/api/playlists \
  -H 'Content-Type: application/json' \
  -d '{"ownerId":"user-1","name":"My Mix","songIds":["song-1","song-2"]}' \
  | sed -E 's/.*"sagaId":"([^"]+)".*/\1/')
echo "sagaId=$SAGA"

# 2) Poll the saga until COMPLETED
curl -s http://localhost:8080/api/sagas/$SAGA      # repeat; STARTED -> MONGO_DONE -> COMPLETED
```

Assert in **Mongo** (the playlist document exists *with* its songIds — the authoritative list):

```bash
docker exec -i musicapp-mongo mongosh musicapp --quiet --eval \
  'db.playlists.find({name:"My Mix"}).pretty()'
```

Assert in **Neo4j** — only the ownership relationship exists, and there are **no** CONTAINS edges:

```bash
docker exec -i musicapp-neo4j cypher-shell -u neo4j -p database \
  "MATCH (pl:Playlist {name:'My Mix'})-[:OWNED_BY]->(u:User) RETURN pl.playlistId, u.userId;"

# Verify NO song-containment edges were created (expect 0):
docker exec -i musicapp-neo4j cypher-shell -u neo4j -p database \
  "MATCH (pl:Playlist {name:'My Mix'})-[r]->(s:Song) RETURN count(r);"
```

Expect one ownership row; `pl.playlistId` equals the playlist `id` in Mongo (shared id rule), and
the containment count is `0`.

---

## Scenario 2 — forced graph failure (compensation -> FAILED)

Restart the app with the demo toggle on so `PlaylistGraphService.createPlaylist` throws:

```bash
# stop the running app, then:
SAGA_GRAPH_FAIL_CREATE_PLAYLIST=true ./mvnw spring-boot:run
# (equivalently: ./mvnw spring-boot:run -Dspring-boot.run.arguments=--saga.graph.fail-create-playlist=true)
```

```bash
SAGA=$(curl -s -X POST http://localhost:8080/api/playlists \
  -H 'Content-Type: application/json' \
  -d '{"ownerId":"user-1","name":"Rollback Mix","songIds":["song-1"]}' \
  | sed -E 's/.*"sagaId":"([^"]+)".*/\1/')

curl -s http://localhost:8080/api/sagas/$SAGA   # STARTED -> MONGO_DONE -> COMPENSATING -> FAILED
```

What to observe:
- App log shows `graph/createPlaylist failed: Forced graph failure...`, then the orchestrator sends
  the compensating `MongoDeletePlaylist`.
- Saga ends at **FAILED**.
- The Mongo document was rolled back (compensation deleted it):

```bash
docker exec -i musicapp-mongo mongosh musicapp --quiet --eval \
  'db.playlists.countDocuments({name:"Rollback Mix"})'   # -> 0
```

- No `:Playlist` node for it in Neo4j (the graph step never committed).

---

## PowerShell equivalents

```powershell
$r = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/playlists `
  -ContentType application/json `
  -Body '{"ownerId":"user-1","name":"My Mix","songIds":["song-1","song-2"]}'
Invoke-RestMethod -Uri "http://localhost:8080/api/sagas/$($r.sagaId)"
```
