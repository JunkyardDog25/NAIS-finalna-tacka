package com.nais.finalna_tacka.dto;

/**
 * Jedan red collaborative filtering izveštaja — pesma koju korisnik još nije slušao,
 * predložena na osnovu preklapanja sa drugim korisnicima u LISTENED grafu.
 */
public record SongRecommendation(String songId, String title, long poklapanje) {}
