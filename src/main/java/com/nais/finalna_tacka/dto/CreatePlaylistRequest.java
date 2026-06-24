package com.nais.finalna_tacka.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Incoming payload for POST /api/playlists. The playlist id is NOT accepted from the client:
 * the controller generates it so Mongo and Neo4j share the same id. {@code songIds} is optional
 * (a playlist may start empty).
 */
public record CreatePlaylistRequest(
        @NotBlank String ownerId,
        @NotBlank String name,
        List<String> songIds) {
}
