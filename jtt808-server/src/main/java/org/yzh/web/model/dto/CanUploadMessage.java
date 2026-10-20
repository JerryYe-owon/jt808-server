package org.yzh.web.model.dto;

import org.yzh.protocol.t808.T0705;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** JSON upload contract shared with VTMS; byte arrays serialize as base64. */
public record CanUploadMessage(UUID uploadId, String simNumber,
                               @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime receivedAt,
                               int total, String dateTime, List<Item> items)
{
    public record Item(long id, byte[] data) { }

    public static CanUploadMessage from(T0705 message)
    {
        return new CanUploadMessage(UUID.randomUUID(), message.getClientId(), OffsetDateTime.now(ZoneOffset.UTC),
            message.getTotal(), message.getDateTime(), message.getItems().stream()
                .map(item -> new Item(Integer.toUnsignedLong(item.getId()), item.getData().clone())).toList());
    }
}
