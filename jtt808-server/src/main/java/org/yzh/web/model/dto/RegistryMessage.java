package org.yzh.web.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class RegistryMessage
{
    private Integer protocolVersion;
    private String deviceModel;
    private String deviceId;
    private String simNumber;
    private String plateNo;
}
