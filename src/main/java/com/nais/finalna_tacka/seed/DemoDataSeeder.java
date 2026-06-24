package com.nais.finalna_tacka.seed;

import com.nais.finalna_tacka.domain.mongo.Artist;
import com.nais.finalna_tacka.domain.mongo.Song;
import com.nais.finalna_tacka.domain.mongo.User;
import com.nais.finalna_tacka.repository.mongo.ArtistRepository;
import com.nais.finalna_tacka.repository.mongo.PlaylistRepository;
import com.nais.finalna_tacka.repository.mongo.SagaStateRepository;
import com.nais.finalna_tacka.repository.mongo.SongRepository;
import com.nais.finalna_tacka.repository.mongo.UserRepository;
import com.nais.finalna_tacka.saga.orchestrator.SagaOrchestrator;
import com.nais.finalna_tacka.saga.state.PlaylistPayload;
import com.nais.finalna_tacka.saga.state.SagaState;
import com.nais.finalna_tacka.saga.state.SagaStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Jedinstveni demo seeder: 5 izvođača, 20 pesama, 10 korisnika, mnoštvo LISTENED relacija i
 * 5 plejlisti — sve kroz sage (PUBLISH_SONG, RECORD_LISTEN, CREATE_PLAYLIST) tako da MongoDB i
 * Neo4j ostaju u sync-u. Pokriva sve sekcije izveštaja iz jednog mesta:
 * proste (Mongo pesme po žanru/trajanju) i složene (CF preporuke + Top najslušanije).
 *
 * <p>Id-jevi su usklađeni sa Grafana dashboard-om: korisnici {@code u1..u10}, žanrovi
 * {@code rock/pop/jazz/hip-hop/electronic}.</p>
 *
 * <p>Pokreće se na svakom startu aplikacije ({@link ApplicationRunner}), ali je idempotentan:
 * ako je već jednom uspešno seed-ovano (postoji {@link #FIRST_SONG_ID}) preskače se, pa nema
 * dupliranja niti dvostrukog brojanja LISTENED relacija. Može se isključiti sa
 * {@code app.seed.demo.enabled=false}.</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.demo.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    static final String FIRST_SONG_ID = "song-1";

    private final SagaOrchestrator orchestrator;
    private final SagaStateRepository sagaStateRepository;
    private final ArtistRepository artistRepository;
    private final SongRepository songRepository;
    private final UserRepository userRepository;
    private final PlaylistRepository playlistRepository;

    @Value("${app.seed.demo.saga-timeout-seconds:30}")
    private int sagaTimeoutSeconds;

    /** Demo prekidači za kompenzaciju — ako su uključeni, sage namerno padaju pa seed nema smisla. */
    @Value("${saga.graph.fail-create:false}")
    private boolean failCreate;

    @Value("${saga.graph.fail-listen:false}")
    private boolean failListen;

    @Override
    public void run(ApplicationArguments args) {
        // "Već pokrenut jednom?" provera: ako prva pesma postoji, seed je već odrađen — preskoči.
        if (songRepository.existsById(FIRST_SONG_ID)) {
            log.info("Demo data already seeded (songId={} postoji), preskačem ponovni seed", FIRST_SONG_ID);
            return;
        }

        // Ako je uključen demo prekidač za forsiran pad grafa, sage bi svakako padale — preskoči
        // seed sa jasnom porukom umesto da pravimo/kompenzujemo song-1 u krug.
        if (failCreate || failListen) {
            log.warn("Demo seed PRESKOČEN: uključen je demo prekidač (saga.graph.fail-create={}, "
                    + "saga.graph.fail-listen={}) koji namerno ruši sage. Ukloni env var "
                    + "SAGA_GRAPH_FAIL_CREATE / SAGA_GRAPH_FAIL_LISTEN (ili --saga.graph.fail-* argument) "
                    + "iz Run konfiguracije i potpuno restartuj aplikaciju za pun seed.",
                    failCreate, failListen);
            return;
        }

        // Seed je samo demo podatak: ako padne (npr. uključen saga.graph.fail-create=true ili je
        // neki resurs nedostupan), NE rušimo pokretanje aplikacije — logujemo grešku i nastavljamo.
        try {
            seedAll();
        } catch (RuntimeException e) {
            log.error("Demo seed nije uspeo i preskočen je (aplikacija nastavlja da radi). "
                    + "Ako je uključen demo prekidač saga.graph.fail-create/fail-listen=true, isključi ga "
                    + "za normalan seed. Uzrok: {}", e.getMessage());
        }
    }

    private void seedAll() {
        log.info("Seeding demo data: 5 izvođača, 20 pesama, 10 korisnika, 5 plejlisti, mnogo LISTENED...");

        // --- 5 izvođača (jedan po žanru) ---
        Artist rock = ensureArtist("artist-1", "Crimson Echo", "RS");
        Artist pop = ensureArtist("artist-2", "Sugar Avenue", "GB");
        Artist jazz = ensureArtist("artist-3", "Blue Mirage", "US");
        Artist hiphop = ensureArtist("artist-4", "Concrete Kings", "DE");
        Artist electronic = ensureArtist("artist-5", "Pulse Theory", "SE");

        // --- 20 pesama (po 4 u svakom žanru) preko PUBLISH_SONG sage ---
        publishSong("song-1", "Iron Dawn", "rock", 215, rock);
        publishSong("song-2", "Stone River", "rock", 198, rock);
        publishSong("song-3", "Wild Current", "rock", 242, rock);
        publishSong("song-4", "Granite Sky", "rock", 176, rock);
        publishSong("song-5", "Sweet Signal", "pop", 188, pop);
        publishSong("song-6", "Paper Hearts", "pop", 203, pop);
        publishSong("song-7", "Golden Hour", "pop", 167, pop);
        publishSong("song-8", "Neon Smile", "pop", 195, pop);
        publishSong("song-9", "Midnight Brass", "jazz", 327, jazz);
        publishSong("song-10", "Velvet Note", "jazz", 301, jazz);
        publishSong("song-11", "Blue Corner", "jazz", 288, jazz);
        publishSong("song-12", "Slow Tide", "jazz", 314, jazz);
        publishSong("song-13", "City Pulse", "hip-hop", 172, hiphop);
        publishSong("song-14", "Block Anthem", "hip-hop", 165, hiphop);
        publishSong("song-15", "Heavy Crown", "hip-hop", 158, hiphop);
        publishSong("song-16", "Concrete Flow", "hip-hop", 142, hiphop);
        publishSong("song-17", "Voltage", "electronic", 256, electronic);
        publishSong("song-18", "Neon Drift", "electronic", 268, electronic);
        publishSong("song-19", "Pulse Engine", "electronic", 360, electronic);
        publishSong("song-20", "Circuit Bloom", "electronic", 231, electronic);

        // --- 10 korisnika ---
        for (int i = 1; i <= 10; i++) {
            ensureUser("u" + i, "User " + i);
        }

        // --- Mnoštvo LISTENED relacija (48 različitih) sa preklapanjima za CF preporuke ---
        listen("u1", 1, 2, 3, 5);                       // rock fan (+ jedna pop)
        listen("u2", 1, 2, 5, 6, 7);                    // "blizanac" sa u1 (1,2) + pop
        listen("u3", 5, 6, 7, 8);                       // pop fan
        listen("u4", 9, 10, 11, 1);                     // jazz + deli rock 1 sa u1
        listen("u5", 9, 10, 13, 14);                    // jazz/hip-hop most
        listen("u6", 13, 14, 15, 16);                   // hip-hop fan
        listen("u7", 17, 18, 19, 20);                   // electronic fan
        listen("u8", 17, 18, 1, 2);                     // "blizanac" sa u1 (1,2) + electronic
        listen("u9", 1, 5, 9, 13, 17);                  // eklektičan (po jedna iz svakog žanra)
        listen("u10", 1, 2, 3, 4, 5, 6, 9, 13, 17, 18); // power user, širok spektar

        // --- 5 plejlisti (naziv sadrži žanr) preko CREATE_PLAYLIST sage ---
        // Namerno PODSKUP pesama svog žanra (po 2 od 4), da "genre-recommendations" sekcija ima
        // šta da preporuči (preostale pesme istog žanra koje nisu u plejlisti).
        createPlaylist("playlist-1", "u1", "Rock Essentials", 1, 2);      // preporuči rock 3, 4
        createPlaylist("playlist-2", "u2", "Pop Hits", 5, 6);             // preporuči pop 7, 8
        createPlaylist("playlist-3", "u4", "Jazz Vibes", 9, 10);          // preporuči jazz 11, 12
        createPlaylist("playlist-4", "u6", "Hip-Hop Heat", 13, 14);       // preporuči hip-hop 15, 16
        createPlaylist("playlist-5", "u9", "Electronic Mix", 17, 18);     // preporuči electronic 19, 20

        log.info("Demo seed complete: {} pesama, {} izvođača, {} korisnika, {} plejlisti. "
                        + "Proveri: GET /api/reports/recommendations/u1 | GET /api/reports/top-users",
                songRepository.count(), artistRepository.count(),
                userRepository.count(), playlistRepository.count());
    }

    // --- Saga-driven seed helpers ---

    private void publishSong(String songId, String title, String genre, int durationSeconds, Artist artist) {
        Song song = new Song();
        song.setId(songId);
        song.setTitle(title);
        song.setArtist(artist);
        song.setAlbumId("album-" + artist.getId());
        song.setGenre(genre);
        song.setDurationSeconds(durationSeconds);
        song.setPlayCount(0);
        song.setCreatedAt(Instant.now());

        String sagaId = orchestrator.startPublishSong(song);
        waitForTerminalSaga(sagaId, "PUBLISH_SONG " + title);
    }

    private void listen(String userId, int... songNumbers) {
        for (int songNumber : songNumbers) {
            String sagaId = orchestrator.startRecordListen(userId, songId(songNumber));
            waitForTerminalSaga(sagaId, "RECORD_LISTEN " + userId + " -> " + songId(songNumber));
        }
    }

    private void createPlaylist(String playlistId, String ownerId, String name, int... songNumbers) {
        List<String> songIds = Arrays.stream(songNumbers).mapToObj(DemoDataSeeder::songId).toList();
        String sagaId = orchestrator.startCreatePlaylist(
                new PlaylistPayload(playlistId, ownerId, name, songIds));
        waitForTerminalSaga(sagaId, "CREATE_PLAYLIST " + name);
    }

    // --- Direct Mongo prerequisites ---

    private Artist ensureArtist(String id, String name, String country) {
        return artistRepository.findById(id).orElseGet(() -> {
            Artist artist = new Artist();
            artist.setId(id);
            artist.setName(name);
            artist.setCountry(country);
            return artistRepository.save(artist);
        });
    }

    private void ensureUser(String userId, String username) {
        userRepository.findById(userId).orElseGet(() -> {
            User user = new User();
            user.setId(userId);
            user.setUsername(username);
            user.setEmail(userId + "@test.rs");
            return userRepository.save(user);
        });
    }

    private static String songId(int number) {
        return "song-" + number;
    }

    // --- Saga wait helpers (kao u ostalim seeder-ima) ---

    private void waitForTerminalSaga(String sagaId, String label) {
        long deadline = System.currentTimeMillis() + sagaTimeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            SagaState state = sagaStateRepository.findById(sagaId)
                    .orElseThrow(() -> new IllegalStateException("Saga not found: " + sagaId));
            if (state.getStatus().isTerminal()) {
                if (state.getStatus() == SagaStatus.FAILED) {
                    throw new IllegalStateException("Seed saga FAILED (" + label + "): " + sagaId);
                }
                return;
            }
            sleep(500);
        }
        throw new IllegalStateException(
                "Seed saga timeout after " + sagaTimeoutSeconds + "s (" + label + "): " + sagaId);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Seed interrupted", e);
        }
    }
}
