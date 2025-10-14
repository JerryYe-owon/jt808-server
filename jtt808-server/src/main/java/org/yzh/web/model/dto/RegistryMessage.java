package org.yzh.web.model.dto;

public record RegistryMessage(
        Integer protocolVersion,
        String deviceModel,
        String deviceId,
        String simNumber,
        String plateNo,
        String makerId)
{
}
