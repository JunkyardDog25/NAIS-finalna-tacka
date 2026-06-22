package com.nais.finalna_tacka.dto;

import jakarta.validation.constraints.NotBlank;

public record ArtistDto(
        @NotBlank String artistId,
        @NotBlank String artistName) {
}
