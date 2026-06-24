package com.nais.finalna_tacka.saga.state;

import java.util.List;

/**
 * Payload za {@link SagaType#CREATE_PLAYLIST}: pleylista je autoritativna u MongoDB-u
 * (id, ownerId, name, songIds), dok Neo4j čuva SAMO vlasništvo ((:Playlist)-[:OWNED_BY]->(:User)).
 */
public record PlaylistPayload(String playlistId, String ownerId, String name, List<String> songIds) {
}
