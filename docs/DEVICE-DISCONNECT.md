# Device online and disconnect events

Registered JT808 session destruction publishes JSON to `device.registry.exchange`
with routing key `device.disconnect`. The existing binding delivers it to
`device.registry.backend.queue`.

```json
{"simNumber":"001234567890","timestamp":1791763200000}
```

`simNumber` is the JT808 client/SIM identifier, preserved as a string.
`timestamp` is the gateway event time in UTC epoch milliseconds. Disconnect
messages have no TTL. They use the existing RabbitTemplate JSON conversion and
persistent-message default.

## Lifecycle

- A detected close or terminal logout emits one event for the current registered
  session, even if that session never sent a heartbeat.
- Unidentified, duplicate, and superseded-session callbacks emit nothing.
- Registration, heartbeat updates, publication, and destruction serialize per
  SIM. An old connection cannot update the replacement's heartbeat state.
- Registration callbacks must match netmc's authoritative session registry
  inside that per-SIM lock. A delayed callback cannot reclaim ownership from a
  replacement connection or recreate state after registry removal. The registry
  dependency is resolved lazily to avoid a Spring bean construction cycle.
- Destruction removes heartbeat state even if RabbitMQ publication fails.
  Failure is logged. There is no durable gateway outbox; backend heartbeat
  monitoring remains the fallback for broker failures or gateway crashes.
- Silent network loss waits for the configured idle timeout, currently 180
  seconds. A detected close does not wait for the heartbeat timeout.
- Registration and successful authentication do not publish online status.
  The current session must authenticate before its heartbeats are tracked.
- The first actual terminal heartbeat after authentication publishes online
  status immediately. Activity resuming after the 120-second heartbeat timeout
  also publishes online immediately; later heartbeats use the existing
  30-second status publisher.
- Repeated registration/authentication on the same session preserves activity.
  Replacement sessions start unauthenticated and require their own heartbeat.
  Authoritative ownership is checked for authentication, heartbeat receipt,
  and scheduled publication, including when a replacement callback is delayed.
- Initial online publication shares the per-SIM lock with disconnect. Broker
  failures are logged and retain actual heartbeat activity for scheduled retry.

## Online heartbeat contract

Online status uses `device.registry.exchange` and routing key `device.heartbeat`,
with the existing JSON schema and 120-second message TTL:

```json
{"deviceId":"001234567890","online":true,"lastHeartbeatTime":1791763200000,"timestamp":1791763200000}
```

For an immediate publication, both timestamps are the actual gateway receipt
time of the terminal heartbeat. Scheduled messages retain that activity time
and use the current publication time for `timestamp`. Authentication never
fabricates a heartbeat. No terminal heartbeat means no online publication.

Backend heartbeat consumption only updates existing device records. If it races
asynchronous backend registration, the initial message is ignored; the periodic
publisher retries while activity remains fresh. The existing frontend manual
refresh reads the resulting database status. No frontend polling is added.

VTMS compares event timestamps atomically, so a queued older heartbeat cannot
restore online status after disconnect. A newer heartbeat restores online after
reconnection; offline wins ties. Gateway and backend clocks must stay synchronized.

## Validation

From the gateway repository:

```powershell
mvn -B -pl jtt808-server -am '-Dmaven.test.skip=false' '-Dtest=JT808EndpointTest,JTSessionListenerTest,GpsGmt8PublishingTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

The explicit `maven.test.skip=false` overrides this repository's default.
Backend migration, deployment order, and broker/database checks are documented
in VTMS `docs/DEVICE-DISCONNECT-ROLLOUT.md`.

Verified 2026-10-12: 12 targeted gateway tests passed, including reordered
registration callbacks against the real netmc session manager, late callbacks
after registry removal, Spring context startup, lifecycle replacement, failure
cleanup, and concurrent publication. Both callback regressions failed before
the authoritative registry check and passed after it.

Verified 2026-10-13: 19 targeted gateway tests passed. Coverage includes no
online publication before auth/heartbeat, immediate first-heartbeat reporting,
repeat auth, replacement ownership, timeout recovery, scheduled retry after
broker failure, and serialization of initial online publication with disconnect.
