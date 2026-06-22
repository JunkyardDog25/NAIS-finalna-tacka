# RECORD_LISTEN saga — demo / verifikacija

Vodič za testiranje **od nule** (prazan Docker, prazna baza). Sve komande su za **PowerShell**
na Windows-u.

Tri scenarija na kraju: happy path (`COMPLETED`), forsirani graph failure (`FAILED` +
kompenzacija), i odbrambene beleške o asimetričnoj kompenzaciji.

---

## Šta RECORD_LISTEN zahteva pre testa

Saga **ne kreira pesmu** — samo povećava `playCount` u Mongo i `LISTENED` u Neo4j. Pre
`POST /api/users/{userId}/listens/{songId}` mora postojati:


| #   | Šta                                             | Zašto                                                       |
| --- | ----------------------------------------------- | ----------------------------------------------------------- |
| 1   | Docker: Mongo, Neo4j, RabbitMQ                  | Saga koristi obe baze + RabbitMQ                            |
| 2   | Spring Boot app na portu `8080`                 | REST + orchestrator + participanti                          |
| 3   | `saga.graph.fail-listen=false`                  | Inače graf korak namerno pada (Scenario 2)                  |
| 4   | Korisnik `u1` u Mongo (opciono ali preporučeno) | Dokument u `users`; graf `User` čvor MERGE-uje saga         |
| 5   | **Pesma u Mongo** sa poznatim `_id`             | `ListenMongoService` baca grešku ako pesma ne postoji       |
| 6   | **Isti `songId` u Neo4j** (preporučeno)         | Publish saga kreira `:Song` čvor; listen MERGE-uje relaciju |


Najlakši način za (5) i (6): prvo pokreni **PUBLISH_SONG** (`POST /api/songs`), sačekaj
`COMPLETED`, pa tek onda RECORD_LISTEN.

> **Baza:** aplikacija piše u Mongo bazu **`musicapp`**. Sve `mongosh` komande ovde
> koriste `musicapp`. (Ako vidiš podatke samo u `test`, app je verovatno radila sa
> zastarelim `spring.data.mongodb.*` property-jima — vidi korak 2.)

---

## 0. Softver i portovi

Na mašini treba da imaš:


| Komponenta          | Verzija / napomena      |
| ------------------- | ----------------------- |
| JDK                 | 21                      |
| Docker Desktop      | pokrenut                |
| Git + repozitorijum | kloniran u radni folder |


Portovi koji moraju biti slobodni:


| Servis        | Port    |
| ------------- | ------- |
| MongoDB       | `27017` |
| Neo4j Bolt    | `7687`  |
| Neo4j Browser | `7474`  |
| RabbitMQ AMQP | `5672`  |
| RabbitMQ UI   | `15672` |
| Aplikacija    | `8080`  |


---

## 1. Pokreni infrastrukturu (Docker)

Iz korena projekta:

```powershell
cd D:\Work\NAIS-finalna-tacka   # prilagodi putanju
docker compose up -d
```

Sačekaj da kontejneri budu healthy (posebno Mongo replica set `rs0`):

```powershell
docker compose ps
```

Očekivano: `musicapp-mongo`, `musicapp-neo4j`, `musicapp-rabbitmq` — status **Up** /
**(healthy)**.

Ako Mongo nije healthy, sačekaj ~30–60 s i ponovi `docker compose ps`.

---

## 2. Proveri konfiguraciju aplikacije

U `src/main/resources/application.properties` za **Scenario 1** (happy path) mora biti
(**Spring Boot 4** — `spring.mongodb.*`):

```properties
spring.mongodb.uri=mongodb://localhost:27017/musicapp?replicaSet=rs0
saga.graph.fail-listen=false
```

> Ne koristi `spring.data.mongodb.database=...` — u Boot 4 se ignoriše i app piše u
> podrazumevanu bazu `test`.

