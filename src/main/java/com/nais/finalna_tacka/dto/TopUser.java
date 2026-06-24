package com.nais.finalna_tacka.dto;

/**
 * Jedan red složene sekcije "Top korisnici po popularnosti plejlisti" — kombinuje OBE baze:
 * <ul>
 *   <li>{@code totalPlayCount} — iz MongoDB: suma {@code playCount} svih pesama u SVIM plejlistama
 *       koje korisnik poseduje ({@code playlists.ownerId} + {@code playlists.songIds});</li>
 *   <li>{@code totalListeners} — iz Neo4j: suma različitih slušalaca po tim pesmama (LISTENED graf).</li>
 * </ul>
 * Rangira se po {@code totalPlayCount} (korisnici čije plejliste sadrže najpopularnije pesme).
 */
public record TopUser(
        String ownerId,
        String ownerName,
        int playlistCount,
        long totalPlayCount,
        long totalListeners) {}
