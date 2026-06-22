# Grafana — Music App Report

Dashboard **Music App Report** se automatski učitava iz
[`grafana/dashboards/music-app-report.json`](../grafana/dashboards/music-app-report.json)
(provisioning — bez ručnog klikanja).

Sadrži:

| Sekcija | Paneli | Baza |
|---------|--------|------|
| Prosta 1 | Pesme po žanru (tabela) | Mongo |
| Prosta 2 | Pesme po trajanju (tabela) | Mongo |
| **Složena** | CF preporuke (tabela) + CF bar chart | **Neo4j** |

**Podaci:** pokreni app sa `dev` profilom (automatski seed) ili prati
[`report-cf-demo.md`](report-cf-demo.md).

```powershell
./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
```

---

## 1. Pokretanje

```powershell
cd D:\Work\NAIS-finalna-tacka
docker compose up -d grafana
# Spring Boot app na :8080
```

| Servis | URL |
|--------|-----|
| Grafana | http://localhost:3000 |
| Dashboard | http://localhost:3000/d/music-app-report |
| Spring API | http://localhost:8080 |

**Login:** `admin` / `admin`

Infinity plugin + datasource + dashboard se učitavaju iz [`grafana/provisioning/`](../grafana/provisioning/).

---

## 2. Dashboard varijable

| Varijabla | Tip | Default | Paneli |
|-----------|-----|---------|--------|
| `genre` | custom | prazno = svi žanrovi | Pesme po žanru |
| `minDur` / `maxDur` | textbox | 120 / 240 | Pesme po trajanju |
| `userId` | custom | `u1` | CF preporuke (složena sekcija) |

---

## 3. Složena sekcija (Neo4j CF)

Dva panela na dnu dashboarda:

| Panel | Tip | Endpoint |
|-------|-----|----------|
| CF preporuke (složena sekcija) | Tabela | `GET /api/reports/recommendations/${userId}` |
| CF preporuke — bar chart | Bar chart | isti endpoint |

Kolone: `songId`, `title`, `poklapanje`.

Posle `dev` seed-a, za `userId=u1` očekuj **Song B** sa `poklapanje=1`.

Provera API-ja:

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/reports/recommendations/u1
```

---

## 4. Provera

| Korak | Očekivano |
|-------|-----------|
| `docker compose up -d grafana` + app na :8080 | Dashboard u folderu **Reports** |
| Dev seed | `cf-song-a` u Mongo, LISTENED u Neo4j |
| Panel „CF preporuke" za u1 | Song B, poklapanje 1 |
| Bar chart | stubac za Song B |

Ako Grafana ne vidi promene u JSON-u, restartuj kontejner:

```powershell
docker compose restart grafana
```

---

## Rešavanje problema

| Problem | Rešavanje |
|---------|-----------|
| Grafana „No data" | App na hostu? URL koristi `host.docker.internal:8080` |
| Prazan CF panel | Pokreni `dev` profil; proveri seed u logu |
| Prazan `title` | Publish pre listen — vidi `report-cf-demo.md` |
| Stari dashboard u UI | `docker compose restart grafana` ili obriši stari u UI pa reload |

---

## Za odbranu

- **Proste sekcije** = Mongo tabele (žanr, trajanje)
- **Složena sekcija** = Neo4j CF u `RecommendationService`, REST u `ReportController`
- **Grafikon** = bar chart panel nad istim JSON endpoint-om
- Sve u jednom provisioned dashboardu — Grafana je prezentacioni sloj

---

## Detalji provisioning-a

- **Datasource** `SpringReports` (uid `springreports`) —
  [`grafana/provisioning/datasources/infinity.yaml`](../grafana/provisioning/datasources/infinity.yaml)
- **Dashboard provider** —
  [`grafana/provisioning/dashboards/dashboards.yaml`](../grafana/provisioning/dashboards/dashboards.yaml)
- Bind mount u [`docker-compose.yml`](../docker-compose.yml):
  `./grafana/provisioning → /etc/grafana/provisioning`,
  `./grafana/dashboards → /var/lib/grafana/dashboards`

### Prosti paneli — endpoint-i

| Panel | Endpoint | Kolone |
|-------|----------|--------|
| Pesme po žanru | `GET /api/reports/songs?genre={genre}` | songId, title, artistName, genre, durationSeconds, playCount |
| Pesme po trajanju | `GET /api/reports/songs/by-duration?min={min}&max={max}` | isto |
