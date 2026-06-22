package com.nais.finalna_tacka.saga.messages;

/**
 * Command: orchestrator -> Neo4j participant. Delete the song node + relationships.
 * Forward step of the DELETE_SONG saga (and compensation of PUBLISH_SONG).
 */
public record GraphDeleteSong(String sagaId, String songId) {
}
