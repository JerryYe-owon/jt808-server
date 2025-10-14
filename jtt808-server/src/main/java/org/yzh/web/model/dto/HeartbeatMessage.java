package org.yzh.web.model.dto;

public record HeartbeatMessage(
        String deviceId,
        Boolean online,
        Long lastHeartbeatTime,
        Long timestamp)
{
}
