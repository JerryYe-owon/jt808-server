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
     * Send a message of a specific vehicle and message type.
     *
     * @param vehicleId   vehicle identifier (e.g., "vehicle123")
     * @param messageType message type (e.g., "gps", "media.start", "media.stop")
     * @param payload     the actual message body (JSON)
     */
    public void sendMessage(String vehicleId, String messageType, Object payload)
    {
        String routingKey = vehicleId + "." + messageType; // e.g. vehicle123.media.start
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.CONTROL_EXCHANGE,
                routingKey,
                payload
        );
    }
}
