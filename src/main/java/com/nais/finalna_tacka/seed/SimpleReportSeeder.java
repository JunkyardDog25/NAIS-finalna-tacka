package com.nais.finalna_tacka.seed;

import com.nais.finalna_tacka.domain.mongo.Album;
import com.nais.finalna_tacka.domain.mongo.Artist;
import com.nais.finalna_tacka.domain.mongo.Song;
import com.nais.finalna_tacka.repository.mongo.AlbumRepository;
import com.nais.finalna_tacka.repository.mongo.ArtistRepository;
import com.nais.finalna_tacka.repository.mongo.SongRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Seed za proste sekcije izveštaja (Mongo-only tabele pesama).
 *
 * <p>Za razliku od {@link CfReportDataSeeder}, ovi redovi se upisuju direktno preko
 * repozitorijuma (bez sage), jer služe samo za prosto čitanje iz MongoDB.
 * Aktivira se sa {@code spring.profiles.active=dev}. Idempotentan: preskače se ako
 * {@link #FIRST_SONG_ID} već postoji.</p>
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.simple-report.enabled", havingValue = "true", matchIfMissing = true)
public class SimpleReportSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimpleReportSeeder.class);

    static final String FIRST_SONG_ID = "rep-song-1";

    private final SongRepository songRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (songRepository.existsById(FIRST_SONG_ID)) {
            log.info("Simple report demo data already present (songId={}), skipping seed", FIRST_SONG_ID);
            return;
        }

        log.info("Seeding simple report demo data (rep-song-1..12 direktno u MongoDB)...");

        Artist nova = artist("rep-artist-nova", "Nova Echo", "RS");
        Artist lumen = artist("rep-artist-lumen", "Lumen Park", "GB");
        Artist sol = artist("rep-artist-sol", "Solaris Trio", "US");

        seedAlbum("rep-album-1", "Northern Lights", nova.getId(), 2019);
        seedAlbum("rep-album-2", "City Pulse", lumen.getId(), 2021);
        seedAlbum("rep-album-3", "Blue Sessions", sol.getId(), 2023);
        seedAlbum("rep-album-4", "Voltage", lumen.getId(), 2025);

        List<Song> songs = List.of(
                song("rep-song-1", "Granite Sky", nova, "rep-album-1", "rock", 215, 4200),
                song("rep-song-2", "Paper Hearts", lumen, "rep-album-2", "pop", 188, 5000),
                song("rep-song-3", "Midnight Brass", sol, "rep-album-3", "jazz", 327, 860),
                song("rep-song-4", "Concrete Flow", nova, "rep-album-2", "hip-hop", 142, 3100),
                song("rep-song-5", "Neon Drift", lumen, "rep-album-4", "electronic", 256, 2750),
                song("rep-song-6", "Stone Garden", nova, "rep-album-1", "rock", 198, 1500),
                song("rep-song-7", "Sugar Static", lumen, "rep-album-2", "pop", 173, 4600),
                song("rep-song-8", "Velvet Note", sol, "rep-album-3", "jazz", 301, 420),
                song("rep-song-9", "Block Party", nova, "rep-album-4", "hip-hop", 165, 2300),
                song("rep-song-10", "Pulse Engine", lumen, "rep-album-4", "electronic", 360, 90),
                song("rep-song-11", "Short Circuit", sol, "rep-album-3", "electronic", 95, 0),
                song("rep-song-12", "Anthem Rising", nova, "rep-album-1", "rock", 244, 3850));

        songRepository.saveAll(songs);

        log.info("Simple report seed complete: {} pesama, {} albuma. Proveri: "
                        + "GET /api/reports/songs?genre=rock | GET /api/reports/songs/by-duration?min=120&max=240",
                songs.size(), albumRepository.count());
    }

    private Artist artist(String id, String name, String country) {
        Artist artist = new Artist();
        artist.setId(id);
        artist.setName(name);
        artist.setCountry(country);
        return artistRepository.save(artist);
    }

    private void seedAlbum(String id, String title, String artistId, int releaseYear) {
        Album album = new Album();
        album.setId(id);
        album.setTitle(title);
        album.setArtistId(artistId);
        album.setReleaseYear(releaseYear);
        albumRepository.save(album);
    }

    private Song song(String id, String title, Artist artist, String albumId,
                      String genre, int durationSeconds, long playCount) {
        Song song = new Song();
        song.setId(id);
        song.setTitle(title);
        song.setArtist(artist);
        song.setAlbumId(albumId);
        song.setGenre(genre);
        song.setDurationSeconds(durationSeconds);
        song.setPlayCount(playCount);
        song.setCreatedAt(Instant.now());
        return song;
    }
}
