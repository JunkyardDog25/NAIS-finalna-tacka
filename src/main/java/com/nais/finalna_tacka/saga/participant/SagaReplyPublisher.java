package com.nais.finalna_tacka.saga.participant;

import com.nais.finalna_tacka.config.RabbitConfig;
import com.nais.finalna_tacka.saga.messages.SagaReply;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs a participant step and sends its {@link SagaReply} to the "saga.replies" queue:
 * success when the step completes, failure (with the error message) when it throws.
 * Centralizes the try/catch + reply pattern shared by every participant handler.
 */
@Component
@RequiredArgsConstructor
public class SagaReplyPublisher {

    private static final Logger log = LoggerFactory.getLogger(SagaReplyPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public void runAndReply(String sagaId, String participant, String operation, Runnable step) {
        try {
            step.run();
            send(sagaId, participant, operation, true, null);
        } catch (Exception e) {
            log.error("Saga {} {}/{} failed: {}", sagaId, participant, operation, e.getMessage());
            send(sagaId, participant, operation, false, e.getMessage());
        }
    }

    private void send(String sagaId, String participant, String operation, boolean success, String reason) {
        rabbitTemplate.convertAndSend(RabbitConfig.SAGA_REPLIES_QUEUE,
                new SagaReply(sagaId, participant, operation, success, reason));
    }
}
