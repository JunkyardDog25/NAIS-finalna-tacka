package com.nais.finalna_tacka.service;

import com.nais.finalna_tacka.domain.mongo.Song;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Mongo strana RECORD_LISTEN sage. Poziva je {@code MongoSagaParticipant}:
 * <ul>
 *   <li>{@link #recordListen} za {@code MongoRecordListen} (forward korak)</li>
 *   <li>{@link #compensateListen} za {@code MongoCompensateListen} (kompenzacija pri padu grafa)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ListenMongoService {

    private static final Logger log = LoggerFactory.getLogger(ListenMongoService.class);

    private final MongoTemplate mongoTemplate;

    /** Inkrementira playCount na dokumentu pesme. Baca grešku ako pesma ne postoji. */
    public void recordListen(String userId, String songId) {
        var result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(songId)),
                new Update().inc("playCount", 1),
                Song.class);

        if (result.getMatchedCount() == 0) {
            throw new IllegalArgumentException("Song not found: " + songId);
        }

        log.info("Recorded listen in Mongo: user {} -> song {} (playCount++)", userId, songId);
    }

/**
     * Poništava jedan playCount inkrement. Idempotentno: spušta na 0 ako je već kompenzovano
     * ili je count bio 0.
     */
    public void compensateListen(String userId, String songId) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(songId)),
                new Update().inc("playCount", -1),
                Song.class);

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(songId).and("playCount").lt(0)),
                new Update().set("playCount", 0),
                Song.class);

        log.info("Compensated listen in Mongo: user {} -> song {} (playCount--, floor 0)", userId, songId);
    }
}
