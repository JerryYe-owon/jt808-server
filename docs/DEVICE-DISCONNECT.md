# Device disconnect events

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
- Destruction removes heartbeat state even if RabbitMQ publication fails.
  Failure is logged. There is no durable gateway outbox; backend heartbeat
  monitoring remains the fallback for broker failures or gateway crashes.
- Silent network loss waits for the configured idle timeout, currently 180
  seconds. A detected close does not wait for the heartbeat timeout.
- Reconnection becomes online through the existing heartbeat flow. Registration
  and authentication behavior remains unchanged.

VTMS compares event timestamps atomically, so a queued older heartbeat cannot
restore online status after disconnect. A newer heartbeat restores online after
reconnection; offline wins ties. Gateway and backend clocks must stay synchronized.

## Validation

From the gateway repository:

```powershell
mvn -B -pl jtt808-server -am '-Dmaven.test.skip=false' '-Dtest=JTSessionListenerTest,GpsGmt8PublishingTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

The explicit `maven.test.skip=false` overrides this repository's default.
Backend migration, deployment order, and broker/database checks are documented
in VTMS `docs/DEVICE-DISCONNECT-ROLLOUT.md`.

Verified 2026-10-12: 9 targeted gateway tests passed, including lifecycle
replacement, failure cleanup, and concurrent publication.
