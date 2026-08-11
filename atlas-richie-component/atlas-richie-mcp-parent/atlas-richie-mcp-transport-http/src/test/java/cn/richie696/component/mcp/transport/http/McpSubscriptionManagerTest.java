package cn.richie696.component.mcp.transport.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpSubscriptionManager} 的四件套：按 spec 选择性分发
 * （{@code toolsListChanged} / {@code promptsListChanged} /
 * {@code resourcesListChanged}）、按 URI 定推的 {@code resourceUpdated}、
 * 重复 {@code requestId} 的注册冲突以及 {@link McpSubscriptionManager.Subscription#close()}
 * 的幂等性（含"complete"完成帧 + 释放注册表项）。同时验证 {@link Subscription#subscribe}
 * 桥接到 {@link java.util.concurrent.SubmissionPublisher}。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpSubscriptionManager 订阅注册表")
class McpSubscriptionManagerTest {

    @Test
    @DisplayName("toolsListChanged 只投递到订阅了 toolsListChanged 的连接")
    void toolsListChangedDeliversOnlyToSubscribers() throws Exception {
        McpSubscriptionManager manager = new McpSubscriptionManager();
        McpSubscriptionManager.Subscription subTools =
                manager.open("tools-sub", new McpSubscriptionSpec(true, false, false, Set.of()));
        McpSubscriptionManager.Subscription subResources =
                manager.open("resources-sub", new McpSubscriptionSpec(false, false, false, Set.of()));

        EventCollector tools = collectFrom(subTools);
        EventCollector resources = collectFrom(subResources);

        manager.toolsChanged();

        assertThat(tools.awaitAtLeast(1, 2, TimeUnit.SECONDS)).isTrue();
        assertThat(resources.received).isEmpty();
    }

    @Test
    @DisplayName("promptsListChanged 与 resourcesListChanged 也按 spec 过滤")
    void promptsAndResourcesChangedFilterBySpec() throws Exception {
        McpSubscriptionManager manager = new McpSubscriptionManager();
        McpSubscriptionManager.Subscription prompts =
                manager.open("prompts", new McpSubscriptionSpec(false, true, false, Set.of()));
        McpSubscriptionManager.Subscription resources =
                manager.open("resources", new McpSubscriptionSpec(false, false, true, Set.of()));

        EventCollector promptCol = collectFrom(prompts);
        EventCollector resourceCol = collectFrom(resources);

        manager.promptsChanged();
        manager.resourcesChanged();

        assertThat(promptCol.awaitAtLeast(1, 2, TimeUnit.SECONDS)).isTrue();
        assertThat(resourceCol.awaitAtLeast(1, 2, TimeUnit.SECONDS)).isTrue();
        assertThat(promptCol.received).singleElement()
                .satisfies(event -> assertThat(event).containsEntry(
                        "method", "notifications/prompts/list_changed"));
        assertThat(resourceCol.received).singleElement()
                .satisfies(event -> assertThat(event).containsEntry(
                        "method", "notifications/resources/list_changed"));
    }

    @Test
    @DisplayName("resourceUpdated 只投递到订阅了该 URI 的连接")
    void resourceUpdatedDeliversByUri() throws Exception {
        McpSubscriptionManager manager = new McpSubscriptionManager();
        McpSubscriptionManager.Subscription subA = manager.open(
                "uri-a", new McpSubscriptionSpec(false, false, false, Set.of("file://a")));
        McpSubscriptionManager.Subscription subB = manager.open(
                "uri-b", new McpSubscriptionSpec(false, false, false, Set.of("file://b")));

        EventCollector a = collectFrom(subA);
        EventCollector b = collectFrom(subB);

        manager.resourceUpdated("file://a");

        assertThat(a.awaitAtLeast(1, 2, TimeUnit.SECONDS)).isTrue();
        assertThat(b.received).isEmpty();
    }

    @Test
    @DisplayName("重复 requestId 抛出 IllegalArgumentException")
    void openDuplicateRequestIdThrows() {
        McpSubscriptionManager manager = new McpSubscriptionManager();
        manager.open("dup", new McpSubscriptionSpec(true, false, false, Set.of()));

        assertThatThrownBy(() -> manager.open(
                "dup", new McpSubscriptionSpec(false, false, false, Set.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dup");
    }

    @Test
    @DisplayName("close：从注册表移除订阅并发出 complete 完成帧")
    void closeRemovesAndSendsCompleteFrame() throws Exception {
        McpSubscriptionManager manager = new McpSubscriptionManager();
        McpSubscriptionManager.Subscription sub = manager.open(
                "to-close", new McpSubscriptionSpec(true, false, false, Set.of()));
        EventCollector collector = collectFrom(sub);

        manager.close("to-close");

        assertThat(collector.awaitAtLeast(1, 2, TimeUnit.SECONDS)).isTrue();
        assertReopenSameIdSucceeds(manager, "to-close");
    }

    private static void assertReopenSameIdSucceeds(McpSubscriptionManager manager, String id) {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> manager.open(id, new McpSubscriptionSpec(false, false, false, Set.of())));
    }

    @Test
    @DisplayName("close 未知 id 是安全的 no-op")
    void closeUnknownIdIsSafe() {
        McpSubscriptionManager manager = new McpSubscriptionManager();
        manager.close("never-opened");
    }

    @Test
    @DisplayName("subscription.requestId 与 spec 原样回传")
    void subscriptionExposesRequestIdAndSpec() {
        McpSubscriptionManager manager = new McpSubscriptionManager();
        McpSubscriptionSpec spec = new McpSubscriptionSpec(true, true, true, Set.of("file://a"));

        McpSubscriptionManager.Subscription sub = manager.open("req", spec);

        assertThat(sub.requestId()).isEqualTo("req");
        assertThat(sub.spec()).isSameAs(spec);
    }

    private static EventCollector collectFrom(McpSubscriptionManager.Subscription sub) {
        EventCollector collector = new EventCollector();
        sub.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription flowSubscription) {
                flowSubscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Map<String, Object> item) {
                collector.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });
        return collector;
    }

    /**
     * 简单的线程安全事件收集器，搭配 {@link CountDownLatch} 使用。
     */
    private static final class EventCollector {
        private final List<Map<String, Object>> received = new CopyOnWriteArrayList<>();
        private final AtomicInteger count = new AtomicInteger();

        void add(Map<String, Object> item) {
            received.add(item);
            count.incrementAndGet();
        }

        boolean awaitAtLeast(int expected, long seconds, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(seconds);
            while (count.get() < expected && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            return count.get() >= expected;
        }
    }
}
