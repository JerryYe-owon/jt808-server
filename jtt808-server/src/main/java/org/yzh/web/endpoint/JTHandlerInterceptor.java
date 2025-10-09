package org.yzh.web.endpoint;

import io.github.yezhihao.netmc.core.HandlerInterceptor;
import io.github.yezhihao.netmc.session.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yzh.protocol.basics.JTMessage;
import org.yzh.protocol.commons.JT808;
import org.yzh.protocol.t808.T0001;
import org.yzh.protocol.t808.T0102;
import org.yzh.protocol.t808.T0200;
import org.yzh.web.config.RabbitMQConfig;
import org.yzh.web.model.dto.AuthMessage;
import org.yzh.web.model.dto.GPSMessage;
import org.yzh.web.model.dto.RegistryMessage;
import org.yzh.web.model.entity.DeviceDO;
import org.yzh.web.model.enums.SessionKey;
import org.yzh.web.model.vo.T0200Ext;
import org.yzh.web.service.DeviceSessionManager;
import org.yzh.web.service.MessageProducer;

@Slf4j
@Component
public class JTHandlerInterceptor implements HandlerInterceptor<JTMessage> {

    private final MessageProducer messageProducer;
    private final DeviceSessionManager deviceSessionManager;

    public JTHandlerInterceptor(MessageProducer messageProducer, DeviceSessionManager deviceSessionManager)
    {
        this.messageProducer = messageProducer;
        this.deviceSessionManager = deviceSessionManager;
    }

    /** 未找到对应的Handle */
    @Override
    public JTMessage notSupported(JTMessage request, Session session) {
        T0001 response = new T0001();
        response.copyBy(request);
        response.setMessageId(JT808.平台通用应答);
        response.setSerialNo(session.nextSerialNo());

        response.setResponseSerialNo(request.getSerialNo());
        response.setResponseMessageId(request.getMessageId());
        response.setResultCode(T0001.NotSupport);

        log.info("{}\n<<<<-未识别的消息{}\n>>>>-{}", session, request, response);
        return response;
    }


    /** 调用之后，返回值为void的 */
    @Override
    public JTMessage successful(JTMessage request, Session session) {
        T0001 response = new T0001();
        response.copyBy(request);
        response.setMessageId(JT808.平台通用应答);
        response.setSerialNo(session.nextSerialNo());

        response.setResponseSerialNo(request.getSerialNo());
        response.setResponseMessageId(request.getMessageId());
        response.setResultCode(T0001.Success);

//        log.info("{}\n<<<<-{}\n>>>>-{}", session, request, response);
        return response;
    }

    /** 调用之后抛出异常的 */
    @Override
    public JTMessage exceptional(JTMessage request, Session session, Throwable e) {
        T0001 response = new T0001();
        response.copyBy(request);
        response.setMessageId(JT808.平台通用应答);
        response.setSerialNo(session.nextSerialNo());

        response.setResponseSerialNo(request.getSerialNo());
        response.setResponseMessageId(request.getMessageId());
        response.setResultCode(T0001.Failure);

        log.warn(session + "\n<<<<-" + request + "\n>>>>-" + response + '\n', e);
        return response;
    }

    /** 调用之前 */
    @Override
    public boolean beforeHandle(JTMessage request, Session session) {
        if (!session.isRegistered()) {
            log.warn("{}未注册的设备<<<<-{}", session, request);
//            return false;//忽略该消息
        }

        int messageId = request.getMessageId();
        String simNumber = request.getClientId();

        if (messageId == JT808.终端注册 || messageId == JT808.终端鉴权)
        {
            return true;
        }

        else if (messageId == JT808.终端心跳)
        {
            deviceSessionManager.updateHeartbeat(simNumber);
        }

        else if (messageId == JT808.位置信息汇报)
        {
            T0200 t0200 = (T0200) request;
            if (t0200.getDeviceTime() == null) {
                return false;//忽略没有时间的消息
            }
            request.setExtData(new T0200Ext(t0200));

            DeviceDO device = session.getAttribute(SessionKey.Device);

            GPSMessage gpsMessage = GPSMessage.builder()
                    .longitude(device.getLocation().getLng())
                    .latitude(device.getLocation().getLat())
                    .deviceTime(device.getLocation().getDeviceTime())
                    .simNumber(simNumber)
                    .build();
            messageProducer.sendMessage(RabbitMQConfig.CONTROL_EXCHANGE, simNumber, "gps", gpsMessage);

            return true;
        }
        return true;
    }

    /** 调用之后 */
    @Override
    public void afterHandle(JTMessage request, JTMessage response, Session session) {
        if (response != null) {
            response.copyBy(request);
            response.setSerialNo(session.nextSerialNo());

            if (response.getMessageId() == 0) {
                response.setMessageId(response.reflectMessageId());
            }

            int messageId = request.getMessageId();
            DeviceDO device = session.getAttribute(SessionKey.Device);
            String simNumber = device.getMobileNo();

            if (messageId == JT808.终端注册)
            {
                RegistryMessage registryMessage = RegistryMessage.builder()
                        .protocolVersion(device.getProtocolVersion())
                        .simNumber(simNumber)
                        .plateNo(device.getPlateNo())
                        .deviceModel(device.getDeviceModel())
                        .deviceId(device.getDeviceId())
                        .makerId(device.getMakerId())
                        .build();

                messageProducer.sendRegistryEvent(RabbitMQConfig.DEVICE_REGISTER_EVENT, registryMessage);
            }
            else if (messageId == JT808.终端鉴权)
            {
                T0102 t0102 = (T0102) request;
                AuthMessage authMessage = AuthMessage.builder()
                        .imei(t0102.getImei())
                        .simNumber(simNumber)
                        .softwareVersion(t0102.getSoftwareVersion())
                        .build();

                messageProducer.sendRegistryEvent(RabbitMQConfig.DEVICE_AUTH_EVENT, authMessage);
            }

            else if (messageId == JT808.终端心跳)
            {

            }
        }
//        log.info("{}\n<<<<-{}\n>>>>-{}", session, request, response);
    }

    /**
     * 根据安装后终端自身的手机号转换。
     * 手机号不足12位，则在前补充数字，
     * 大陆手机号补充数字0，
     * 港澳台则根据其区号进行位数补充。
     */
    private String buildMobileNo12(String mobileNo)
    {
        if (mobileNo == null || mobileNo.isEmpty())
        {
            return null;
        }
        if (mobileNo.length() == 12)
        {
            return mobileNo;
        }
        if (mobileNo.length() == 11)
        {
            return "0" + mobileNo; // +86
        }
        // todo
        return String.format("%012d", Long.parseLong(mobileNo));
    }
}