package org.yzh.web.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.yzh.web.config.RabbitMQConfig;

@Component
public class MessageProducer
{

    private final RabbitTemplate rabbitTemplate;

    public MessageProducer(RabbitTemplate rabbitTemplate)
    {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Send a control or media message for a specific vehicle.
     *
     * @param exchange    which exchange to publish (CONTROL_EXCHANGE or MEDIA_EXCHANGE)
     * @param simNo   vehicle identifier (e.g., "vehicle123")
     * @param messageType message type (e.g., "gps", "media.start", "media.stop")
     * @param payload     the actual message body (JSON or object)
     */
    public void sendMessage(String exchange, String simNo, String messageType, Object payload)
    {
        String routingKey = simNo + "." + messageType; // e.g., vehicle123.gps
        rabbitTemplate.convertAndSend(exchange, routingKey, payload);
    }

    public void sendMessage(String exchange, String routingKey, Object payload)
    {
        rabbitTemplate.convertAndSend(exchange, routingKey, payload);
    }

    /**
     * Send a device registry event.
     *
     * @param routingKey registry event type (e.g., "device.register", "device.heartbeat", "device.disconnect")
     * @param payload    the actual message body (JSON or object)
     */
    public void sendRegistryEvent(String routingKey, Object payload)
    {
        rabbitTemplate.convertAndSend(RabbitMQConfig.REGISTRY_EXCHANGE, routingKey, payload);
    }
}
