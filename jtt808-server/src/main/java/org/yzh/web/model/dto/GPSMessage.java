package org.yzh.web.model.dto;

import java.time.LocalDateTime;

public record GPSMessage(
        String simNumber,
        double longitude,
        double latitude,
        LocalDateTime deviceTime)
{
}
