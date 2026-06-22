package com.nais.finalna_tacka.dto;

/**
 * Jedan red proste sekcije izveštaja — ravna (flat) projekcija {@code songs} kolekcije
 * iz MongoDB, spremna za Grafana Infinity table panele.
 */
public record SongRow(
        String songId,
        String title,
        String artistName,
        String genre,
        int durationSeconds,
        long playCount) {}
