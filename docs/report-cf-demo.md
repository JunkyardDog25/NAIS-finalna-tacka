# Collaborative filtering izveštaj — demo seed

Vodič za podatke koji pokreću **složenu Neo4j sekciju** izveštaja:  
`GET /api/reports/recommendations/{userId}`.

---

## Automatski seed (preporučeno)

Umesto ručnih PowerShell koraka ispod, pokreni app sa **`dev` profilom**. Klasa
`CfReportDataSeeder` automatski izvršava isti scenario kroz PUBLISH_SONG i RECORD_LISTEN
sage (Mongo + Neo4j ostaju usklađeni).

```powershell
docker compose up -d
./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Ili kopiraj [`.env.example`](../.env.example) u `.env` — već sadrži `SPRING_PROFILES_ACTIVE=dev`.

U logu traži:

```text
Seeding CF report demo data ...
CF report demo seed complete. Proveri: GET /api/reports/recommendations/u1
```

Odmah proveri:

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/reports/recommendations/u1 | ConvertTo-Json
```

**Fiksni ID-jevi** (lakše za debug nego UUID iz ručnog publish-a):

| Entitet | ID |
|---------|-----|
| Pesma A | `cf-song-a` |
| Pesma B | `cf-song-b` |
| Pesma C | `cf-song-c` |
| Artist | `artist-cf` |
| Korisnici | `u1`, `u2`, `u3` |

Seeder je **idempotentan**: ako `cf-song-a` već postoji u Mongo, preskače seed pri sledećem
startu. Za ponovni seed obriši CF pesme iz obe baze (npr. `db.songs.deleteMany({_id:/^cf-song-/})`
i odgovarajuće `:Song` čvorove u Neo4j).

Isključi seed: `app.seed.cf-report.enabled=false` u `application-dev.properties` ili env.

---

## Ručni seed (alternativa)

Ako ne koristiš `dev` profil, prati korake **0–5** ispod.

---

## Šta CF endpoint očekuje


| Uslov                                | Zašto                                                                                    |
| ------------------------------------ | ---------------------------------------------------------------------------------------- |
| Pesme **publish-ovane** pre slušanja | `Song` čvor u Neo4j dobija `title` pri PUBLISH_SONG; listen saga samo MERGE-uje `songId` |
| `LISTENED` relacije u grafu          | RECORD_LISTEN saga (`POST /api/users/{id}/listens/{songId}`)                             |
| Preklapanje između korisnika         | CF nalazi „slične" korisnike koji slušaju iste pesme                                     |


**Baza:** `musicapp` (vidi `application.properties`).

---

## Scenario preklapanja


| Korisnik | Sluša                                  |
| -------- | -------------------------------------- |
| `u1`     | pesma **A**                            |
| `u2`     | pesme **A** i **B**                    |
| `u3`     | pesma **C** (opciono, za dubinu grafa) |


**Očekivano za `u1`:** preporuka **B** — `u2` deli pesmu A sa `u1`, a sluša B koju `u1` nije.

```json
[
  {"songId":"cf-song-b","title":"Song B","poklapanje":1}
]
```

---

## 0. Preduslovi

```powershell
cd D:\Work\NAIS-finalna-tacka   # prilagodi putanju
docker compose up -d
```

Sačekaj healthy kontejnere (`mongo`, `neo4j`, `rabbitmq`):

```powershell
docker compose ps
```

U `application.properties` (ili `.env`):

```properties
spring.mongodb.uri=mongodb://localhost:27017/musicapp?replicaSet=rs0
saga.graph.fail-listen=false
saga.graph.fail-create=false
```

Pokreni aplikaciju u zasebnom prozoru:

```powershell
./mvnw spring-boot:run
```

Health:

```powershell
Invoke-RestMethod -Uri http://localhost:8080/health
```

---

## 1. Publish sve pesme (A, B, C) — **obavezno pre slušanja**

Koristi helper funkciju za poll saga statusa:

