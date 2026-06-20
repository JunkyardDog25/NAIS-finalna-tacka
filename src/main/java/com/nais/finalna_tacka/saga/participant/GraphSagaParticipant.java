package com.nais.finalna_tacka.saga.participant;

import com.nais.finalna_tacka.config.RabbitConfig;
import com.nais.finalna_tacka.saga.messages.GraphCreateSong;
import com.nais.finalna_tacka.saga.messages.GraphDeleteSong;
import com.nais.finalna_tacka.saga.messages.SagaReply;
import com.nais.finalna_tacka.service.SongGraphService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Neo4j participant: consumes commands from "graph.commands" and replies to "saga.replies".
 *
 * <p>Class-level {@code @RabbitListener} + {@code @RabbitHandler} dispatches each message to
 * the handler matching its payload type (the JSON converter carries the type in a header),
 * so create and delete commands on the one queue go to the right method.</p>
 *
 * <p>Consumers must be idempotent (RabbitMQ delivers at least once): use MERGE-style
 * creates and treat deleting a missing node as a no-op.</p>
 */
@Component
@RequiredArgsConstructor
@RabbitListener(queues = RabbitConfig.GRAPH_COMMANDS_QUEUE)
public class GraphSagaParticipant {

    private static final Logger log = LoggerFactory.getLogger(GraphSagaParticipant.class);
    private static final String PARTICIPANT = "graph";

    private final RabbitTemplate rabbitTemplate;
    private final SongGraphService songGraphService;

    @RabbitHandler
    public void onCreate(GraphCreateSong cmd) {
        try {
            songGraphService.createSong(cmd.payload());
            reply(cmd.sagaId(), "create", true, null);
        } catch (Exception e) {
            log.error("Saga {} GraphCreateSong failed: {}", cmd.sagaId(), e.getMessage());
            reply(cmd.sagaId(), "create", false, e.getMessage());
        }
    }

    @RabbitHandler
    public void onDelete(GraphDeleteSong cmd) {
        try {
            songGraphService.deleteSong(cmd.songId());
            reply(cmd.sagaId(), "delete", true, null);
        } catch (Exception e) {
            log.error("Saga {} GraphDeleteSong failed: {}", cmd.sagaId(), e.getMessage());
            reply(cmd.sagaId(), "delete", false, e.getMessage());
        }
    }

    private void reply(String sagaId, String operation, boolean success, String reason) {
        rabbitTemplate.convertAndSend(RabbitConfig.SAGA_REPLIES_QUEUE,
                new SagaReply(sagaId, PARTICIPANT, operation, success, reason));
    }
}
