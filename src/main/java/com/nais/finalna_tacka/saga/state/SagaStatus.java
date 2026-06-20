package com.nais.finalna_tacka.saga.state;

/**
 * Lifecycle of a saga instance. COMPLETED and FAILED are terminal: replies that arrive
 * for a saga already in a terminal state are ignored (idempotency under at-least-once
 * delivery).
 */
public enum SagaStatus {
    STARTED,
    MONGO_DONE,
    GRAPH_DONE,
    COMPLETED,
    COMPENSATING,
    FAILED
}
