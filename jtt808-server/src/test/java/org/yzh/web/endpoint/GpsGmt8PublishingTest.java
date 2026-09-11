package org.yzh.web.endpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.yzh.protocol.t808.T0200;
import org.yzh.web.config.RabbitMQConfig;
import org.yzh.web.model.dto.GPSMessage;
import org.yzh.web.service.DeviceSessionManager;
import org.yzh.web.service.FileService;
import org.yzh.web.service.MessageProducer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GpsGmt8PublishingTest {
    @Test
    void publishesDecodedGmt8WallClockAsIsoStringInEveryJvmZone() throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            for (String zone : List.of("UTC", "Asia/Hong_Kong", "America/New_York")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                MessageProducer producer = mock(MessageProducer.class);
                JT808Endpoint endpoint = new JT808Endpoint(mock(FileService.class), producer, mock(DeviceSessionManager.class));
                T0200 packet = new T0200();
                packet.setClientId("123456789012");
                packet.setDeviceTime(LocalDateTime.parse("2026-09-10T09:30:00"));
                endpoint.T0200(List.of(packet));
                ArgumentCaptor<GPSMessage> message = ArgumentCaptor.forClass(GPSMessage.class);
                verify(producer).sendMessage(eq(RabbitMQConfig.CONTROL_EXCHANGE), eq("123456789012"), eq("gps"), message.capture());
                ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
                assertEquals("2026-09-10T09:30:00+08:00", mapper.readTree(mapper.writeValueAsString(message.getValue())).get("deviceTime").asText());
                assertEquals(Instant.parse("2026-09-10T01:30:00Z"), message.getValue().deviceTime().toInstant());
                assertEquals(LocalDateTime.parse("2026-09-10T09:30:00"), packet.getDeviceTime());
            }
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
