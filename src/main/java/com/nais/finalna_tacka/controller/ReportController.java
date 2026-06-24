package com.nais.finalna_tacka.controller;

import com.nais.finalna_tacka.dto.GenrePlaylistRecommendation;
import com.nais.finalna_tacka.dto.SongRecommendation;
import com.nais.finalna_tacka.dto.SongRow;
import com.nais.finalna_tacka.dto.TopUser;
import com.nais.finalna_tacka.service.GenreRecommendationService;
import com.nais.finalna_tacka.service.RecommendationService;
import com.nais.finalna_tacka.service.SongReportService;
import com.nais.finalna_tacka.service.TopUsersService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST izveštaji za Grafana Infinity datasource.
 * Složene sekcije: CF preporuke iz Neo4j grafa; Top korisnici po popularnosti plejlisti (Mongo playCount + Neo4j LISTENED).
 * Proste sekcije: tabele pesama čitane direktno iz MongoDB.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final RecommendationService recommendationService;
    private final SongReportService songReportService;
    private final TopUsersService topUsersService;
    private final GenreRecommendationService genreRecommendationService;

    @GetMapping("/recommendations/{userId}")
    public List<SongRecommendation> recommendations(@PathVariable String userId) {
        return recommendationService.recommendFor(userId);
    }

    /** Složena sekcija: preporuka pesama čiji se žanr nalazi u nazivu neke korisnikove plejliste
     *  (Mongo plejliste + Neo4j IN_GENRE), bez onih koje su već u njegovim plejlistama. */
    @GetMapping("/genre-recommendations/{userId}")
    public List<GenrePlaylistRecommendation> genreRecommendations(@PathVariable String userId) {
        return genreRecommendationService.recommendFor(userId);
    }

    /** Složena sekcija: top N korisnika čije plejliste sadrže najpopularnije pesme (Mongo playCount + Neo4j LISTENED). */
    @GetMapping("/top-users")
    public List<TopUser> topUsers(@RequestParam(defaultValue = "3") int limit) {
        return topUsersService.topByPlaylistPopularity(limit);
    }

    @GetMapping("/songs")
    public List<SongRow> songsByGenre(@RequestParam(required = false) String genre) {
        return songReportService.byGenre(genre);
    }

    @GetMapping("/songs/by-duration")
    public List<SongRow> songsByDuration(
            @RequestParam(defaultValue = "0") int min,
            @RequestParam(defaultValue = "" + Integer.MAX_VALUE) int max) {
        return songReportService.byDuration(min, max);
    }
}
