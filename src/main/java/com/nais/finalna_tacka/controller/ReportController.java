package com.nais.finalna_tacka.controller;

import com.nais.finalna_tacka.dto.SongRecommendation;
import com.nais.finalna_tacka.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST izveštaji za Grafana Infinity datasource. Složena sekcija: CF preporuke iz Neo4j grafa.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final RecommendationService recommendationService;

    @GetMapping("/recommendations/{userId}")
    public List<SongRecommendation> recommendations(@PathVariable String userId) {
        return recommendationService.recommendFor(userId);
    }
}
