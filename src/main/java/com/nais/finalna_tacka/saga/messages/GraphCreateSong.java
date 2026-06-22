package com.nais.finalna_tacka.saga.messages;

import com.nais.finalna_tacka.domain.mongo.Song;

/**
 * Command: orchestrator -> Neo4j participant. Create the song node + relationships.
 * Forward step of the PUBLISH_SONG saga (and compensation of DELETE_SONG).
 */
public record GraphCreateSong(String sagaId, Song payload) {
}
