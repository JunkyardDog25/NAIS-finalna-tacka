package com.nais.finalna_tacka.mapper;

import com.nais.finalna_tacka.domain.mongo.Artist;
import com.nais.finalna_tacka.domain.mongo.Song;
import com.nais.finalna_tacka.dto.ArtistDto;
import com.nais.finalna_tacka.dto.PublishSongRequest;
import com.nais.finalna_tacka.repository.mongo.ArtistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Builds a {@link Song} from a {@link PublishSongRequest}, resolving and embedding the full
 * {@link Artist} from Mongo. The song id is left null (the orchestrator assigns the shared id).
 */
@Component
@RequiredArgsConstructor
public class SongMapper {

    private final ArtistRepository artistRepository;

    public Song toSong(PublishSongRequest request) {
        ArtistDto dto = request.artist();
        // Upsert the artist so the name is always persisted, even if the id already
        // existed (e.g. created name-less by an earlier publish).
        Artist artist = artistRepository.findById(dto.artistId()).orElseGet(Artist::new);
        artist.setId(dto.artistId());
        artist.setName(dto.artistName());
        artist = artistRepository.save(artist);

        Song song = new Song();
        song.setTitle(request.title());
        song.setArtist(artist);
        song.setAlbumId(request.albumId());
        song.setGenre(request.genre());
        song.setDurationSeconds(request.durationSeconds());
        song.setPlayCount(0);
        song.setCreatedAt(Instant.now());
        return song;
    }
}
