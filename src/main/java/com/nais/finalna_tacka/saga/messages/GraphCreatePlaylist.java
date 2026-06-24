package com.nais.finalna_tacka.saga.messages;

import com.nais.finalna_tacka.saga.state.PlaylistPayload;

/**
 * Command: orchestrator -> Neo4j participant. MERGE the playlist node + OWNED_BY ownership.
 * Forward step of the CREATE_PLAYLIST saga (graph stores ownership only, no CONTAINS edges).
 */
public record GraphCreatePlaylist(String sagaId, PlaylistPayload payload) {
}
