package org.yzh.web.model.dto;

import java.time.OffsetDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

public record GPSMessage(
        String simNumber,
        double longitude,
        double latitude,
        int altitude,
        int speed,
        int direction,
        @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime deviceTime)
{
}
