package org.yzh.web.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class DeviceSessionManager
{

    // Key: Device ID (terminal ID)
    private final ConcurrentMap<String, Long> heartbeatMap = new ConcurrentHashMap<>();

    public void updateHeartbeat(String deviceId)
    {
        heartbeatMap.put(deviceId, System.currentTimeMillis());
    }

    public ConcurrentMap<String, Long> getHeartbeatMap()
    {
        return heartbeatMap;
    }
}