Neo4j lozinka u Docker-u podrazumevano je `database` (vidi `docker-compose.yml` /
`NEO4J_AUTH`).

---

## 3. Pokreni aplikaciju

U **zasebnom** PowerShell prozoru:

```powershell
cd D:\Work\NAIS-finalna-tacka
./mvnw spring-boot:run
```

Sačekaj log: `Started FinalnaTackaApplication`.

---

## 4. Health check (Mongo + Neo4j)

U drugom prozoru (dok app radi):

```powershell
Invoke-RestMethod -Uri http://localhost:8080/health
```

Očekivano:

```json
mongo: UP
neo4j: UP
status: UP
```

Ako je `503` / `DOWN`, ne nastavljaj — prvo popravi Docker konekciju.

> Health **ne proverava RabbitMQ**. Ako saga ostane u `STARTED`, proveri da li Rabbit radi:
> [http://localhost:15672](http://localhost:15672) (guest/guest).

---

## 5. Seed korisnika `u1` (Mongo)

```powershell
'db.users.updateOne({_id:"u1"},{$set:{username:"demo",email:"demo@test.rs"}},{upsert:true})' | docker exec -i musicapp-mongo mongosh musicapp --quiet
```

Provera:

```powershell
'db.users.findOne({_id:"u1"})' | docker exec -i musicapp-mongo mongosh musicapp --quiet
```

---

## 6. Publish pesme (PUBLISH_SONG) — obavezno pre RECORD_LISTEN

RECORD_LISTEN traži postojeći `songId`. Ako nemaš nijednu pesmu, objavi je ovako.

### 6.1 Pošalji publish

```powershell
$pub = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/songs `
  -ContentType application/json `
  -Body '{"title":"Listen Demo","artist":{"artistId":"artist-1","artistName":"The Testers"},"albumId":"album-1","genre":"rock","durationSeconds":200}'
"publish sagaId=$($pub.sagaId)"
```

### 6.2 Sačekaj COMPLETED

```powershell
do {
  Start-Sleep -Milliseconds 500
  $pubSaga = Invoke-RestMethod -Uri "http://localhost:8080/api/sagas/$($pub.sagaId)"
  "publish status=$($pubSaga.status)"
} while ($pubSaga.status -notin @("COMPLETED","FAILED"))
```

Mora biti `**COMPLETED**`. Ako je `FAILED`, pogledaj app log (Mongo/Neo4j/Rabbit) pre nego
što nastaviš.

### 6.3 Uzmi `songId` i početni `playCount`

```powershell
$json = ('JSON.stringify(db.songs.findOne({title:"Listen Demo"}, {_id:1, playCount:1}))' | docker exec -i musicapp-mongo mongosh musicapp --quiet --norc) -join "`n"
$m = [regex]::Match($json, '\{.*\}')
$doc = $m.Value | ConvertFrom-Json
$songId = $doc._id
$playCountBefore = if ($doc.playCount.PSObject.Properties.Name -contains 'low') { $doc.playCount.low } else { $doc.playCount }
"songId=$songId"
"playCountBefore=$playCountBefore"
```

Očekivano: `playCountBefore=0` (nova pesma).

Provera u Neo4j da publish saga je kreirala čvor:

```powershell
docker exec -i musicapp-neo4j cypher-shell -u neo4j -p database "MATCH (s:Song {songId:'$songId'}) RETURN s.songId, s.title;"
```

---

## 7. Spremno za RECORD_LISTEN — checklist

Pre Scenario 1 proveri:

- [ ] `docker compose ps` — mongo, neo4j, rabbitmq Up
- [ ] App radi, `/health` → `UP`
- [ ] `saga.graph.fail-listen=false`
- [ ] Korisnik `u1` postoji u `musicapp.users`
- [ ] Pesma postoji u `musicapp.songs`, imaš `$songId`
- [ ] Publish saga za tu pesmu je bila `COMPLETED`
- [ ] Znaš `$playCountBefore` (obično `0`)

---

## Scenario 1 — happy path (COMPLETED)

### 7.1 Pokreni slušanje

```powershell
$listen = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/users/u1/listens/$songId"
"listen sagaId=$($listen.sagaId)"
```

### 7.2 Sačekaj COMPLETED

```powershell
do {
  Start-Sleep -Milliseconds 500
  $saga = Invoke-RestMethod -Uri "http://localhost:8080/api/sagas/$($listen.sagaId)"
  "status=$($saga.status)"
} while ($saga.status -notin @("COMPLETED","FAILED"))
```

Očekivano: `STARTED` → `MONGO_DONE` → `**COMPLETED**`.

### 7.3 Provera Mongo (`playCount` +1)

```powershell
$playCountAfter = (docker exec -i musicapp-mongo mongosh musicapp --quiet --eval "print(db.songs.findOne({_id:'$songId'}).playCount)").Trim()
"playCountAfter=$playCountAfter (očekivano: $([int]$playCountBefore + 1))"
$playCountBefore = $playCountAfter   # osveži baseline za sledeće slušanje (korak 7.5)
```

> **Napomena:** `$playCountBefore` je vrednost **pre ovog** slušanja. Posle provere ga
> postavi na `$playCountAfter`, inače će pri drugom slušanju „očekivano“ i dalje računati
> od stare vrednosti (npr. 0+1=1 iako je u bazi već 2).

### 7.4 Provera Neo4j (`LISTENED` relacija)

```powershell
docker exec -i musicapp-neo4j cypher-shell -u neo4j -p database "MATCH (u:User {userId:'u2'})-[r:LISTENED]->(s:Song {songId:'$songId'}) RETURN r.count;"
```

Očekivano: jedan red, `r.count = 1` (posle prvog slušanja).

### 7.5 (Opciono) Drugo slušanje — isti user, ista pesma

Pre drugog slušanja **ponovo pročitaj** trenutni `playCount` (ili koristi `$playCountBefore`
ako si ga osvežio u koraku 7.3):

```powershell
$playCountBefore = [int]([regex]::Match((docker exec -i musicapp-mongo mongosh musicapp --quiet --eval "print(db.songs.findOne({_id:'$songId'}).playCount)"), '\d+').Value)
"playCountBefore =$playCountBefore"

$listen2 = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/users/u2/listens/$songId"
do {
  Start-Sleep -Milliseconds 500
  $saga2 = Invoke-RestMethod -Uri "http://localhost:8080/api/sagas/$($listen2.sagaId)"
  "status=$($saga2.status)"
} while ($saga2.status -notin @("COMPLETED","FAILED"))

$playCountAfter = [int]([regex]::Match((docker exec -i musicapp-mongo mongosh musicapp --quiet --eval "print(db.songs.findOne({_id:'$songId'}).playCount)"), '\d+').Value)
"playCountAfter=$playCountAfter (očekivano: $($playCountBefore + 1))"
```

Provera Neo4j — `r.count` takođe treba da bude za 1 veći nego posle prvog slušanja (npr. `2`):

```powershell
docker exec -i musicapp-neo4j cypher-shell -u neo4j -p database "MATCH (u:User {userId:'u1'})-[r:LISTENED]->(s:Song {songId:'$songId'}) RETURN r.count;"
```

---

## Scenario 2 — forsirani graph failure (Mongo kompenzacija → FAILED)

Koristi **istog** `$songId` i `$u1` kao posle Setup koraka 6. Zabeleži `playCount` pre testa.

### 8.1 Uključi demo toggle

U `src/main/resources/application.properties`:

```properties
saga.graph.fail-listen=true
```

> Na Windows/PowerShell `-Dspring-boot.run.arguments=...` zna da ne radi pouzdano. Menjaj
> `application.properties` i restartuj app.

### 8.2 Restartuj aplikaciju

```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
./mvnw spring-boot:run
```

### 8.3 Pokreni slušanje (treba da padne)

```powershell
# Pre slušanja
$playCountBeforeFail = [int]([regex]::Match((docker exec -i musicapp-mongo mongosh musicapp --quiet --eval "print(db.songs.findOne({_id:'$songId'}).playCount)"), '\d+').Value)
"playCountBeforeFail=$playCountBeforeFail"

$listen = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/users/u1/listens/$songId"
do {
  Start-Sleep -Milliseconds 500
  $saga = Invoke-RestMethod -Uri "http://localhost:8080/api/sagas/$($listen.sagaId)"
  "status=$($saga.status)"
} while ($saga.status -notin @("COMPLETED","FAILED"))

# Posle slušanja — kompenzacija mora da vrati playCount na početnu
$playCountAfterFail = [int]([regex]::Match((docker exec -i musicapp-mongo mongosh musicapp --quiet --eval "print(db.songs.findOne({_id:'$songId'}).playCount)"), '\d+').Value)
"sagaStatus=$($saga.status) (očekivano: FAILED)"
"playCountAfterFail=$playCountAfterFail (očekivano: $playCountBeforeFail — kompenzacija +1/−1)"
```

Očekivano: `STARTED` → `MONGO_DONE` → `COMPENSATING` → `**FAILED**`.

### 8.4 Šta proveriti

- App log: graf `recordListen` baca grešku, zatim `MongoCompensateListen`.
- Mongo `playCount` **nepromenjen** u odnosu na pre pokušaja (forward +1, kompenzacija −1):

```powershell
$playCountAfterFail = (docker exec -i musicapp-mongo mongosh musicapp --quiet --eval "print(db.songs.findOne({_id:'$songId'}).playCount)").Trim()
"pre=$playCountBeforeFail posle=$playCountAfterFail (mora biti isto)"
```

- Neo4j: nema **novog** inkrementa od ovog pokušaja (graf korak nije uspeo).

### 8.5 Vrati normalan rad

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

**Poznato ograničenje (ako pitaju za idempotentnost):** ni Mongo `$inc` ni graf  
`ON MATCH count+1` nisu idempotentni. Ako RabbitMQ isporuči poruku dvaput pre terminalnog  
stanja, brojač se duplo poveća. Pravo rešenje je dedup po `sagaId`; za obim projekta je  
dovoljno biti svestan ograničenja.

Brza provera broja dokumenata u pravoj bazi:

```powershell
'print("songs=" + db.songs.countDocuments() + " users=" + db.users.countDocuments() + " sagas=" + db.saga_state.countDocuments())' | docker exec -i musicapp-mongo mongosh musicapp --quiet
```

---

Kompenzacija u RECORD_LISTEN sagi je namerno asimetrična jer dva koraka nisu jednako reverzibilna. Mongo korak (`playCount++`) ima trivijalnu inverziju — `playCount--` čisto vraća stanje. Neo4j korak je terminalni: ide poslednji, pa ako uspe, saga je gotova i nema šta da se kompenzuje. Zato orkestrator nikad ne poziva graf kompenzaciju — ne postoji korak posle grafa koji bi mogao da padne i zahtevao njegov undo. Jedini realan failure scenario je da Neo4j padne, a tada se kompenzuje samo Mongo: `playCount` se vrati na početnu vrednost i saga završi kao FAILED, što i demonstriram u Scenariju 2. Graf kompenzacija ipak postoji u kodu (`ListenGraphService.compensateRecordListen`) zbog korektnosti i čitljivosti, i ona nije naivni `count--` — jer bi to ostavilo „mrtvu" relaciju sa `count=0` koja i dalje postoji u grafu. Umesto toga: ako je `count == 1`, relacija se briše u potpunosti; ako je `count > 1`, dekrementira se za jedan. Time graf nikad ne ostaje u nekonzistentnom stanju, a kompenzacija odražava stvarnu semantiku „poništavanja jednog slušanja".