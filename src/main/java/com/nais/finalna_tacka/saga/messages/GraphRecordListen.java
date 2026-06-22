package com.nais.finalna_tacka.saga.messages;

/**
 * Komanda: orkestrator -> Graph participant. Upsert LISTENED relacije za korisnika/pesmu.
 * Forward korak RECORD_LISTEN sage (graf je terminalni korak).
 */
public record GraphRecordListen(String sagaId, String userId, String songId) {
}
