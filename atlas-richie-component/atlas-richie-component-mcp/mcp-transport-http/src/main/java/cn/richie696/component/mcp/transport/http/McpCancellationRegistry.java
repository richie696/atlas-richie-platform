package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.api.McpCancellationToken;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bridges JSON-RPC cancelled notifications to in-flight request tokens. */
public final class McpCancellationRegistry {
    private final ConcurrentMap<String, AtomicBoolean> requests = new ConcurrentHashMap<>();

    public McpCancellationToken begin(String requestId) {
        AtomicBoolean cancelled = new AtomicBoolean();
        requests.put(requestId, cancelled);
        return cancelled::get;
    }

    public void cancel(Object requestId) {
        if (requestId != null) {
            AtomicBoolean cancelled = requests.get(String.valueOf(requestId));
            if (cancelled != null) cancelled.set(true);
        }
    }

    public void finish(String requestId) {
        requests.remove(requestId);
    }
}
