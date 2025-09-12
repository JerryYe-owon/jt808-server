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
    public Integer version;
    public String deviceId;
    public String simNumber;
    public String event;
}