```powershell
function Wait-SagaCompleted {
    param([string]$SagaId)
    do {
        Start-Sleep -Milliseconds 500
        $s = Invoke-RestMethod -Uri "http://localhost:8080/api/sagas/$SagaId"
        "  status=$($s.status)"
    } while ($s.status -notin @("COMPLETED","FAILED"))
    if ($s.status -eq "FAILED") { throw "Saga $SagaId FAILED" }
}

function Publish-Song {
    param([string]$Title, [string]$Genre = "rock")
    $body = @{
        title = $Title
        artist = @{ artistId = "artist-cf"; artistName = "CF Demo Band" }
        albumId = "album-cf"
        genre = $Genre
        durationSeconds = 180
    } | ConvertTo-Json -Depth 3
    $pub = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/songs `
        -ContentType application/json -Body $body
    "publish '$Title' sagaId=$($pub.sagaId)"
    Wait-SagaCompleted -SagaId $pub.sagaId
}
```

Publish tri pesme:

```powershell
Publish-Song -Title "Song A"
Publish-Song -Title "Song B"
Publish-Song -Title "Song C"
```

Uzmi `songId` za svaku pesmu:

```powershell
function Get-SongIdByTitle {
    param([string]$Title)
    $json = ('JSON.stringify(db.songs.findOne({title:"' + $Title + '"}, {_id:1, title:1}))' |
        docker exec -i musicapp-mongo mongosh musicapp --quiet --norc) -join "`n"
    $m = [regex]::Match($json, '\{.*\}')
    ($m.Value | ConvertFrom-Json)._id
}

$songIdA = Get-SongIdByTitle -Title "Song A"
$songIdB = Get-SongIdByTitle -Title "Song B"
$songIdC = Get-SongIdByTitle -Title "Song C"
"songIdA=$songIdA"
"songIdB=$songIdB"
"songIdC=$songIdC"
```

Provera da Neo4j čvorovi imaju `title`:

```powershell
docker exec -i musicapp-neo4j cypher-shell -u neo4j -p database `
  "MATCH (s:Song) WHERE s.title IN ['Song A','Song B','Song C'] RETURN s.songId, s.title ORDER BY s.title;"
```

---

## 2. Seed korisnika (Mongo)

```powershell
'db.users.updateOne({_id:"u1"},{$set:{username:"u1",email:"u1@test.rs"}},{upsert:true})' | docker exec -i musicapp-mongo mongosh musicapp --quiet
'db.users.updateOne({_id:"u2"},{$set:{username:"u2",email:"u2@test.rs"}},{upsert:true})' | docker exec -i musicapp-mongo mongosh musicapp --quiet
'db.users.updateOne({_id:"u3"},{$set:{username:"u3",email:"u3@test.rs"}},{upsert:true})' | docker exec -i musicapp-mongo mongosh musicapp --quiet
```

---

## 3. Slušanja (RECORD_LISTEN saga)

Helper za listen + poll:

```powershell
function Record-Listen {
    param([string]$UserId, [string]$SongId)
    $listen = Invoke-RestMethod -Method Post `
        -Uri "http://localhost:8080/api/users/$UserId/listens/$SongId"
    "listen $UserId -> $SongId sagaId=$($listen.sagaId)"
    Wait-SagaCompleted -SagaId $listen.sagaId
}
```

**Redosled iz scenarija:**

```powershell
# u1 sluša A
Record-Listen -UserId u1 -SongId $songIdA

# u2 sluša A i B
Record-Listen -UserId u2 -SongId $songIdA
Record-Listen -UserId u2 -SongId $songIdB

# u3 sluša C (opciono)
Record-Listen -UserId u3 -SongId $songIdC
```

---

## 4. Provera LISTENED grafa (Neo4j)

```powershell
docker exec -i musicapp-neo4j cypher-shell -u neo4j -p database `
  "MATCH (u:User)-[r:LISTENED]->(s:Song) RETURN u.userId, s.title, r.count ORDER BY u.userId, s.title;"
```

Očekivano:


| userId | title  | count |
| ------ | ------ | ----- |
| u1     | Song A | 1     |
| u2     | Song A | 1     |
| u2     | Song B | 1     |
| u3     | Song C | 1     |


---

## 5. CF endpoint — glavna provera

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/reports/recommendations/u1 | ConvertTo-Json
```

Očekivano (redosled polja može varirati):

```json
[
  {
    "songId": "cf-song-b",
    "title": "Song B",
    "poklapanje": 1
  }
]
```

**Zašto nema Song C:** `u3` ne deli nijednu pesmu sa `u1` (u1 sluša samo A).

**Za `u2`:** endpoint vraća prazan niz `[]` — jedini „sličan" korisnik je `u1`, a on sluša
samo A koju `u2` već ima; nema novih pesama za preporuku.

---

## 6. Ručni Cypher (isti upit kao u servisu)

U Neo4j Browser ili `cypher-shell`:

```cypher
MATCH (me:User {userId: 'u1'})-[:LISTENED]->(:Song)<-[:LISTENED]-(other:User)
WHERE me <> other
MATCH (other)-[:LISTENED]->(rec:Song)
WHERE NOT (me)-[:LISTENED]->(rec)
RETURN rec.songId AS songId, rec.title AS title, count(DISTINCT other) AS poklapanje
ORDER BY poklapanje DESC
LIMIT 10;
```

**Obavezno:** publish pre listen — inače `title` na graf čvoru može biti prazan.