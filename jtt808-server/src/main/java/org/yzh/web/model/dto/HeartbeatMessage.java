package org.yzh.web.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class HeartbeatMessage
{
    private String deviceId;
    private Boolean online;
    private Long lastHeartbeatTime;
    private Long timestamp;
}
