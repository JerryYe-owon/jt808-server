package org.yzh.web.endpoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessageProperties;
import org.yzh.protocol.t808.T0705;
import org.yzh.web.config.RabbitMQConfig;
import org.yzh.web.config.RabbitMQMessageConverterConfig;
import org.yzh.web.model.dto.CanUploadMessage;
import org.yzh.web.service.MessageProducer;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CanUploadPublishTest
{
    @Test
    void publishesOrderedUnsignedFramesWithOriginalCountAndTime() throws Exception
    {
        MessageProducer producer = mock(MessageProducer.class);
        JT808Endpoint endpoint = new JT808Endpoint(null, producer, null, null);
        T0705 message = new T0705();
        message.setClientId("001234567890");
        message.setDateTime("0102030004");
        byte[] bytes = new byte[] {0, 1, 2, 3, 4, 5, 6, (byte) 255};
        message.setItems(List.of(new T0705.Item().setId(0xE1234567).setData(bytes),
            new T0705.Item().setId(1).setData(new byte[8])));

        endpoint.T0705(message, null);
        ArgumentCaptor<CanUploadMessage> captor = ArgumentCaptor.forClass(CanUploadMessage.class);
        verify(producer).sendMessage(eq(RabbitMQConfig.CONTROL_EXCHANGE), eq("001234567890"), eq("can"), captor.capture());
        CanUploadMessage upload = captor.getValue();
        assertThat(upload.uploadId()).isNotNull();
        assertThat(upload.total()).isEqualTo(2);
        assertThat(upload.dateTime()).isEqualTo("0102030004");
        assertThat(upload.items().getFirst().id()).isEqualTo(0xE1234567L);
        bytes[0] = 99;
        assertThat(upload.items().getFirst().data()[0]).isZero();

        var wire = new RabbitMQMessageConverterConfig().jackson2JsonMessageConverter()
            .toMessage(upload, new MessageProperties());
        JsonNode json = new ObjectMapper().readTree(wire.getBody());
        assertThat(json.get("receivedAt").isTextual()).isTrue();
        assertThat(json.get("simNumber").asText()).isEqualTo("001234567890");
        assertThat(json.get("items").get(0).get("data").binaryValue()).hasSize(8);
    }
}
