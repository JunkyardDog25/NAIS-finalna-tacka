package com.nais.finalna_tacka.saga.participant;

import com.nais.finalna_tacka.config.RabbitConfig;
import com.nais.finalna_tacka.saga.messages.GraphCreateSong;
import com.nais.finalna_tacka.saga.messages.GraphDeleteSong;
import com.nais.finalna_tacka.service.SongGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Neo4j participant: consumes commands from "graph.commands" and replies to "saga.replies"
 * (via {@link SagaReplyPublisher}).
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

    private static final String PARTICIPANT = "graph";

    private final SongGraphService songGraphService;
    private final SagaReplyPublisher replyPublisher;

    @RabbitHandler
    public void onCreate(GraphCreateSong cmd) {
        replyPublisher.runAndReply(cmd.sagaId(), PARTICIPANT, "create",
                () -> songGraphService.createSong(cmd.payload()));
    }

    @RabbitHandler
    public void onDelete(GraphDeleteSong cmd) {
        replyPublisher.runAndReply(cmd.sagaId(), PARTICIPANT, "delete",
                () -> songGraphService.deleteSong(cmd.songId()));
    }
}
