package com.nais.finalna_tacka.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ wiring for the Saga orchestration.
 *
 * <p>Topology (one direct exchange, routing key == queue name):</p>
 * <pre>
 *   orchestrator --(MongoCreateSong/MongoDeleteSong)--> [mongo.commands] --> MongoSagaParticipant
 *   orchestrator --(GraphCreateSong/GraphDeleteSong)--> [graph.commands] --> GraphSagaParticipant
 *   participants --(SagaReply)-------------------------> [saga.replies] --> SagaOrchestrator
 * </pre>
 *
 * <p>All payloads travel as JSON via {@link JacksonJsonMessageConverter}, used by both the
 * sending {@link RabbitTemplate} and the listener container factory.</p>
 */
@Configuration
public class RabbitConfig {

    public static final String SAGA_EXCHANGE = "saga.exchange";

    public static final String MONGO_COMMANDS_QUEUE = "mongo.commands";
    public static final String GRAPH_COMMANDS_QUEUE = "graph.commands";
    public static final String SAGA_REPLIES_QUEUE = "saga.replies";

    // --- Connection (env vars with localhost defaults) ---

    @Bean
    public ConnectionFactory connectionFactory(
            @Value("${RABBITMQ_HOST:localhost}") String host,
            @Value("${RABBITMQ_PORT:5672}") int port,
            @Value("${RABBITMQ_USER:guest}") String username,
            @Value("${RABBITMQ_PASS:guest}") String password) {
        CachingConnectionFactory factory = new CachingConnectionFactory(host, port);
        factory.setUsername(username);
        factory.setPassword(password);
        return factory;
    }

    // --- Exchange ---

    @Bean
    public DirectExchange sagaExchange() {
        return new DirectExchange(SAGA_EXCHANGE);
    }

    // --- Queues ---

    @Bean
    public Queue mongoCommandsQueue() {
        return new Queue(MONGO_COMMANDS_QUEUE, true);
    }

    @Bean
    public Queue graphCommandsQueue() {
        return new Queue(GRAPH_COMMANDS_QUEUE, true);
    }

    @Bean
    public Queue sagaRepliesQueue() {
        return new Queue(SAGA_REPLIES_QUEUE, true);
    }

    // --- Bindings (routing key == queue name) ---

    @Bean
    public Binding mongoCommandsBinding() {
        return BindingBuilder.bind(mongoCommandsQueue()).to(sagaExchange()).with(MONGO_COMMANDS_QUEUE);
    }

    @Bean
    public Binding graphCommandsBinding() {
        return BindingBuilder.bind(graphCommandsQueue()).to(sagaExchange()).with(GRAPH_COMMANDS_QUEUE);
    }

    @Bean
    public Binding sagaRepliesBinding() {
        return BindingBuilder.bind(sagaRepliesQueue()).to(sagaExchange()).with(SAGA_REPLIES_QUEUE);
    }

    // --- JSON conversion (shared by template and listeners) ---

    @Bean
    public MessageConverter jsonMessageConverter() {
        // Trust our own message package so the converter will deserialize the command/reply
        // records carried in the "__TypeId__" header (default trusts only java.util/java.lang).
        return new JacksonJsonMessageConverter("com.nais.finalna_tacka.saga.messages");
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setExchange(SAGA_EXCHANGE);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        return factory;
    }
}
