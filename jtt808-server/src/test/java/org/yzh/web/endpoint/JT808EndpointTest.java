package org.yzh.web.endpoint;

import io.github.yezhihao.netmc.session.Session;
import io.github.yezhihao.netmc.session.SessionManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.yzh.protocol.basics.JTMessage;
import org.yzh.protocol.t808.T0001;
import org.yzh.protocol.t808.T0100;
import org.yzh.protocol.t808.T0102;
import org.yzh.web.model.dto.AuthMessage;
import org.yzh.web.model.dto.HeartbeatMessage;
import org.yzh.web.service.DeviceSessionManager;
import org.yzh.web.service.HeartbeatPublisher;
import org.yzh.web.service.MessageProducer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.yzh.web.config.RabbitMQConfig.*;

class JT808EndpointTest
{
    private final SessionManager registry = mock(SessionManager.class);
    private final DeviceSessionManager manager;
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final MessageProducer producer = mock(MessageProducer.class);
    private final HeartbeatPublisher publisher;
    private final JT808Endpoint endpoint;

    JT808EndpointTest()
    {
        var factory = new DefaultListableBeanFactory();
        factory.registerSingleton("sessionManager", registry);
        manager = new DeviceSessionManager(factory.getBeanProvider(SessionManager.class));
        publisher = new HeartbeatPublisher(manager, rabbit);
        endpoint = new JT808Endpoint(null, producer, manager, publisher);
    }

    private Session session()
    {
        Session session = mock(Session.class);
        when(session.getClientId()).thenReturn("001234567890");
        when(session.getId()).thenReturn("001234567890");
        doAnswer(invocation -> {
            when(registry.get(session.getId())).thenReturn(session);
            manager.register(session);
            return null;
        }).when(session).register(any(JTMessage.class));
        return session;
    }

    private T0102 authMessage()
    {
        T0102 message = new T0102();
        message.setClientId("001234567890");
        message.setToken("device,plate");
        return message;
    }

    private HeartbeatMessage publishedHeartbeat(int count)
    {
        ArgumentCaptor<HeartbeatMessage> message = ArgumentCaptor.forClass(HeartbeatMessage.class);
        verify(rabbit, times(count)).convertAndSend(eq(REGISTRY_EXCHANGE), eq(HEARTBEAT_EVENT),
            message.capture(), any(MessagePostProcessor.class));
        return message.getValue();
    }

    @Test
    void registrationAndAuthenticationWaitForFirstRealHeartbeat()
    {
        Session session = session();
        T0100 registration = new T0100();
        registration.setClientId(session.getClientId());
        endpoint.T0100(registration, session);
        endpoint.T0002(null, session);
        publisher.publishHeartbeat();
        verifyNoInteractions(rabbit);

        assertEquals(T0001.Success, endpoint.T0102(authMessage(), session).getResultCode());
        publisher.publishHeartbeat();
        verifyNoInteractions(rabbit);
        verify(producer).sendRegistryEvent(eq(DEVICE_AUTH_EVENT), any(AuthMessage.class));

        long before = System.currentTimeMillis();
        endpoint.T0002(null, session);
        HeartbeatMessage first = publishedHeartbeat(1);
        assertEquals(session.getClientId(), first.deviceId());
        assertTrue(first.online());
        assertTrue(first.lastHeartbeatTime() >= before);
        assertEquals(first.lastHeartbeatTime(), first.timestamp());
        ArgumentCaptor<MessagePostProcessor> ttl = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbit).convertAndSend(eq(REGISTRY_EXCHANGE), eq(HEARTBEAT_EVENT), any(HeartbeatMessage.class), ttl.capture());
        Message processed = ttl.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertEquals("120000", processed.getMessageProperties().getExpiration());

        endpoint.T0002(null, session);
        publishedHeartbeat(1);
        publisher.publishHeartbeat();
        HeartbeatMessage scheduled = publishedHeartbeat(2);
        assertTrue(scheduled.online());
        assertTrue(scheduled.lastHeartbeatTime() >= first.lastHeartbeatTime());
        assertTrue(scheduled.timestamp() >= scheduled.lastHeartbeatTime());
    }

    @Test
    void failedAuthDoesNotEnableHeartbeatTracking()
    {
        Session session = session();
        doThrow(new RuntimeException("Auth metadata publication failed")).when(producer)
            .sendRegistryEvent(eq(DEVICE_AUTH_EVENT), any(AuthMessage.class));
        assertThrows(RuntimeException.class, () -> endpoint.T0102(authMessage(), session));
        endpoint.T0002(null, session);
        publisher.publishHeartbeat();
        verifyNoInteractions(rabbit);
    }

    @Test
    void replacementRequiresAuthenticationAndOldSessionCannotReportOnline()
    {
        Session old = session();
        endpoint.T0102(authMessage(), old);
        Session replacement = session();
        replacement.register(authMessage());
        manager.authenticate(old);
        endpoint.T0002(null, old);
        endpoint.T0002(null, replacement);
        publisher.publishHeartbeat();
        verifyNoInteractions(rabbit);

        endpoint.T0102(authMessage(), replacement);
        endpoint.T0002(null, replacement);
        publishedHeartbeat(1);
        manager.disconnect(replacement, () -> {});
        endpoint.T0002(null, replacement);
        publisher.publishHeartbeat();
        publishedHeartbeat(1);
    }

    @Test
    void authoritativeReplacementBlocksHeartbeatBeforeItsCallbackArrives()
    {
        Session old = session();
        endpoint.T0102(authMessage(), old);
        endpoint.T0002(null, old);
        Session replacement = session();
        when(registry.get(old.getId())).thenReturn(replacement);
        endpoint.T0002(null, old);
        publisher.publishHeartbeat();
        publishedHeartbeat(1);
    }

    @Test
    void repeatAuthPreservesHeartbeatAndTimeoutRecoveryPublishesImmediately()
    {
        Session session = session();
        endpoint.T0102(authMessage(), session);
        endpoint.T0002(null, session);
        endpoint.T0102(authMessage(), session);
        endpoint.T0002(null, session);
        publishedHeartbeat(1);

        Map<?, ?> states = (Map<?, ?>) ReflectionTestUtils.getField(manager, "sessions");
        assertNotNull(states);
        ReflectionTestUtils.setField(states.get(session.getClientId()), "lastHeartbeatTime",
            System.currentTimeMillis() - 120_001L);
        publisher.publishHeartbeat();
        assertFalse(publishedHeartbeat(2).online());
        endpoint.T0002(null, session);
        assertTrue(publishedHeartbeat(3).online());

        manager.clearExpiredHeartbeats(Long.MAX_VALUE, 360_000);
        endpoint.T0002(null, session);
        assertTrue(publishedHeartbeat(4).online());
    }

    @Test
    void firstHeartbeatBrokerFailureRetainsActivityForScheduledRetry()
    {
        Session session = session();
        endpoint.T0102(authMessage(), session);
        doThrow(new RuntimeException("Broker unavailable")).doNothing().when(rabbit)
            .convertAndSend(eq(REGISTRY_EXCHANGE), eq(HEARTBEAT_EVENT), any(HeartbeatMessage.class),
                any(MessagePostProcessor.class));
        assertDoesNotThrow(() -> endpoint.T0002(null, session));
        publisher.publishHeartbeat();
        HeartbeatMessage retried = publishedHeartbeat(2);
        assertTrue(retried.online());
        assertNotNull(retried.lastHeartbeatTime());
    }
}
