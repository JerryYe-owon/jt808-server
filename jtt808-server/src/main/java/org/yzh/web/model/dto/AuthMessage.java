package org.yzh.web.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class AuthMessage
{
    private String imei;
    private String simNumber;
    private String softwareVersion;
}
