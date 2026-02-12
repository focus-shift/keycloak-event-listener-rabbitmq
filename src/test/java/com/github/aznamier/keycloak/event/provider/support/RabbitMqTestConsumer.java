package com.github.aznamier.keycloak.event.provider.support;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;

/**
 * Shared RabbitMQ connection for integration tests.
 * <p>
 * Create one instance per test class ({@code @BeforeAll}) and call
 * {@link #subscribe(String)} in each test to get an isolated
 * {@link Subscription} bound to a precise routing key.
 */
public class RabbitMqTestConsumer implements Closeable {

    public record Message(String routingKey, AMQP.BasicProperties properties, String body) {}

    private final Connection connection;
    private final String exchange;

    public RabbitMqTestConsumer(RabbitMQContainer container, String exchange) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(container.getAmqpUrl());
        factory.setUsername(container.getAdminUsername());
        factory.setPassword(container.getAdminPassword());

        this.connection = factory.newConnection();
        this.exchange = exchange;
    }

    public Subscription subscribe(String bindingKey) throws IOException {
        Channel channel = connection.createChannel();
        String queue = channel.queueDeclare().getQueue();
        channel.queueBind(queue, exchange, bindingKey);

        BlockingQueue<Message> messages = new LinkedBlockingQueue<>();
        channel.basicConsume(queue, true, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) {
                messages.add(new Message(
                        envelope.getRoutingKey(),
                        properties,
                        new String(body, StandardCharsets.UTF_8)
                ));
            }
        });

        return new Subscription(channel, messages);
    }

    @Override
    public void close() throws IOException {
        if (connection.isOpen()) {
            connection.close();
        }
    }

    public static class Subscription implements Closeable {

        private final Channel channel;
        private final BlockingQueue<Message> messages;

        private Subscription(Channel channel, BlockingQueue<Message> messages) {
            this.channel = channel;
            this.messages = messages;
        }

        public List<Message> receivedMessages() {
            return new ArrayList<>(messages);
        }

        @Override
        public void close() throws IOException {
            try {
                if (channel.isOpen()) {
                    channel.close();
                }
            } catch (TimeoutException e) {
                throw new IOException(e);
            }
        }
    }
}
