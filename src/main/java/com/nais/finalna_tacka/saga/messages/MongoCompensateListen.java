package com.nais.finalna_tacka.saga.messages;

/**
 * Payload za {@link SagaType#RECORD_LISTEN}: povezuje slušanje pesme od strane korisnika
 * između MongoDB-a (playCount) i Neo4j-a (LISTENED relacija).
 */
public record MongoCompensateListen(String sagaId, String userId, String songId) {
}
