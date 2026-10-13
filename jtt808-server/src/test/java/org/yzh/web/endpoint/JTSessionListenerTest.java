package org.yzh.web.endpoint;

import io.github.yezhihao.netmc.session.Session;
import io.github.yezhihao.netmc.session.SessionListener;
import io.github.yezhihao.netmc.session.SessionManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.yzh.web.model.dto.DisconnectMessage;
import org.yzh.web.model.dto.HeartbeatMessage;
import org.yzh.web.service.DeviceSessionManager;
import org.yzh.web.service.HeartbeatPublisher;
import org.yzh.web.service.MessageProducer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.yzh.web.config.RabbitMQConfig.DEVICE_DISCONNECT_EVENT;
import static org.yzh.web.config.RabbitMQConfig.HEARTBEAT_EVENT;
import static org.yzh.web.config.RabbitMQConfig.REGISTRY_EXCHANGE;

class JTSessionListenerTest
{
    private final SessionManager registry = mock(SessionManager.class);
    private final DeviceSessionManager manager = new DeviceSessionManager(provider(registry));
    private final MessageProducer producer = mock(MessageProducer.class);
    private final JTSessionListener listener = new JTSessionListener(manager, producer);

    private Session session(String sim)
    {
        Session session = mock(Session.class);
        when(session.getClientId()).thenReturn(sim);
        when(session.getId()).thenReturn(sim);
        return session;
    }

    private ObjectProvider<SessionManager> provider(SessionManager sessionManager)
    {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("sessionManager", sessionManager);
        return factory.getBeanProvider(SessionManager.class);
    }

    private void register(Session session)
    {
        when(registry.get(session.getId())).thenReturn(session);
        listener.sessionRegistered(session);
        manager.authenticate(session);
    }

    private void heartbeat(DeviceSessionManager tracker, Session session)
    {
        tracker.updateHeartbeat(session, 120_000, (sim, time) -> {});
    }

    @Test
    void closePublishesOneDisconnectAndStopsHeartbeats()
    {
        Session session = session("001234567890");
        register(session);
        heartbeat(manager, session);
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
        register(session(null));
        listener.sessionDestroyed(session(null));
        listener.sessionDestroyed(session("001234567890"));
        verifyNoInteractions(producer);
    }

    @Test
    void supersededConnectionCannotDisconnectOrHeartbeatForReplacement()
    {
        Session old = session("001234567890");
        Session replacement = session("001234567890");
        register(old);
        heartbeat(manager, old);
        register(replacement);
        heartbeat(manager, old);
        listener.sessionDestroyed(old);
        List<Long> heartbeats = new ArrayList<>();
        manager.forEachHeartbeat((sim, time) -> heartbeats.add(time));
        assertTrue(heartbeats.isEmpty());
        verifyNoInteractions(producer);
        heartbeat(manager, replacement);
        manager.forEachHeartbeat((sim, time) -> heartbeats.add(time));
        assertEquals(1, heartbeats.size());
        listener.sessionDestroyed(replacement);
        verify(producer).sendRegistryEvent(eq(DEVICE_DISCONNECT_EVENT), any(DisconnectMessage.class));
    }

    @Test
    void registeringSameSessionAgainRetainsHeartbeat()
    {
        Session session = session("001234567890");
        register(session);
        heartbeat(manager, session);
        register(session);
        List<Long> heartbeats = new ArrayList<>();
        manager.forEachHeartbeat((sim, time) -> heartbeats.add(time));
        assertEquals(1, heartbeats.size());
    }

    @Test
    void expiredHeartbeatStillAllowsDisconnectAndFreshHeartbeat()
    {
        Session session = session("001234567890");
        register(session);
        heartbeat(manager, session);
        manager.clearExpiredHeartbeats(Long.MAX_VALUE, 360_000);
        manager.forEachHeartbeat((sim, time) -> fail("Expired heartbeat published"));
        heartbeat(manager, session);
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
        register(session);
        heartbeat(manager, session);
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
        register(session);
        heartbeat(manager, session);
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
        new JT808Endpoint(null, producer, manager, null).T0003(null, session);
        verify(session).invalidate();
    }

