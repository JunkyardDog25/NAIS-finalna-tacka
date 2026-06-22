package com.nais.finalna_tacka.seed;

import com.nais.finalna_tacka.domain.mongo.Artist;
import com.nais.finalna_tacka.domain.mongo.Song;
import com.nais.finalna_tacka.domain.mongo.User;
import com.nais.finalna_tacka.repository.mongo.ArtistRepository;
import com.nais.finalna_tacka.repository.mongo.SagaStateRepository;
import com.nais.finalna_tacka.repository.mongo.SongRepository;
import com.nais.finalna_tacka.repository.mongo.UserRepository;
import com.nais.finalna_tacka.saga.orchestrator.SagaOrchestrator;
import com.nais.finalna_tacka.saga.state.SagaState;
import com.nais.finalna_tacka.saga.state.SagaStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Automatski seed za CF izveštaj — isti scenario kao {@code docs/report-cf-demo.md},
 * ali kroz PUBLISH_SONG i RECORD_LISTEN sage (Mongo + Neo4j u sync-u).
 *
 * <p>Aktivira se sa {@code spring.profiles.active=dev}. Preskače se ako pesma
 * {@link #SONG_A_ID} već postoji (idempotentno pri ponovnom startu).</p>
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.cf-report.enabled", havingValue = "true", matchIfMissing = true)
public class CfReportDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CfReportDataSeeder.class);

    static final String ARTIST_ID = "artist-cf";
    static final String SONG_A_ID = "cf-song-a";
    static final String SONG_B_ID = "cf-song-b";
    static final String SONG_C_ID = "cf-song-c";
    static final String SONG_D_ID = "cf-song-d";
    static final String SONG_E_ID = "cf-song-e";
    static final String SONG_F_ID = "cf-song-f";

    private final SagaOrchestrator orchestrator;
    private final SagaStateRepository sagaStateRepository;
    private final ArtistRepository artistRepository;
    private final SongRepository songRepository;
    private final UserRepository userRepository;

    @Value("${app.seed.cf-report.saga-timeout-seconds:30}")
    private int sagaTimeoutSeconds;

    @Override
    public void run(ApplicationArguments args) {
        if (songRepository.existsById(SONG_A_ID)) {
            log.info("CF report demo data already present (songId={}), skipping seed", SONG_A_ID);
            return;
        }

        log.info("Seeding CF report demo data (publish A–F, users u1–u5, LISTENED relacije)...");

        Artist artist = seedArtist();
        publishSong(SONG_A_ID, "Song A", "rock", artist);
        publishSong(SONG_B_ID, "Song B", "rock", artist);
        publishSong(SONG_C_ID, "Song C", "pop", artist);
        publishSong(SONG_D_ID, "Song D", "pop", artist);
        publishSong(SONG_E_ID, "Song E", "jazz", artist);
        publishSong(SONG_F_ID, "Song F", "electronic", artist);

        seedUser("u1");
        seedUser("u2");
        seedUser("u3");
        seedUser("u4");
        seedUser("u5");

        // u1 i u2 dele A+B (jaki "blizanci") — u2 dodatno sluša D => snažna preporuka D za u1
        recordListen("u1", SONG_A_ID);
        recordListen("u1", SONG_B_ID);
        recordListen("u2", SONG_A_ID);
        recordListen("u2", SONG_B_ID);
        recordListen("u2", SONG_D_ID);

        // u4 takođe deli A sa u1 i sluša C => druga preporuka (manje poklapanje) za u1
        recordListen("u4", SONG_A_ID);
        recordListen("u4", SONG_C_ID);

        // u3 i u5 grade dodatnu dubinu grafa (jazz/electronic klaster, bez preklapanja sa u1)
        recordListen("u3", SONG_C_ID);
        recordListen("u3", SONG_E_ID);
        recordListen("u5", SONG_E_ID);
        recordListen("u5", SONG_F_ID);

        log.info("CF report demo seed complete. Proveri: GET /api/reports/recommendations/u1");
    }

    private Artist seedArtist() {
        return artistRepository.findById(ARTIST_ID).orElseGet(() -> {
            Artist artist = new Artist();
            artist.setId(ARTIST_ID);
            artist.setName("CF Demo Band");
            return artistRepository.save(artist);
        });
    }

    private void seedUser(String userId) {
        userRepository.findById(userId).orElseGet(() -> {
            User user = new User();
            user.setId(userId);
            user.setUsername(userId);
            user.setEmail(userId + "@test.rs");
            return userRepository.save(user);
        });
    }

    private void publishSong(String songId, String title, String genre, Artist artist) {
        Song song = new Song();
        song.setId(songId);
        song.setTitle(title);
        song.setArtist(artist);
        song.setAlbumId("album-cf");
        song.setGenre(genre);
        song.setDurationSeconds(180);
        song.setPlayCount(0);
        song.setCreatedAt(Instant.now());

        String sagaId = orchestrator.startPublishSong(song);
        waitForTerminalSaga(sagaId, "PUBLISH_SONG " + title);
    }

    private void recordListen(String userId, String songId) {
        String sagaId = orchestrator.startRecordListen(userId, songId);
        waitForTerminalSaga(sagaId, "RECORD_LISTEN " + userId + " -> " + songId);
    }

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
