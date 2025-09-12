package org.yzh.web.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig
{

    // Exchange names
    public static final String CONTROL_EXCHANGE = "jt808.control.exchange";
    public static final String MEDIA_EXCHANGE = "jt1078.media.exchange";

    // Queue names
    public static final String CONTROL_QUEUE_BACKEND = "jt808.control.backend.queue";
    public static final String MEDIA_QUEUE_BACKEND = "jt1078.media.backend.queue";

    // Routing keys (topic pattern)
    public static final String CONTROL_ROUTING_KEY = "*.gps";        // receive all gps
    public static final String MEDIA_ROUTING_KEY = "*.media.*";    // receive all media events

    // --- Control ---
    @Bean
    public Queue controlBackendQueue()
    {
        return QueueBuilder.durable(CONTROL_QUEUE_BACKEND).build();
    }

    @Bean
    public TopicExchange controlExchange()
    {
        return ExchangeBuilder.topicExchange(CONTROL_EXCHANGE).durable(true).build();
    }

    @Bean
    public Binding controlBinding(Queue controlBackendQueue, TopicExchange controlExchange)
    {
        return BindingBuilder
                .bind(controlBackendQueue)
                .to(controlExchange)
                .with(CONTROL_ROUTING_KEY);
    }

    // --- Media ---
    @Bean
    public Queue mediaBackendQueue()
    {
        return QueueBuilder.durable(MEDIA_QUEUE_BACKEND).build();
    }

    @Bean
    public TopicExchange mediaExchange()
    {
        return ExchangeBuilder.topicExchange(MEDIA_EXCHANGE).durable(true).build();
    }

    @Bean
    public Binding mediaBinding(Queue mediaBackendQueue, TopicExchange mediaExchange)
    {
        return BindingBuilder
                .bind(mediaBackendQueue)
                .to(mediaExchange)
                .with(MEDIA_ROUTING_KEY);
    }
}
