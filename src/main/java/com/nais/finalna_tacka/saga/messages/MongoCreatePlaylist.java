package com.nais.finalna_tacka.saga.messages;

import com.nais.finalna_tacka.saga.state.PlaylistPayload;

/**
 * Command: orchestrator -> Mongo participant. Insert the playlist document.
 * Forward step of the CREATE_PLAYLIST saga.
 */
public record MongoCreatePlaylist(String sagaId, PlaylistPayload payload) {
}
