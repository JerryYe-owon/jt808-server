package org.yzh.web.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig
{

    // --- Exchange names ---
    public static final String CONTROL_EXCHANGE = "jt808.control.exchange";
    public static final String MEDIA_EXCHANGE = "jt1078.media.exchange";
    public static final String REGISTRY_EXCHANGE = "device.registry.exchange";

    // --- Queue names ---
    public static final String CONTROL_QUEUE_BACKEND = "jt808.control.backend.queue";
    public static final String MEDIA_QUEUE_BACKEND = "jt1078.media.backend.queue";
    public static final String REGISTRY_QUEUE_BACKEND = "device.registry.backend.queue";
    public static final String HEARTBEAT_QUEUE_BACKEND = "device.heartbeat.backend.queue";
    public static final String RESPONSE_QUEUE_BACKEND = "device.response.backend.queue";

    // --- Routing keys (topic pattern) ---
    public static final String GPS_ROUTING_KEY = "*.gps";                    // GPS updates
    public static final String CONTROL_ROUTING_KEY = "*.control";            // Backend → Gateway commands
    public static final String MEDIA_ROUTING_KEY = "*.media.*";              // Example: VEH12345.media.ch1
    public static final String MEDIA_START_EVENT = "media.start";

    // --- Device Registry routing keys ---
    public static final String DEVICE_REGISTER_EVENT = "device.register";
    public static final String DEVICE_AUTH_EVENT = "device.auth";
    public static final String DEVICE_DISCONNECT_EVENT = "device.disconnect";  // TCP disconnect
    public static final String HEARTBEAT_EVENT = "device.heartbeat";
    public static final String GENERAL_STATUS_CHANGE = "device.status";    // General status change (fallback)

    public static final String DEVICE_RESPONSE = "device.response.*";

    // === DEVICE REGISTRY ===
    @Bean
    public Queue registryBackendQueue()
    {
        return QueueBuilder.durable(REGISTRY_QUEUE_BACKEND).build();
    }

    @Bean
    public TopicExchange registryExchange()
    {
        return ExchangeBuilder.topicExchange(REGISTRY_EXCHANGE).durable(true).build();
    }

    @Bean
    public Binding deviceRegisterBinding(Queue registryBackendQueue, TopicExchange registryExchange)
    {
        return BindingBuilder
                .bind(registryBackendQueue)
                .to(registryExchange)
                .with(DEVICE_REGISTER_EVENT);
    }

    @Bean
    public Binding deviceAuthBinding(Queue registryBackendQueue, TopicExchange registryExchange)
    {
        return BindingBuilder
                .bind(registryBackendQueue)
                .to(registryExchange)
                .with(DEVICE_AUTH_EVENT);
    }

    @Bean
    public Binding deviceDisconnectBinding(Queue registryBackendQueue, TopicExchange registryExchange)
    {
        return BindingBuilder
                .bind(registryBackendQueue)
                .to(registryExchange)
                .with(DEVICE_DISCONNECT_EVENT);
    }

    @Bean
    public Binding deviceStatusBinding(Queue registryBackendQueue, TopicExchange registryExchange)
    {
        return BindingBuilder
                .bind(registryBackendQueue)
                .to(registryExchange)
                .with(GENERAL_STATUS_CHANGE);
    }

    /**
     * General binding to receive all device-related events (for monitoring or logging)
     * Pattern: *.device.*
     */
    @Bean
    public Binding allDeviceEventsBinding(Queue registryBackendQueue, TopicExchange registryExchange)
    {
        return BindingBuilder
                .bind(registryBackendQueue)
                .to(registryExchange)
                .with("*.device.*");
    }

    // === DEVICE HEARTBEAT ===
    @Bean
    public Queue heartbeatQueue()
    {
        return QueueBuilder.durable(HEARTBEAT_QUEUE_BACKEND).build();
    }

    @Bean
    public Binding deviceHeartbeatBinding(Queue heartbeatQueue, TopicExchange registryExchange)
    {
        return BindingBuilder
                .bind(heartbeatQueue)
                .to(registryExchange)
                .with(HEARTBEAT_EVENT);
    }

    // === RESPONSE ===
    @Bean
    public Queue responseBackendQueue()
    {
        return QueueBuilder.durable(RESPONSE_QUEUE_BACKEND).build();
    }

    @Bean
    public Binding deviceResponseBinding(Queue responseBackendQueue, TopicExchange controlExchange)
    {
        return BindingBuilder
                .bind(responseBackendQueue)
                .to(controlExchange)
                .with(DEVICE_RESPONSE);
    }

    // === CONTROL ===
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
                .with(GPS_ROUTING_KEY);
    }

    // === MEDIA ===
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
