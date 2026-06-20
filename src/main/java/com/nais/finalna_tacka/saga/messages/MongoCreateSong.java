package com.nais.finalna_tacka.saga.messages;

import com.nais.finalna_tacka.domain.mongo.Song;

/**
 * Command: orchestrator -> Mongo participant. Insert the song document.
 * Forward step of the PUBLISH_SONG saga (and compensation of DELETE_SONG).
 */
public record MongoCreateSong(String sagaId, Song payload) {
}
