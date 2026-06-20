package com.nais.finalna_tacka.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Incoming payload for POST /api/songs. The song id is NOT accepted from the client: the
 * orchestrator generates it so Mongo and Neo4j share the same id.
 */
public record PublishSongRequest(
        @NotBlank String title,
        @NotBlank String artistId,
        String albumId,
        @NotBlank String genre,
        @Positive int durationSeconds) {
}
