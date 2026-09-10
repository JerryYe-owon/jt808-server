# GPS time contract

The device GPS wall-clock value in JT808 T0200 represents fixed GMT+8. The device
team has confirmed this interpretation.
The six-byte packet timestamp has no offset; protocol decoding remains LocalDateTime.
The T0200 publishing endpoint attaches ZoneOffset.ofHours(8) and the GPS DTO
serializes deviceTime as an ISO string: `2026-09-10T09:30:00+08:00`. This value
represents the UTC instant `2026-09-10T01:30:00Z`.

Routing, all other fields, packet formats and UTC server-time responses remain
unchanged. This serialization setting applies only to GPS deviceTime.

Pause GPS publishing, drain old queued messages through the old backend, complete
the planned dev/test GPS reset, apply backend V011, and deploy backend, gateway and
frontend together before resuming publishing. Verify a captured position across
RabbitMQ, database, REST/SSE and browser. Offsetless legacy messages are unsupported
after rollout. Do not reinterpret historical data or change DVR/JT1078 formats.
