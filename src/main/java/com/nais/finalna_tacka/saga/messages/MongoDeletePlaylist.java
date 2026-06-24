package com.nais.finalna_tacka.saga.messages;

/**
 * Command: orchestrator -> Mongo participant. Delete the playlist document.
 * Compensation of the CREATE_PLAYLIST saga when the graph step fails.
 */
public record MongoDeletePlaylist(String sagaId, String playlistId) {
}
