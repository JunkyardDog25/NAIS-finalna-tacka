package com.nais.finalna_tacka.saga.messages;

/**
 * Command: orchestrator -> Mongo participant. Delete the song document.
 * Forward step of the DELETE_SONG saga (and compensation of PUBLISH_SONG).
 */
public record MongoDeleteSong(String sagaId, String songId) {
}
