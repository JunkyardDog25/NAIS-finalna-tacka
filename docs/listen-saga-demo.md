# RECORD_LISTEN saga — demo / verifikacija

Tri scenarija: happy path (saga dostigne `COMPLETED`, `playCount` u Mongo i `LISTENED` u Neo4j),
forsirani graph failure (Mongo kompenzovan, saga `FAILED`), i odbrambene beleške o
asimetričnoj kompenzaciji.

> Napomena o bazi: aplikacija upisuje u Mongo bazu `**test**`, pa sve `mongosh` komande
> ovde gađaju `test`. Pesme i `saga_state` su zajedno u `test`, što je sve što sagi treba.
> Sve komande su za PowerShell (Windows).

---

## Preduslovi

Kontejneri moraju biti `Up` (healthy):

```powershell
docker compose up -d
docker compose ps
```

Aplikacija pokrenuta (u zasebnom prozoru):

```powershell
./mvnw spring-boot:run
```

Seed korisnika `u1` u `test` (pipe pristup — bez problema sa navodnicima):

```powershell
'db.users.updateOne({_id:"u1"},{$set:{username:"demo",email:"demo@test.rs"}},{upsert:true})' | docker exec -i musicapp-mongo mongosh test --quiet
```

Uzmi `songId` postojeće pesme iz `test` (ako nemaš nijednu, prvo je publish-uj preko
`POST /api/songs`):

```powershell
$songId = (docker exec -i musicapp-mongo mongosh test --quiet --eval 'db.songs.findOne()._id').Trim()
"songId=$songId"
```

---

## Scenario 1 — happy path (COMPLETED)

```powershell
$listen = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/users/u1/listens/$songId"
"sagaId=$($listen.sagaId)"

do {
  Start-Sleep -Milliseconds 500
  $saga = Invoke-RestMethod -Uri "http://localhost:8080/api/sagas/$($listen.sagaId)"
  "status=$($saga.status)"
} while ($saga.status -notin @("COMPLETED","FAILED"))
```

Status prolazi `STARTED → MONGO_DONE → COMPLETED`.

Provera u **Mongo** (`playCount` inkrementiran):

```powershell
"db.songs.findOne({_id:'$songId'}).playCount" | docker exec -i musicapp-mongo mongosh test --quiet
```

Provera u **Neo4j** (`LISTENED` relacija sa count):

```powershell
docker exec -i musicapp-neo4j cypher-shell -u neo4j -p database "MATCH (u:User {userId:'u1'})-[r:LISTENED]->(s:Song {songId:'$songId'}) RETURN r.count;"
```

Oba broja se poklapaju (npr. oba `1` posle jednog slušanja). Ponovljeno slušanje iste pesme
povećava i `playCount` i `r.count` — `MERGE ... ON MATCH count+1` ne pravi novu relaciju,
samo povećava brojač.

---

## Scenario 2 — forsirani graph failure (Mongo kompenzacija → FAILED)

Uključi demo toggle u `src/main/resources/application.properties`:

```properties
saga.graph.fail-listen=true
```

> Napomena: pokretanje sa `-Dspring-boot.run.arguments=...` na Windows/PowerShell-u zna da
> ne radi (argument se okrnji). Zato menjaj `application.properties` direktno i restartuj.

Restartuj aplikaciju:

```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
./mvnw spring-boot:run
```

Pa pusti slušanje (treba da padne):

```powershell
$listen = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/users/u1/listens/$songId"
do {
  Start-Sleep -Milliseconds 500
  $saga = Invoke-RestMethod -Uri "http://localhost:8080/api/sagas/$($listen.sagaId)"
  "status=$($saga.status)"
} while ($saga.status -notin @("COMPLETED","FAILED"))
```

Šta se vidi:

- App log: graf `recordListen` baci grešku, orkestrator pošalje `MongoCompensateListen`.
- Status prolazi `STARTED → MONGO_DONE → COMPENSATING → FAILED`.
- Mongo `playCount` se vrati na vrednost pre pokušaja — forward korak ga inkrementira (+1),
pa kompenzacija dekrementira (−1), neto nepromenjen:

```powershell
"db.songs.findOne({_id:'$songId'}).playCount" | docker exec -i musicapp-mongo mongosh test --quiet
```

- Nema nove `LISTENED` relacije za ovaj pokušaj (graf korak nije commit-ovan).

Posle demonstracije vrati toggle na `false` i restartuj:

```properties
saga.graph.fail-listen=false
```

```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
./mvnw spring-boot:run
```

---

## Scenario 3 — odbrana (asimetrična kompenzacija, bez pokretanja)

Graf je **terminalni korak** u RECORD_LISTEN (Mongo → Neo4j). Posle uspešnog upisa u graf
saga ide direktno u `COMPLETED`, pa **kompenzacija grafa nije ukačena u orkestrator**.

Da je graf bio u sredini lanca, poništavanje `ON MATCH SET r.count = r.count + 1` bi
zahtevalo:

- `count == 1` → **obriši** relaciju (naivni `count--` ostavlja mrtvu relaciju sa `count=0`)
- `count > 1` → dekrement

Ta logika je implementirana (ali ne wire-ovana) u
`ListenGraphService.compensateRecordListen(userId, songId)`.

**Rečenica za odbranu:**

> „Graf je terminalni korak u mojoj sagi, pa kompenzacija grafa nije potrebna u runtime-u.
> Da je graf bio u sredini lanca, kompenzacija bi morala da razlikuje count=1 (obriši relaciju)
> od count>1 (dekrement), jer naivni count-- ostavlja mrtve relacije sa nulom."

**Poznato ograničenje (ako pitaju za idempotentnost):** ni Mongo `$inc` ni graf
`ON MATCH count+1` nisu idempotentni. Ako RabbitMQ isporuči poruku dvaput pre terminalnog
stanja, brojač se duplo poveća. Pravo rešenje je dedup po `sagaId`; za obim projekta je
dovoljno biti svestan ograničenja.

---

## Mapiranje na specifikaciju (5 poena)

- Dve NoSQL baze u jednoj funkcionalnosti: Mongo `playCount` + Neo4j `LISTENED`
- Insert/modify/delete: modifikacija dokumenta + upsert relacije
- Saga orchestration: postojeći RabbitMQ + orkestrator pattern
- Lični doprinos: Neo4j Cypher, graph participant, demo toggle, objašnjenje kompenzacije
- Timski rad: Mongo participant (kolega) vs Graph participant (ti)

