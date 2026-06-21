package com.nais.finalna_tacka.saga.messages;

/**
 * Komanda: orkestrator -> Mongo participant. Inkrementira playCount na dokumentu pesme.
 * Forward korak RECORD_LISTEN sage.
 */
public record MongoRecordListen(String sagaId, String userId, String songId) {
}
