package org.yzh.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.yzh.protocol.t1078.T9101;
import org.yzh.protocol.t1078.T9102;
import org.yzh.protocol.t808.T0001;
import org.yzh.web.config.JTProperties;
import org.yzh.web.endpoint.MessageManager;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JT1078ControllerVideo360Test {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageManager messageManager = mock(MessageManager.class);
    private JT1078Controller controller;

    @BeforeEach
    void setUp() {
        controller = new JT1078Controller(messageManager, new JTProperties(), objectMapper);
    }

    @Test
    void start360StreamOverridesChannelAndDefaultsMissingMediaType() throws Exception {
        T0001 response = new T0001();
        when(messageManager.request(org.mockito.ArgumentMatchers.any(T9101.class), eq(T0001.class)))
                .thenReturn(Mono.just(response));
        JsonNode body = objectMapper.readTree("""
                {
                  "clientId": "terminal-1",
                  "ip": "192.168.1.10",
                  "tcpPort": 9100,
                  "udpPort": 9101,
                  "channelNo": 1,
                  "streamType": 1
                }
                """);

        T0001 actual = controller.T9101Video360("left", body).block();

        ArgumentCaptor<T9101> captor = ArgumentCaptor.forClass(T9101.class);
        verify(messageManager).request(captor.capture(), eq(T0001.class));
        T9101 request = captor.getValue();
        assertSame(response, actual);
        assertEquals(244, request.getChannelNo());
        assertEquals(1, request.getMediaType());
        assertEquals("192.168.1.10", request.getIp());
        assertEquals(9100, request.getTcpPort());
        assertEquals(9101, request.getUdpPort());
        assertEquals(1, request.getStreamType());
    }

    @Test
    void start360StreamPreservesExplicitMediaType() throws Exception {
        when(messageManager.request(org.mockito.ArgumentMatchers.any(T9101.class), eq(T0001.class)))
                .thenReturn(Mono.just(new T0001()));
        JsonNode body = objectMapper.readTree("""
                {
                  "clientId": "terminal-1",
                  "channelNo": 1,
                  "mediaType": 0
                }
                """);

        controller.T9101Video360("panorama", body).block();

        ArgumentCaptor<T9101> captor = ArgumentCaptor.forClass(T9101.class);
        verify(messageManager).request(captor.capture(), eq(T0001.class));
        assertEquals(240, captor.getValue().getChannelNo());
        assertEquals(0, captor.getValue().getMediaType());
    }

    @Test
    void control360StreamOverridesChannelAndPreservesControlFields() {
        when(messageManager.request(org.mockito.ArgumentMatchers.any(T9102.class), eq(T0001.class)))
                .thenReturn(Mono.just(new T0001()));
        T9102 body = new T9102()
                .setChannelNo(1)
                .setCommand(1)
                .setCloseType(2)
                .setStreamType(1);

        controller.T9102Video360("keep-rotation", body).block();

        ArgumentCaptor<T9102> captor = ArgumentCaptor.forClass(T9102.class);
        verify(messageManager).request(captor.capture(), eq(T0001.class));
        T9102 request = captor.getValue();
        assertEquals(246, request.getChannelNo());
        assertEquals(1, request.getCommand());
        assertEquals(2, request.getCloseType());
        assertEquals(1, request.getStreamType());
    }
}
