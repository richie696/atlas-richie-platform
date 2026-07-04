# Atlas Richie MQTT Component (atlas-richie-component-mqtt)

> Unified **MQTT client** component for Spring Boot 4.x. Wraps [Eclipse Paho](https://www.eclipse.org/paho/) and [HiveMQ MQTT Client](https://www.hivemq.com/) with auto-reconnect, QoS-aware pub/sub, retained message handling, last-will-and-testament (LWT), and TLS / mTLS out of the box. Designed for IoT device fleets.

---

## 📖 Contents

- [📖 Overview](#📖-overview)
  - [What this component is — and what it isn't](#what-this-component-is-—-and-what-it-isnt)
- [✨ Features](#✨-features)
  - [Core capabilities](#core-capabilities)
  - [Design choices](#design-choices)
- [🏗️ Architecture & Module Layout](#🏗️-architecture-&-module-layout)
- [🚀 Quick Start](#🚀-quick-start)
  - [1. Add the dependency](#1-add-the-dependency)
  - [2. Configure](#2-configure)
  - [3. Publish a message](#3-publish-a-message)
  - [4. Subscribe to a topic](#4-subscribe-to-a-topic)
- [🔧 Core Capabilities](#🔧-core-capabilities)
  - [1. Publish with QoS 0 / 1 / 2](#1-publish-with-qos-0-/-1-/-2)
  - [2. Topic wildcards](#2-topic-wildcards)
  - [3. Last will and testament](#3-last-will-and-testament)
  - [4. Retained messages](#4-retained-messages)
  - [5. TLS / mTLS](#5-tls-/-mtls)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
  - [Q1: Paho vs HiveMQ — which one?](#q1-paho-vs-hivemq-—-which-one?)
  - [Q2: How do I handle device-side offline buffering?](#q2-how-do-i-handle-device-side-offline-buffering?)
  - [Q3: Can I bridge MQTT to Kafka?](#q3-can-i-bridge-mqtt-to-kafka?)
  - [Q4: How do I authenticate devices?](#q4-how-do-i-authenticate-devices?)
- [📚 Further Reading](#📚-further-reading)
---

## 📖 Overview

| Item | Value |
|------|-------|
| **Artifact** | `com.richie.component:atlas-richie-component-mqtt` |
| **Category** | IoT / messaging — MQTT pub/sub |
| **Hard dependencies** | Eclipse Paho / HiveMQ MQTT Client |
| **Compatible with** | MQTT 3.1.1 / 5.0 brokers (EMQX, HiveMQ, Mosquitto, AWS IoT Core) |

### `What` this component is — and what it isn't

| ✅ It gives you | ❌ It does not give you |
|-----------------|------------------------|
| One `MqttClient` facade for Paho and HiveMQ | A managed broker (use EMQX / HiveMQ / Mosquitto separately) |
| Auto-reconnect with backoff | Cluster client failover (HiveMQ Enterprise) |
| QoS-aware pub / sub | AMQP / Kafka (use other components) |
| TLS / mTLS / WebSocket transports | Persistent session storage (broker-side) |

## ✨ Features

### `Core` capabilities

- ✅ **Paho + HiveMQ** — switch by config (`paho` / `hivemq`).
- ✅ **Auto-reconnect** — exponential backoff with jitter.
- ✅ **QoS 0 / 1 / 2** — per-publish / per-subscribe.
- ✅ **Topic wildcards** — `+` (single level), `#` (multi level).
- ✅ **Last will and testament (LWT)** — declare offline message per client.
- ✅ **Retained messages** — set `retained=true` on publish.
- ✅ **TLS / mTLS** — server cert + client cert / key.
- ✅ **WebSocket transport** — for browser clients behind a proxy.

### `Design` choices

- ✅ **Auto-config** — `MqttClient` bean ready after Spring context up.
- ✅ **Connection pooling** — multi-broker support via named clients.
- ✅ **Spring events** — `MqttMessageReceivedEvent` decouples subscribers from raw callbacks.

## 🏗️ Architecture & Module Layout

```
atlas-richie-component-mqtt
├── config/
│   ├── MqttAutoConfiguration
│   ├── MqttProperties
│   └── MqttClientFactory
├── client/
│   ├── MqttClient                     ← facade (auto-injected)
│   ├── PahoClientAdapter
│   └── HiveMqClientAdapter
├── topic/
│   ├── MqttPublisher                 ← publish API
│   └── MqttSubscriber                ← @MqttListener annotation
├── security/
│   ├── TlsMaterialLoader
│   └── ClientCertProvider
└── event/
    └── MqttMessageReceivedEvent
```

## 🚀 Quick Start

### 1) `Add` the dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-mqtt</artifactId>
</dependency>
```

### 2) `Configure`

```yaml
platform:
  component:
    mqtt:
      provider: paho                       # paho | hivemq
      broker: tcp://broker.local:1883
      client-id: ${spring.application.name}-${random.uuid}
      auto-reconnect: true
      keep-alive-seconds: 30
      clean-session: true
      default-qos: 1
      last-will:
        topic: devices/${client-id}/status
        message: offline
        qos: 1
        retained: true
      ssl:
        enabled: false
```

### 3) `Publish` a message

```java
@Service
@RequiredArgsConstructor
public class DeviceTelemetryService {
    private final MqttPublisher publisher;

    public void report(String deviceId, Telemetry telemetry) {
        publisher.publish(
                "devices/" + deviceId + "/telemetry",
                JsonUtils.toJson(telemetry),
                MqttQos.AT_LEAST_ONCE,
                false   // not retained
        );
    }
}
```

### 4) `Subscribe` to a topic

```java
@Component
public class DeviceCommandListener {
    @MqttListener(topics = "devices/+/command", qos = MqttQos.AT_LEAST_ONCE)
    public void onCommand(String topic, byte[] payload) {
        String deviceId = extractDeviceId(topic);
        String command = new String(payload);
        // process
    }
}
```

## 🔧 Core Capabilities

### 1) `Publish` with `QoS` 0 / 1 / 2

```java
publisher.publish(topic, payload, MqttQos.AT_MOST_ONCE,    false);  // QoS 0: fire-and-forget
publisher.publish(topic, payload, MqttQos.AT_LEAST_ONCE,   false);  // QoS 1: ack
publisher.publish(topic, payload, MqttQos.EXACTLY_ONCE,    false);  // QoS 2: 4-way handshake
```

### 2) `Topic` wildcards

```java
@MqttListener(topics = "sensors/+/temperature")     // sensors/kitchen/temperature
@MqttListener(topics = "sensors/#")                 // sensors/anything/anything
```

### 3) `Last` will and testament

```yaml
platform:
  component:
    mqtt:
      last-will:
        topic: devices/${client-id}/status
        message: offline
        qos: 1
        retained: true
```

Broker publishes this on unexpected disconnect.

### 4) `Retained` messages

```java
publisher.publish("home/kitchen/temperature", payload, MqttQos.AT_LEAST_ONCE, true);  // retained=true
```

New subscribers immediately receive the latest retained message.

### 5) `TLS` / mTLS

```yaml
platform:
  component:
    mqtt:
      ssl:
        enabled: true
        protocol: TLSv1.3
        trust-store: classpath:truststore.jks
        trust-store-password: changeit
        key-store: classpath:keystore.jks
        key-store-password: changeit
        key-password: changeit
```

For mTLS, provide both `trust-store` and `key-store`.

## ⚙️ Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `provider` | enum | `paho` | `paho` / `hivemq` |
| `broker` | String | – | Broker URL (tcp:// / ssl:// / ws://) |
| `client-id` | String | auto | Unique client ID |
| `auto-reconnect` | boolean | `true` | Auto-reconnect on failure |
| `keep-alive-seconds` | int | `30` | PINGREQ interval |
| `clean-session` | boolean | `true` | Discard session on disconnect |
| `default-qos` | enum | `1` | Default QoS for pub / sub |
| `max-inflight` | int | `1000` | Max in-flight QoS 1 / 2 messages |
| `last-will.topic` | String | – | LWT topic |
| `last-will.qos` | enum | `1` | LWT QoS |
| `last-will.retained` | boolean | `false` | LWT retained |
| `ssl.enabled` | boolean | `false` | Enable TLS |
| `ssl.protocol` | enum | `TLSv1.3` | TLS version |

## 🎯 Best Practices

1. **Use stable client IDs in production** — `random.uuid` is great for dev, but kills retained message continuity.
2. **Set LWT for every client** — `last-will` ensures broker knows when device drops offline.
3. **QoS 1 is the default for IoT telemetry** — QoS 2 is expensive (4-way handshake); QoS 0 may lose messages.
4. **Retained messages for device state** — non-retained for streaming telemetry.
5. **TLS for production** — even on internal network. mTLS for device-to-cloud.

## ⚠️ Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **Single broker per `MqttClient` bean** | No transparent failover | Run multiple `MqttClient` beans for HA |
| **MQTT 5.0 only on HiveMQ** | Paho supports 3.1.1 / 5.0 partially | Use HiveMQ for full v5 |
| **No shared subscription** | HiveMQ Enterprise only | Standard MQTT doesn't support |
| **No request / response pattern** | Use HTTP for sync | Combine with [`atlas-richie-component-http`](../atlas-richie-component-http/README.md) |

## ❓ FAQ

### `Q1`: `Paho` vs `HiveMQ` — which one?

- **Paho** — small, mature, default.
- **HiveMQ** — modern, faster, full MQTT 5.0, WebSocket first-class.

### `Q2` — `How` do `I` handle device-side offline buffering?

Use `clean-session: false` and store messages locally on device; replay on reconnect. The component doesn't provide device-side buffering.

### `Q3` — `Can` `I` bridge `MQTT` to `Kafka`?

Use a connector (EMQX has one). Or write a Spring `@MqttListener` that publishes to Kafka via [`atlas-richie-component-messaging`](../atlas-richie-component-messaging/README.md).

### `Q4` — `How` do `I` authenticate devices?

Use username/password (`username` / `password` config) or X.509 client certs (mTLS). For OAuth 2.0-based MQTT (HiveMQ only), implement custom auth provider.

## 📚 Further Reading

- **Parent component** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **HTTP** — [`../atlas-richie-component-http/README.md`](../atlas-richie-component-http/README.md)
- **Messaging** — [`../atlas-richie-component-messaging/README.md`](../atlas-richie-component-messaging/README.md)
- External: [Eclipse Paho](https://www.eclipse.org/paho/) · [HiveMQ MQTT Client](https://www.hivemq.com/) · [MQTT 5.0 spec](https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html)

---

**atlas-richie-component-mqtt** 🚀
