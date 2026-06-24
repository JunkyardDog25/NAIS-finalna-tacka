package com.nais.finalna_tacka.dto;

/**
 * Jedan red složene sekcije "Preporuke po žanru iz naziva plejliste".
 *
 * <p>Korisniku se preporučuje pesma čiji se žanr pojavljuje u nazivu neke njegove plejliste
 * (npr. rock pesma za plejlistu "Rock Classics"), a koja još NIJE ni u jednoj njegovoj plejlisti.</p>
 *
 * @param songName     naziv preporučene pesme
 * @param playlistName naziv plejliste zbog koje je pesma preporučena (njen žanr je u nazivu)
 */
public record GenrePlaylistRecommendation(String songName, String playlistName) {}
