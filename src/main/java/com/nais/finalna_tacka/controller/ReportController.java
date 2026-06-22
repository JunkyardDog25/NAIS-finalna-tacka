package com.nais.finalna_tacka.controller;

import com.nais.finalna_tacka.dto.SongRecommendation;
import com.nais.finalna_tacka.dto.SongRow;
import com.nais.finalna_tacka.service.RecommendationService;
import com.nais.finalna_tacka.service.SongReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST izveštaji za Grafana Infinity datasource.
 * Složena sekcija: CF preporuke iz Neo4j grafa.
 * Proste sekcije: tabele pesama čitane direktno iz MongoDB.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final RecommendationService recommendationService;
    private final SongReportService songReportService;

    @GetMapping("/recommendations/{userId}")
    public List<SongRecommendation> recommendations(@PathVariable String userId) {
        return recommendationService.recommendFor(userId);
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
