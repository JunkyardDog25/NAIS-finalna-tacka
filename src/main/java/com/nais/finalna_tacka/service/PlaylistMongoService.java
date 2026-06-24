package com.nais.finalna_tacka.service;

import com.nais.finalna_tacka.domain.mongo.Playlist;
import com.nais.finalna_tacka.repository.mongo.PlaylistRepository;
import com.nais.finalna_tacka.repository.mongo.SongRepository;
import com.nais.finalna_tacka.repository.mongo.UserRepository;
import com.nais.finalna_tacka.saga.state.PlaylistPayload;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Mongo side of the CREATE_PLAYLIST saga (the playlist is authoritative here). Invoked by
 * {@code MongoSagaParticipant}:
 * <ul>
 *   <li>{@link #createPlaylist} for {@code MongoCreatePlaylist} (forward step)</li>
 *   <li>{@link #deletePlaylist} for {@code MongoDeletePlaylist} (compensation on graph failure)</li>
 * </ul>
 *
 * <p>Precondition for the forward step: the owner must exist as a User and every song id must
 * exist as a Song — otherwise the step throws, the participant replies success=false and the
 * saga ends FAILED with the graph untouched.</p>
 */
@Service
@RequiredArgsConstructor
public class PlaylistMongoService {

    private static final Logger log = LoggerFactory.getLogger(PlaylistMongoService.class);

    private final SongRepository songRepository;
    private final UserRepository userRepository;
    private final PlaylistRepository playlistRepository;

    /**
     * Insert the playlist document after validating owner + songs exist. Idempotent: if a
     * document with the same id already exists (a redelivered command under at-least-once
     * delivery) it is treated as success.
     */
    public void createPlaylist(PlaylistPayload p) {
        if (!userRepository.existsById(p.ownerId())) {
            throw new IllegalArgumentException("Owner not found: " + p.ownerId());
        }

        List<String> songIds = p.songIds() == null ? new ArrayList<>() : p.songIds();
        for (String songId : songIds) {
            if (!songRepository.existsById(songId)) {
                throw new IllegalArgumentException("Song not found: " + songId);
            }
        }

        if (playlistRepository.existsById(p.playlistId())) {
            log.info("Playlist {} already in Mongo; create is a no-op (idempotent)", p.playlistId());
            return;
        }

        Playlist playlist = new Playlist();
        playlist.setId(p.playlistId());
        playlist.setOwnerId(p.ownerId());
        playlist.setName(p.name());
        playlist.setSongIds(new ArrayList<>(songIds));
        playlistRepository.save(playlist);
        log.info("Inserted playlist {} into Mongo (owner={}, {} song(s))",
                p.playlistId(), p.ownerId(), songIds.size());
    }

    /**
     * Delete the playlist document. Idempotent: deleting a missing playlist is a no-op, so a
     * redelivered command (or the same command used as a CREATE compensation) is safe.
     */
    public void deletePlaylist(String playlistId) {
        playlistRepository.deleteById(playlistId);
        log.info("Deleted playlist {} from Mongo", playlistId);
    }
}
