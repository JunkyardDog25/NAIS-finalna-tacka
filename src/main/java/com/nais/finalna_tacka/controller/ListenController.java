package com.nais.finalna_tacka.controller;

import com.nais.finalna_tacka.saga.orchestrator.SagaOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Pokreće RECORD_LISTEN sage. Vraća odmah sa sagaId; pozivaj
 * {@code GET /api/sagas/{sagaId}} da pratiš napredovanje statusa.
 */
@RestController
@RequiredArgsConstructor
public class ListenController {

    private final SagaOrchestrator orchestrator;

    @PostMapping("/api/users/{userId}/listens/{songId}")
    public ResponseEntity<Map<String, String>> recordListen(
            @PathVariable String userId,
            @PathVariable String songId) {
        String sagaId = orchestrator.startRecordListen(userId, songId);
        return ResponseEntity.accepted().body(Map.of("sagaId", sagaId));
    }
}
