package org.yzh.web.model.dto;

public record AuthMessage(
        String imei,
        String simNumber,
        String softwareVersion
)
{
}
