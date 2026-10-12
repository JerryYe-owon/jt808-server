package org.yzh.web.endpoint;

import io.github.yezhihao.netmc.session.Session;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.yzh.web.model.dto.DisconnectMessage;
import org.yzh.web.service.DeviceSessionManager;
import org.yzh.web.service.HeartbeatPublisher;
import org.yzh.web.service.MessageProducer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.yzh.web.config.RabbitMQConfig.DEVICE_DISCONNECT_EVENT;

class JTSessionListenerTest
{
    private final DeviceSessionManager manager = new DeviceSessionManager();
    private final MessageProducer producer = mock(MessageProducer.class);
    private final JTSessionListener listener = new JTSessionListener(manager, producer);

    private Session session(String sim)
    {
        Session session = mock(Session.class);
        when(session.getClientId()).thenReturn(sim);
        return session;
    }

    @Test
    void closePublishesOneDisconnectAndStopsHeartbeats()
    {
        Session session = session("001234567890");
        listener.sessionRegistered(session);
        manager.updateHeartbeat(session);
        long before = System.currentTimeMillis();
        listener.sessionDestroyed(session);
        listener.sessionDestroyed(session);

        ArgumentCaptor<DisconnectMessage> event = ArgumentCaptor.forClass(DisconnectMessage.class);
        verify(producer).sendRegistryEvent(eq(DEVICE_DISCONNECT_EVENT), event.capture());
        assertEquals("001234567890", event.getValue().simNumber());
        assertTrue(event.getValue().timestamp() >= before);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        new HeartbeatPublisher(manager, rabbit).publishHeartbeat();
        verifyNoInteractions(rabbit);
    }

    @Test
    void ignoresUnidentifiedAndNeverRegisteredConnections()
    {
        listener.sessionRegistered(session(null));
        listener.sessionDestroyed(session(null));
        listener.sessionDestroyed(session("001234567890"));
        verifyNoInteractions(producer);
    }

    @Test
    void supersededConnectionCannotDisconnectOrHeartbeatForReplacement()
    {
        Session old = session("001234567890");
        Session replacement = session("001234567890");
        listener.sessionRegistered(old);
        manager.updateHeartbeat(old);
        listener.sessionRegistered(replacement);
        manager.updateHeartbeat(old);
        listener.sessionDestroyed(old);
        List<Long> heartbeats = new ArrayList<>();
        manager.forEachHeartbeat((sim, time) -> heartbeats.add(time));
        assertTrue(heartbeats.isEmpty());
        verifyNoInteractions(producer);
        manager.updateHeartbeat(replacement);
        manager.forEachHeartbeat((sim, time) -> heartbeats.add(time));
        assertEquals(1, heartbeats.size());
        listener.sessionDestroyed(replacement);
        verify(producer).sendRegistryEvent(eq(DEVICE_DISCONNECT_EVENT), any(DisconnectMessage.class));
    }

    @Test
    void registeringSameSessionAgainRetainsHeartbeat()
    {
        Session session = session("001234567890");
        listener.sessionRegistered(session);
        manager.updateHeartbeat(session);
        listener.sessionRegistered(session);
        List<Long> heartbeats = new ArrayList<>();
        manager.forEachHeartbeat((sim, time) -> heartbeats.add(time));
        assertEquals(1, heartbeats.size());
    }

    @Test
    void expiredHeartbeatStillAllowsDisconnectAndFreshHeartbeat()
    {
        Session session = session("001234567890");
        listener.sessionRegistered(session);
        manager.updateHeartbeat(session);
        manager.clearExpiredHeartbeats(Long.MAX_VALUE, 360_000);
        manager.forEachHeartbeat((sim, time) -> fail("Expired heartbeat published"));
        manager.updateHeartbeat(session);
        List<Long> heartbeats = new ArrayList<>();
        manager.forEachHeartbeat((sim, time) -> heartbeats.add(time));
        assertEquals(1, heartbeats.size());
        manager.clearExpiredHeartbeats(Long.MAX_VALUE, 360_000);
        listener.sessionDestroyed(session);
        verify(producer).sendRegistryEvent(eq(DEVICE_DISCONNECT_EVENT), any(DisconnectMessage.class));
    }

    @Test
    void brokerFailureStillClearsDisconnectedSession()
    {
        Session session = session("001234567890");
        listener.sessionRegistered(session);
        manager.updateHeartbeat(session);
        doThrow(new RuntimeException("Broker unavailable")).when(producer)
            .sendRegistryEvent(eq(DEVICE_DISCONNECT_EVENT), any());
        assertDoesNotThrow(() -> listener.sessionDestroyed(session));
        manager.forEachHeartbeat((sim, time) -> fail("Disconnected heartbeat retained"));
        listener.sessionDestroyed(session);
        verify(producer, times(1)).sendRegistryEvent(eq(DEVICE_DISCONNECT_EVENT), any());
    }

    @Test
    void disconnectWaitsForInFlightPublicationThenPreventsFurtherPublication() throws Exception
    {
        Session session = session("001234567890");
        listener.sessionRegistered(session);
        manager.updateHeartbeat(session);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        doAnswer(invocation -> { order.add("disconnect"); return null; }).when(producer)
            .sendRegistryEvent(eq(DEVICE_DISCONNECT_EVENT), any());
        try (var executor = Executors.newFixedThreadPool(2))
        {
            var heartbeat = executor.submit(() -> manager.forEachHeartbeat((sim, time) -> {
                entered.countDown();
                try
                {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                order.add("heartbeat");
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var disconnect = executor.submit(() -> listener.sessionDestroyed(session));
            release.countDown();
            heartbeat.get(5, TimeUnit.SECONDS);
            disconnect.get(5, TimeUnit.SECONDS);
        }
        assertEquals(List.of("heartbeat", "disconnect"), order);
        manager.forEachHeartbeat((sim, time) -> fail("Post-disconnect publication"));
    }

    @Test
    void logoutInvalidatesSession()
    {
        Session session = session("001234567890");
        new JT808Endpoint(null, producer, manager).T0003(null, session);
        verify(session).invalidate();
    }
}
