package com.nais.finalna_tacka.saga.messages;

/**
 * Single generic reply: participant -> orchestrator, sent to the "saga.replies" queue.
 *
 * @param sagaId      correlates the reply with its {@code SagaState}
 * @param participant who replied (e.g. "mongo" / "graph"), for logging/clarity
 * @param operation   the command that was handled (e.g. "MongoCreateSong")
 * @param success     whether the participant's step succeeded
 * @param reason      failure detail when {@code success} is false (else may be null/empty)
 */
public record SagaReply(String sagaId, String participant, String operation, boolean success, String reason) {
}
