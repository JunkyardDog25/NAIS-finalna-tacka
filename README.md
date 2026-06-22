# finalna-tacka — Music App (MongoDB + Neo4j)

Spring Boot application backed by **two** data stores:

- **MongoDB** — document store of record (`domain.mongo`, `repository.mongo`)
- **Neo4j** — graph projection for relationship queries (`domain.graph`, `repository.graph`)

> **Key invariant:** the same entity has the **same id** in both stores
> (`User.id` == `User.userId`, `Song.id` == `Song.songId`, `Artist.id` == `Artist.artistId`).
> This is what lets the **Saga** (`saga` package) keep the two stores in sync.

This repository is the **shared skeleton** only. There is no business logic yet — the
`saga`, `service`, and report `controller` parts are marked with `// TODO` for teammates.

## Project layout

```
com.nais.finalna_tacka
├── config           # DatabaseConfig: splits Mongo vs Neo4j repository scanning
├── domain.mongo     # @Document classes: User, Artist, Album, Song, Playlist
├── domain.graph     # @Node classes: User, Song, Artist, Genre + ListenedRel
├── repository.mongo # MongoRepository per document
├── repository.graph # Neo4jRepository per node
├── service          # (TODO) business services
├── saga             # (TODO) cross-store sync saga
└── controller       # HealthController (+ TODO report controllers)
```

> Note: the base package is `com.nais.finalna_tacka` (underscore) because Java package
> names cannot contain a hyphen, while the Maven artifact/dir stays `finalna-tacka`.

## Prerequisites

- JDK 21
- Docker + Docker Compose

## 1. Start the infrastructure

```bash
docker compose up -d
```

This starts:

| Service  | Ports        | Notes                                                        |
|----------|--------------|-------------------------------------------------------------|
| mongo    | 27017        | single-node replica set `rs0` (auto-initiated, enables txns) |
| neo4j    | 7474, 7687   | APOC enabled; Browser at http://localhost:7474              |
| grafana  | 3000         | Infinity datasource plugin pre-installed; http://localhost:3000 |

Wait until Mongo is healthy (the healthcheck runs `rs.initiate()` on first boot):

```bash
docker compose ps
```

Default credentials:

- **Neo4j**: `neo4j` / `database` (override via `NEO4J_AUTH`)

  > `NEO4J_AUTH` only takes effect on the **first** start against an empty `neo4j-data`
  > volume. If you change the password later you must recreate the volume
  > (`docker compose down -v`) or the old password stays in effect.
- **Grafana**: `admin` / `admin` (you'll be asked to change it on first login)

## 2. Configuration

`application.properties` uses **Spring Boot 4** property names. Mongo connection settings
use the `spring.mongodb.*` prefix (the old `spring.data.mongodb.*` host/port/database keys
are ignored in Boot 4 and the app would default to database `test`).

| Property / env var | Default |
|--------------------|---------|
| `spring.mongodb.uri` | `mongodb://localhost:27017/musicapp?replicaSet=rs0` |
| `SPRING_MONGODB_URI` | same (env override for `spring.mongodb.uri`) |
| `NEO4J_URI` | `bolt://localhost:7687` |
| `NEO4J_USERNAME` | `neo4j` |
| `NEO4J_PASSWORD` | `database` |

In MongoDB Compass, connect to `localhost:27017` and open database **`musicapp`** (not
`test`) to see application data.

## 3. Build & run the app

```bash
# build (skip tests — the context test needs the databases up)
./mvnw clean package -DskipTests

# run
./mvnw spring-boot:run
```

On Windows use `mvnw.cmd` instead of `./mvnw`.

## 4. Verify both connections

```bash
curl http://localhost:8080/health
```

Expected when both stores are reachable:

```json
{ "mongo": "UP", "neo4j": "UP", "status": "UP" }
```

Returns HTTP `503` with per-store error messages if either store is down.

## Tear down

```bash
docker compose down        # keep data
docker compose down -v     # also remove named volumes
```
