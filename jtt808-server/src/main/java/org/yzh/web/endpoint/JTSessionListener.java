package org.yzh.web.endpoint;

import io.github.yezhihao.netmc.core.model.Message;
import io.github.yezhihao.netmc.session.Session;
import io.github.yezhihao.netmc.session.SessionListener;
import org.springframework.stereotype.Component;
import org.yzh.protocol.basics.JTMessage;
import org.yzh.web.config.RabbitMQConfig;
import org.yzh.web.model.dto.DisconnectMessage;
import org.yzh.web.model.entity.DeviceDO;
import org.yzh.web.model.enums.SessionKey;
import org.yzh.web.service.DeviceSessionManager;
import org.yzh.web.service.MessageProducer;

import java.util.function.BiConsumer;

/**
 * @author yezhihao
 * https://gitee.com/yezhihao/jt808-server
 */
@Component
public class JTSessionListener implements SessionListener {

    private final DeviceSessionManager deviceSessionManager;
    private final MessageProducer messageProducer;

    public JTSessionListener(DeviceSessionManager deviceSessionManager, MessageProducer messageProducer) {
        this.deviceSessionManager = deviceSessionManager;
        this.messageProducer = messageProducer;
    }

    /**
     * 下行消息拦截器
     */
    private static final BiConsumer<Session, Message> requestInterceptor = (session, message) -> {
        JTMessage request = (JTMessage) message;
        request.setClientId(session.getClientId());
        request.setSerialNo(session.nextSerialNo());

        if (request.getMessageId() == 0) {
            request.setMessageId(request.reflectMessageId());
        }

        DeviceDO device = session.getAttribute(SessionKey.Device);
        if (device != null) {
            int protocolVersion = device.getProtocolVersion();
            if (protocolVersion > 0) {
                request.setVersion(true);
                request.setProtocolVersion(protocolVersion);
            }
        }
    };

    /**
     * 设备连接
     */
    @Override
    public void sessionCreated(Session session) {
        session.requestInterceptor(requestInterceptor);
    }

    /**
     * 设备注册
     */
    @Override
    public void sessionRegistered(Session session) {
        deviceSessionManager.register(session);
    }

    /**
     * 设备离线
     */
    @Override
    public void sessionDestroyed(Session session) {
        deviceSessionManager.disconnect(session, () -> messageProducer.sendRegistryEvent(
                RabbitMQConfig.DEVICE_DISCONNECT_EVENT,
                new DisconnectMessage(session.getClientId(), System.currentTimeMillis())));
    }
}
