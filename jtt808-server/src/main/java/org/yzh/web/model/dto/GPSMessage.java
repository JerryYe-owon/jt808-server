package org.yzh.web.model.dto;

import java.time.LocalDateTime;

public record GPSMessage(
        String simNumber,
        double longitude,
        double latitude,
        int altitude,
        int speed,
        int direction,
        LocalDateTime deviceTime)
{
}
