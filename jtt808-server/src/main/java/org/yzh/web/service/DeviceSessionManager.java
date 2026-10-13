package org.yzh.web.service;

import io.github.yezhihao.netmc.session.Session;
import io.github.yezhihao.netmc.session.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

@Slf4j
@Component
public class DeviceSessionManager
{

    private final ConcurrentMap<String, State> sessions = new ConcurrentHashMap<>();
    private final ObjectProvider<SessionManager> sessionManagerProvider;

    public DeviceSessionManager(ObjectProvider<SessionManager> sessionManagerProvider)
    {
        this.sessionManagerProvider = sessionManagerProvider;
    }

    private static final class State
    {
        private final Session session;
        private boolean authenticated;
        private Long lastHeartbeatTime;

        private State(Session session)
        {
            this.session = session;
        }
    }

    public void register(Session session)
    {
        String sim = session.getClientId();
        if (sim == null || sim.isBlank()) return;
        sessions.compute(sim, (key, state) -> {
            // netmc updates its registry before invoking callbacks; older callbacks can arrive last.
            if (sessionManagerProvider.getObject().get(session.getId()) != session)
                return state;
            return state != null && state.session == session ? state : new State(session);
        });
    }

    public void authenticate(Session session)
    {
        String sim = session.getClientId();
        if (sim == null || sim.isBlank()) return;
        sessions.computeIfPresent(sim, (key, state) -> {
            if (isCurrent(state, session))
                state.authenticated = true;
            return state;
        });
    }

    public void updateHeartbeat(Session session, long timeoutMs, BiConsumer<String, Long> publishOnline)
    {
        String sim = session.getClientId();
        if (sim == null || sim.isBlank()) return;
        sessions.computeIfPresent(sim, (key, state) -> {
            if (!state.authenticated || !isCurrent(state, session)) return state;
            long now = System.currentTimeMillis();
            boolean becameOnline = state.lastHeartbeatTime == null || now - state.lastHeartbeatTime > timeoutMs;
            state.lastHeartbeatTime = now;
            if (becameOnline)
            {
                try
                {
                    publishOnline.accept(key, now);
                }
                catch (RuntimeException e)
                {
                    // Retain activity so scheduled publication can retry after a broker failure.
                    log.error("Failed to publish online heartbeat for sim={}", sim, e);
                }
            }
            return state;
        });
    }

    private boolean isCurrent(State state, Session session)
    {
        return state.session == session && sessionManagerProvider.getObject().get(session.getId()) == session;
    }

    public void disconnect(Session session, Runnable publish)
    {
        String sim = session.getClientId();
        if (sim == null || sim.isBlank()) return;
        sessions.computeIfPresent(sim, (key, state) -> {
            if (state.session != session) return state;
            try
            {
                publish.run();
            }
            catch (RuntimeException e)
            {
                log.error("Failed to publish disconnect for sim={}", sim, e);
            }
            // Cleanup must succeed even when RabbitMQ is unavailable.
            return null;
        });
    }

    /** Publication runs under the same per-SIM lock as registration and disconnect. */
    public void forEachHeartbeat(BiConsumer<String, Long> publish)
    {
        sessions.keySet().forEach(sim -> sessions.computeIfPresent(sim, (key, state) -> {
            if (state.authenticated && state.lastHeartbeatTime != null && isCurrent(state, state.session))
                publish.accept(key, state.lastHeartbeatTime);
            return state;
        }));
    }

    public void clearExpiredHeartbeats(long now, long timeoutMs)
    {
        sessions.keySet().forEach(sim -> sessions.computeIfPresent(sim, (key, state) -> {
            if (state.lastHeartbeatTime != null && now - state.lastHeartbeatTime > timeoutMs)
                state.lastHeartbeatTime = null;
            // Retain ownership so a later close still emits disconnect.
            return state;
        }));
    }
}