    @Test
    void disconnectWaitsForFirstOnlineHeartbeatAndThenStopsPublication() throws Exception
    {
        Session session = session("001234567890");
        register(session);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        HeartbeatPublisher publisher = new HeartbeatPublisher(manager, rabbit);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            order.add("online");
            return null;
        }).when(rabbit).convertAndSend(eq(REGISTRY_EXCHANGE), eq(HEARTBEAT_EVENT),
            any(HeartbeatMessage.class), any(MessagePostProcessor.class));
        doAnswer(invocation -> { order.add("disconnect"); return null; }).when(producer)
            .sendRegistryEvent(eq(DEVICE_DISCONNECT_EVENT), any());
        try (var executor = Executors.newFixedThreadPool(2))
        {
            var heartbeat = executor.submit(() -> publisher.receiveHeartbeat(session));
            try
            {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var disconnect = executor.submit(() -> listener.sessionDestroyed(session));
                release.countDown();
                heartbeat.get(5, TimeUnit.SECONDS);
                disconnect.get(5, TimeUnit.SECONDS);
            }
            finally
            {
                release.countDown();
            }
        }
        assertEquals(List.of("online", "disconnect"), order);
        publisher.receiveHeartbeat(session);
        publisher.publishHeartbeat();
        verify(rabbit).convertAndSend(eq(REGISTRY_EXCHANGE), eq(HEARTBEAT_EVENT),
            any(HeartbeatMessage.class), any(MessagePostProcessor.class));
    }

    @Test
    void delayedRegistrationCannotStealReplacementHeartbeatAndDisconnect() throws Exception
    {
        Session old = session("001234567890");
        Session replacement = session("001234567890");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<JTSessionListener> delegate = new AtomicReference<>();
        TestSessionManager authoritative = new TestSessionManager(new SessionListener() {
            @Override
            public void sessionCreated(Session session) {}

            @Override
            public void sessionRegistered(Session session)
            {
                if (session == old)
                {
                    entered.countDown();
                    try
                    {
                        if (!release.await(5, TimeUnit.SECONDS))
                            throw new IllegalStateException("Registration callback was not released");
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }
                delegate.get().sessionRegistered(session);
            }

            @Override
            public void sessionDestroyed(Session session)
            {
                delegate.get().sessionDestroyed(session);
            }
        });
        DeviceSessionManager tracker = new DeviceSessionManager(provider(authoritative));
        delegate.set(new JTSessionListener(tracker, producer));
        try (var executor = Executors.newSingleThreadExecutor())
        {
            var pending = executor.submit(() -> authoritative.addSession(old));
            try
            {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                authoritative.addSession(replacement);
            }
            finally
            {
                release.countDown();
            }
            pending.get(5, TimeUnit.SECONDS);
        }
        assertSame(replacement, authoritative.get(replacement.getId()));
        tracker.authenticate(old);
        heartbeat(tracker, old);
        tracker.forEachHeartbeat((sim, time) -> fail("Superseded session heartbeat was accepted"));
        tracker.authenticate(replacement);
        heartbeat(tracker, replacement);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        HeartbeatPublisher publisher = new HeartbeatPublisher(tracker, rabbit);
        publisher.publishHeartbeat();
        ArgumentCaptor<HeartbeatMessage> heartbeat = ArgumentCaptor.forClass(HeartbeatMessage.class);
        verify(rabbit).convertAndSend(eq(REGISTRY_EXCHANGE), eq(HEARTBEAT_EVENT), heartbeat.capture(),
            any(MessagePostProcessor.class));
        assertEquals(replacement.getClientId(), heartbeat.getValue().deviceId());
        assertTrue(heartbeat.getValue().online());
        authoritative.removeSession(replacement);
        authoritative.removeSession(old);
        delegate.get().sessionDestroyed(replacement);
        verify(producer, times(1)).sendRegistryEvent(eq(DEVICE_DISCONNECT_EVENT), any(DisconnectMessage.class));
        publisher.publishHeartbeat();
        verifyNoMoreInteractions(rabbit);
    }

    @Test
    void callbackAfterRegistryRemovalCannotRecreateHeartbeatState()
    {
        Session session = session("001234567890");
        register(session);
        listener.sessionDestroyed(session);
        when(registry.get(session.getId())).thenReturn(null);
        listener.sessionRegistered(session);
        heartbeat(manager, session);
        manager.forEachHeartbeat((sim, time) -> fail("Removed session was recreated by delayed callback"));
        listener.sessionDestroyed(session);
        verify(producer, times(1)).sendRegistryEvent(eq(DEVICE_DISCONNECT_EVENT), any(DisconnectMessage.class));
    }

    @Test
    void lazyRegistryDependencyAllowsSpringContextStartup()
    {
        try (var context = new AnnotationConfigApplicationContext())
        {
            context.register(DeviceSessionManager.class, JTSessionListener.class);
            context.registerBean(MessageProducer.class, () -> producer);
            context.registerBean(SessionManager.class,
                () -> new TestSessionManager(context.getBean(SessionListener.class)));
            context.refresh();
            TestSessionManager authoritative = (TestSessionManager) context.getBean(SessionManager.class);
            Session session = session("001234567890");
            authoritative.addSession(session);
            DeviceSessionManager tracker = context.getBean(DeviceSessionManager.class);
            tracker.authenticate(session);
            heartbeat(tracker, session);
            List<Long> heartbeats = new ArrayList<>();
            tracker.forEachHeartbeat((sim, time) -> heartbeats.add(time));
            assertEquals(1, heartbeats.size());
            authoritative.removeSession(session);
            verify(producer).sendRegistryEvent(eq(DEVICE_DISCONNECT_EVENT), any(DisconnectMessage.class));
        }
    }

    private static final class TestSessionManager extends SessionManager
    {
        private TestSessionManager(SessionListener listener)
        {
            super(listener);
        }

        private void addSession(Session session)
        {
            super.add(session);
        }

        private void removeSession(Session session)
        {
            super.remove(session);
        }
    }
}
