package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.McpMetaKeys;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/** In-process subscription runtime; transport adapters own the actual stream. */
public final class McpSubscriptionManager {
    private final ConcurrentMap<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public Subscription open(String requestId, McpSubscriptionSpec spec) {
        Subscription subscription = new Subscription(requestId, spec);
        Subscription existing = subscriptions.putIfAbsent(requestId, subscription);
        if (existing != null) {
            throw new IllegalArgumentException("Duplicate MCP subscription request id: " + requestId);
        }
        return subscription;
    }

    public void toolsChanged() {
        subscriptions.values().forEach(subscription -> {
            if (subscription.spec().toolsListChanged()) subscription.publish(notification(
                    "notifications/tools/list_changed", subscription.requestId(), Map.of()));
        });
    }

    public void promptsChanged() {
        subscriptions.values().forEach(subscription -> {
            if (subscription.spec().promptsListChanged()) subscription.publish(notification(
                    "notifications/prompts/list_changed", subscription.requestId(), Map.of()));
        });
    }

    public void resourcesChanged() {
        subscriptions.values().forEach(subscription -> {
            if (subscription.spec().resourcesListChanged()) subscription.publish(notification(
                    "notifications/resources/list_changed", subscription.requestId(), Map.of()));
        });
    }

    public void resourceUpdated(String uri) {
        subscriptions.values().forEach(subscription -> {
            if (subscription.spec().resourceSubscriptions().contains(uri)) {
                subscription.publish(notification("notifications/resources/updated",
                        subscription.requestId(), Map.of("uri", uri)));
            }
        });
    }

    public void close(String requestId) {
        Subscription subscription = subscriptions.remove(requestId);
        if (subscription != null) subscription.close();
    }

    private Map<String, Object> notification(String method, String requestId, Map<String, Object> params) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(McpMetaKeys.SUBSCRIPTION_ID, requestId);
        Map<String, Object> payload = new LinkedHashMap<>(params);
        payload.put("_meta", metadata);
        return Map.of("jsonrpc", "2.0", "method", method, "params", payload);
    }

    public final class Subscription implements Flow.Publisher<Map<String, Object>> {
        private final String requestId;
        private final McpSubscriptionSpec spec;
        private final SubmissionPublisher<Map<String, Object>> publisher = new SubmissionPublisher<>();

        private Subscription(String requestId, McpSubscriptionSpec spec) {
            this.requestId = requestId;
            this.spec = spec;
        }

        public String requestId() {
            return requestId;
        }

        public McpSubscriptionSpec spec() {
            return spec;
        }

        public void publish(Map<String, Object> event) {
            publisher.submit(event);
        }

        public void close() {
            subscriptions.remove(requestId, this);
            publisher.submit(Map.of(
                    "jsonrpc", "2.0",
                    "id", requestId,
                    "result", Map.of(
                            "resultType", "complete",
                            "_meta", Map.of(McpMetaKeys.SUBSCRIPTION_ID, requestId))));
            publisher.close();
        }

        @Override
        public void subscribe(Flow.Subscriber<? super Map<String, Object>> subscriber) {
            publisher.subscribe(subscriber);
        }
    }
}
