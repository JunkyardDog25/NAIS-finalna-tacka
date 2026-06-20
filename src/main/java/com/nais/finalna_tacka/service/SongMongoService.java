package com.nais.finalna_tacka.service;

import com.nais.finalna_tacka.domain.mongo.Playlist;
import com.nais.finalna_tacka.domain.mongo.Song;
import com.nais.finalna_tacka.repository.mongo.SongRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Mongo side of the song sagas. Invoked by {@code MongoSagaParticipant}:
 * <ul>
 *   <li>{@link #createSong} for {@code MongoCreateSong} (PUBLISH forward / DELETE compensation)</li>
 *   <li>{@link #deleteSong} for {@code MongoDeleteSong} (DELETE forward / PUBLISH compensation)</li>
 * </ul>
 */
@Service
public class SongMongoService {

    private static final Logger log = LoggerFactory.getLogger(SongMongoService.class);

    private final SongRepository songRepository;
    private final MongoTemplate mongoTemplate;

    public SongMongoService(SongRepository songRepository, MongoTemplate mongoTemplate) {
        this.songRepository = songRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Insert the song document. Idempotent: if a document with the same id already exists
     * (a redelivered command under at-least-once delivery) it is treated as success.
     */
    public void createSong(Song song) {
        if (song.getId() != null && songRepository.existsById(song.getId())) {
            log.info("Song {} already in Mongo; create is a no-op (idempotent)", song.getId());
            return;
        }
        songRepository.save(song);
        log.info("Inserted song {} into Mongo", song.getId());
    }

    /**
     * Delete the song document and remove it from every playlist that references it.
     * Idempotent: deleting a missing song and pulling an absent id are both no-ops, so a
     * redelivered command (or the same command used as a PUBLISH compensation) is safe.
     */
    public void deleteSong(String songId) {
        // Remove the song id from all playlists' songIds arrays ($pull is a no-op if absent).
        long playlistsUpdated = mongoTemplate.updateMulti(
                Query.query(Criteria.where("songIds").is(songId)),
                new Update().pull("songIds", songId),
                Playlist.class).getModifiedCount();

        songRepository.deleteById(songId);
        log.info("Deleted song {} from Mongo (removed from {} playlist(s))", songId, playlistsUpdated);
    }
}
