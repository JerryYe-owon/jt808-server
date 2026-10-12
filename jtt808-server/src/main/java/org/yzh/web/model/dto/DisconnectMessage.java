package org.yzh.web.model.dto;

/** SIM identifier and disconnect event time in UTC epoch milliseconds. */
public record DisconnectMessage(String simNumber, Long timestamp)
{
}
