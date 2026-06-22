# Grafana — Music App Report (CF sekcija)

Koraci za vizualizaciju **collaborative filtering** izveštaja preko Grafana **Infinity**
datasource-a. Aplikacija mora biti pokrenuta na hostu (`:8080`).

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
# Spring Boot app na :8080 (./mvnw spring-boot:run)
```

| Servis | URL |
|--------|-----|
| Grafana | http://localhost:3000 |
| Spring API | http://localhost:8080 |

**Login:** `admin` / `admin` (promeni lozinku pri prvom ulasku ako Grafana traži).

Infinity plugin je već u [`docker-compose.yml`](../docker-compose.yml)
(`yesoreyeram-infinity-datasource`).

---

## 2. Infinity datasource

1. **Connections → Data sources → Add new data source**
2. Izaberi **Infinity**
3. Podesi:
   - **Name:** `SpringReports`
   - **URL** (base): `http://host.docker.internal:8080`

   > Na Windows Docker Desktop-u `host.docker.internal` pokazuje na host mašinu gde radi
   > Spring Boot. **Ne koristi** `localhost:8080` iz Grafana kontejnera — to je localhost
   > samog kontejnera, ne tvoje aplikacije.

4. **Save & test**

Ako test ne uspe, proveri da app radi:

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/reports/recommendations/u1
```

---

## 3. Novi dashboard

**Dashboards → New → New dashboard**

Ime: `Music App Report`

---

## 4. Panel 1 — Tabela preporuka (složena sekcija)

1. **Add visualization**
2. **Data source:** `SpringReports`
3. **Query tip (Infinity):**
   - **Type:** JSON
   - **Parser:** JSON
   - **Source:** URL
   - **URL:** `/api/reports/recommendations/u1`
   - **Method:** GET

4. U **JSON / Root** ili **Columns** sekciji (zavisi od verzije Infinity plugina):
   - Root: `$` (ceo niz)
   - Kolone: `title`, `poklapanje`, `songId`

5. **Visualization:** Table

6. **Panel title:** `CF preporuke za u1`

7. **Save**

Očekivani podaci posle seed-a: jedan red — **Song B**, `poklapanje = 1`.

---

## 5. Panel 2 — Bar chart (obavezan grafikon)

1. **Add visualization** (ili Duplicate panel 1 pa promeni vizualizaciju)
2. **Isti datasource i URL:** `/api/reports/recommendations/u1`
3. **Visualization:** **Bar chart**
4. Podesi:
   - **X-axis / Labels:** polje `title`
   - **Y-axis / Value:** polje `poklapanje`
5. **Panel title:** `Collaborative filtering preporuke za u1`
6. **Save dashboard**

---

## 6. Provera

| Korak | Očekivano |
|-------|-----------|
| Seed iz `report-cf-demo.md` | `u1` → preporuka Song B |
| Panel tabela | 1 red: Song B, poklapanje 1 |
| Bar chart | jedan stubac za Song B |

Za korisnika bez preklapanja (npr. pre seed-a):

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/reports/recommendations/unknown-user
```

Vraća `[]` — Grafana prikazuje prazan panel (to je OK).

---

## 7. Kolegini Mongo paneli (referenca)

U isti dashboard `Music App Report` kolega može dodati proste sekcije sa istim
`SpringReports` datasource-om, npr.:

| Panel | Endpoint (primer) |
|-------|-------------------|
| Pesme po žanru | `GET /api/reports/songs/by-genre?genre=rock` |
| Top pesme | `GET /api/reports/songs/top?limit=10` |

Ti endpoint-i **nisu deo ovog CF zadatka** — dodaju se kada kolega implementira Mongo izveštaje.

---

## Rešavanje problema

| Problem | Rešavanje |
|---------|-----------|
| Grafana „No data" | Proveri seed; URL mora biti pun put od base URL-a |
| Connection refused | App na hostu? Koristi `host.docker.internal`, ne `localhost` |
| Prazan `title` u tabeli | Publish pesme **pre** listen — vidi `report-cf-demo.md` |
| Infinity plugin nedostaje | `docker compose up -d grafana` ponovo; proveri compose env za plugin |

---

## Za odbranu

- **Složena sekcija** = Neo4j CF upit u `RecommendationService`, REST u `ReportController`
- **Grafikon** = Grafana bar chart nad istim JSON endpoint-om
- Logika ostaje u Spring-u; Grafana je samo prezentacioni sloj
