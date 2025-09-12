package org.yzh.web.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class GPSMessage
{
    private String simCardNo;
    private double longitude;
    private double latitude;
    private LocalDateTime deviceTime;
}
