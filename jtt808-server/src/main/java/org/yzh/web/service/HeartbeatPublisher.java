package org.yzh.web.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.yzh.web.model.dto.HeartbeatMessage;

import static org.yzh.web.config.RabbitMQConfig.HEARTBEAT_EVENT;
import static org.yzh.web.config.RabbitMQConfig.REGISTRY_EXCHANGE;

@Slf4j
@Component
public class HeartbeatPublisher
{
    private static final long TIMEOUT_MS = 120_000; // 2 minutes

    private final DeviceSessionManager sessionManager;
    private final RabbitTemplate rabbitTemplate;

    public HeartbeatPublisher(DeviceSessionManager sessionManager, RabbitTemplate rabbitTemplate)
    {
        this.sessionManager = sessionManager;
        this.rabbitTemplate = rabbitTemplate;
    }

    // Set TTL for heartbeat message
    MessagePostProcessor messagePostProcessor = message -> {
        message.getMessageProperties().setExpiration(String.valueOf(TIMEOUT_MS));
        return message;
    };

    @Scheduled(fixedDelay = 30_000) // every 30 seconds
    public void publishHeartbeat()
    {
        long now = System.currentTimeMillis();

        sessionManager.getHeartbeatMap().forEach((deviceId, lastTime) -> {
            boolean online = (now - lastTime) <= TIMEOUT_MS;
            HeartbeatMessage message = new HeartbeatMessage(deviceId, online, lastTime, now);
            rabbitTemplate.convertAndSend(REGISTRY_EXCHANGE, HEARTBEAT_EVENT, message, messagePostProcessor);
//            log.info("Publish heartbeat, {}, {}", HEARTBEAT_EVENT, message);
        });
    }

    @Scheduled(fixedDelay = 300_000) // every 5 minutes
    public void cleanExpiredDevices()
    {
        long now = System.currentTimeMillis();
        sessionManager.getHeartbeatMap().entrySet().removeIf(entry -> {
            long lastTime = entry.getValue();
            boolean expired = (now - lastTime) > (TIMEOUT_MS * 3); // 3x timeout
            if (expired)
            {
                log.info("Removing expired device {}", entry.getKey());
            }
            return expired;
        });
    }
}
