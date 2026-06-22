package com.nais.finalna_tacka.saga.state;

/**
 * Payload za {@link SagaType#RECORD_LISTEN}: povezuje slušanje pesme od strane korisnika
 * između MongoDB-a (playCount) i Neo4j-a (LISTENED relacija).
 */
public record ListenPayload(String userId, String songId) {
}
