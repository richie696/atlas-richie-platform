package com.richie.component.concurrency.virtual;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link VirtualThreadFactory} 的单元测试。
 *
 * <p>覆盖以下行为契约：
 * <ul>
 *   <li>线程命名规则：自定义前缀、默认前缀（{@code "vt-"}）、单调递增计数器</li>
 *   <li>线程类型：返回的 {@link Thread} 必须是 {@link Thread#isVirtual() 虚拟线程}</li>
 *   <li>{@link ScopedValue} 绑定：构造器链式绑定、批量绑定、空绑定容错</li>
 *   <li>ScopedValue 隔离性：未绑定上下文的工厂创建的线程不会继承父线程的 ScopedValue</li>
 *   <li>并发安全性：原子计数器保证并发调用下的名称唯一性</li>
 *   <li>静态工厂：{@code builder()} / {@code of(String)} 默认值正确</li>
 *   <li>异常隔离：任务抛出的异常不会影响调用方线程</li>
 * </ul>
 *
 * <p>使用 {@link ScopedValue#newInstance()} 创建测试专用键，
 * 避免与模块内部使用的 {@code ScopedValue} 冲突。所有异步断言
 * 通过 {@link AtomicReference}/{@link CountDownLatch} 在线程间传递结果。
 */
class VirtualThreadFactoryTest {

    /**
     * 测试用 ScopedValue 键——字符串类型。
     */
    private static final ScopedValue<String> STRING_KEY = ScopedValue.newInstance();

    /**
     * 测试用 ScopedValue 键——整数类型。
     */
    private static final ScopedValue<Integer> INT_KEY = ScopedValue.newInstance();

    /**
     * 测试用 ScopedValue 键——长整型类型。
     */
    private static final ScopedValue<Long> LONG_KEY = ScopedValue.newInstance();

    // =====================================================================
    // 1. 线程命名
    // =====================================================================

    @Test
    @DisplayName("newThread 使用自定义前缀作为线程名前缀")
    void newThread_withCustomPrefix_usesPrefixAsThreadNameBase() throws InterruptedException {
        VirtualThreadFactory factory = VirtualThreadFactory.of("custom-");

        Thread thread = factory.newThread(() -> { /* no-op */ });
        thread.start();
        thread.join();

        assertThat(thread.getName())
                .as("线程名应以自定义前缀开头")
                .startsWith("custom-");
    }

    @Test
    @DisplayName("多次 newThread 生成的线程名单调递增且全部唯一")
    void newThread_invokedMultipleTimes_incrementsCounter() throws InterruptedException {
        VirtualThreadFactory factory = VirtualThreadFactory.builder()
                .namePrefix("counter-")
                .build();

        List<String> names = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Thread thread = factory.newThread(() -> { /* no-op */ });
            threads.add(thread);
            names.add(thread.getName());
        }

        for (Thread thread : threads) {
            thread.start();
            thread.join();
        }

        assertThat(names)
                .as("线程名应单调递增（counter-1, counter-2, ...）")
                .containsExactly("counter-1", "counter-2", "counter-3", "counter-4", "counter-5");
    }

    @Test
    @DisplayName("Builder 默认前缀为 vt-")
    void newThread_withDefaultPrefix_usesVtPrefix() throws InterruptedException {
        VirtualThreadFactory factory = VirtualThreadFactory.builder().build();

        Thread thread = factory.newThread(() -> { /* no-op */ });
        thread.start();
        thread.join();

        assertThat(thread.getName())
                .as("默认前缀应为 vt-")
                .isEqualTo("vt-1");
    }

    @Test
    @DisplayName("newThread 返回的线程是虚拟线程")
    void newThread_threadIsVirtual() {
        VirtualThreadFactory factory = VirtualThreadFactory.builder()
                .namePrefix("is-virtual-")
                .build();

        Thread thread = factory.newThread(() -> { /* no-op */ });

        assertThat(thread.isVirtual())
                .as("工厂应创建虚拟线程")
                .isTrue();
    }

    // =====================================================================
    // 2. ScopedValue 绑定
    // =====================================================================

    @Test
    @DisplayName("newThread 通过 Builder 绑定 ScopedValue，键值在线程内可见")
    void newThread_withScopedValue_bindingVisibleInsideThread() throws InterruptedException {
        VirtualThreadFactory factory = VirtualThreadFactory.builder()
                .namePrefix("scoped-")
                .scopedValue(STRING_KEY, "expected-value")
                .build();
        AtomicReference<String> seenValue = new AtomicReference<>();
        AtomicReference<Boolean> wasBound = new AtomicReference<>(Boolean.FALSE);

        Thread thread = factory.newThread(() -> {
            wasBound.set(STRING_KEY.isBound());
            if (STRING_KEY.isBound()) {
                seenValue.set(STRING_KEY.get());
            }
        });
        thread.start();
        thread.join();

        assertThat(wasBound.get())
                .as("线程内的 ScopedValue 应处于已绑定状态")
                .isTrue();
        assertThat(seenValue.get())
                .as("线程内读取的 ScopedValue 应与构建时绑定的值一致")
                .isEqualTo("expected-value");
    }

    @Test
    @DisplayName("无 ScopedValue 绑定时，子线程不会继承父线程的上下文")
    void newThread_withoutScopedValueBinding_parentContextNotVisibleByDefault() throws Exception {
        VirtualThreadFactory factory = VirtualThreadFactory.builder()
                .namePrefix("isolated-")
                .build();
        AtomicReference<Boolean> wasBoundInsideChild = new AtomicReference<>(Boolean.TRUE);
        AtomicReference<String> childExceptionMessage = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);

        ScopedValue.where(STRING_KEY, "parent-value").run(() -> {
            Thread child = factory.newThread(() -> {
                try {
                    wasBoundInsideChild.set(STRING_KEY.isBound());
                } catch (Throwable t) {
                    childExceptionMessage.set(t.getMessage());
                } finally {
                    finished.countDown();
                }
            });
            child.start();
            try {
                child.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        if (!finished.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("子线程未在限定时间内完成");
        }

        assertThat(childExceptionMessage.get())
                .as("ScopedValue 不应在子线程中引发异常")
                .isNull();
        assertThat(wasBoundInsideChild.get())
                .as("无 ScopedValue 绑定的工厂生成的线程不应看到父线程的上下文")
                .isFalse();
    }

    @Test
    @DisplayName("Builder 多次调用 scopedValue 可链式绑定多个 ScopedValue")
    void builder_scopedValue_bindsMultipleValues() throws InterruptedException {
        VirtualThreadFactory factory = VirtualThreadFactory.builder()
                .namePrefix("multi-")
                .scopedValue(STRING_KEY, "string-value")
                .scopedValue(INT_KEY, 42)
                .scopedValue(LONG_KEY, 1234L)
                .build();
        AtomicReference<String> seenString = new AtomicReference<>();
        AtomicReference<Integer> seenInt = new AtomicReference<>();
        AtomicReference<Long> seenLong = new AtomicReference<>();

        Thread thread = factory.newThread(() -> {
            seenString.set(STRING_KEY.isBound() ? STRING_KEY.get() : null);
            seenInt.set(INT_KEY.isBound() ? INT_KEY.get() : null);
            seenLong.set(LONG_KEY.isBound() ? LONG_KEY.get() : null);
        });
        thread.start();
        thread.join();

        assertThat(seenString.get()).isEqualTo("string-value");
        assertThat(seenInt.get()).isEqualTo(42);
        assertThat(seenLong.get()).isEqualTo(1234L);
    }

    @Test
    @DisplayName("Builder.scopedValues 批量绑定数组形式的绑定项")
    void builder_scopedValues_bindsArrayOfBindings() throws InterruptedException {
        VirtualThreadFactory.ScopedValueBinding<String> binding1 =
                new VirtualThreadFactory.ScopedValueBinding<>(STRING_KEY, "from-array-1");
        VirtualThreadFactory.ScopedValueBinding<Integer> binding2 =
                new VirtualThreadFactory.ScopedValueBinding<>(INT_KEY, 99);
        VirtualThreadFactory factory = VirtualThreadFactory.builder()
                .namePrefix("array-")
                .scopedValues(binding1, binding2)
                .build();
        AtomicReference<String> seenString = new AtomicReference<>();
        AtomicReference<Integer> seenInt = new AtomicReference<>();

        Thread thread = factory.newThread(() -> {
            seenString.set(STRING_KEY.isBound() ? STRING_KEY.get() : null);
            seenInt.set(INT_KEY.isBound() ? INT_KEY.get() : null);
        });
        thread.start();
        thread.join();

        assertThat(seenString.get()).isEqualTo("from-array-1");
        assertThat(seenInt.get()).isEqualTo(99);
    }

    @Test
    @DisplayName("Builder.scopedValues 空数组等同于不绑定，不应失败")
    void builder_scopedValues_emptyArray_doesNotFail() throws InterruptedException {
        VirtualThreadFactory factory = VirtualThreadFactory.builder()
                .namePrefix("empty-sv-")
                .scopedValues()
                .build();
        AtomicReference<Boolean> wasBound = new AtomicReference<>(Boolean.TRUE);
        AtomicReference<String> seenValue = new AtomicReference<>("__unread__");
        RuntimeException[] capturedError = new RuntimeException[1];

        Thread thread = factory.newThread(() -> {
            try {
                wasBound.set(STRING_KEY.isBound());
                seenValue.set(STRING_KEY.isBound() ? STRING_KEY.get() : null);
            } catch (RuntimeException ex) {
                capturedError[0] = ex;
            }
        });
        thread.start();
        thread.join();

        assertThat(capturedError[0])
                .as("空 bindings 不应抛出异常")
                .isNull();
        assertThat(wasBound.get())
                .as("空 bindings 时线程不应携带任何 ScopedValue")
                .isFalse();
        assertThat(seenValue.get())
                .as("未绑定键的 getOrElse 结果应为 null")
                .isNull();
    }

    // =====================================================================
    // 3. 并发
    // =====================================================================

    @Test
    @DisplayName("并发调用 newThread 100 次生成唯一线程名")
    void newThread_concurrentInvocation_uniqueThreadNames() {
        VirtualThreadFactory factory = VirtualThreadFactory.builder()
                .namePrefix("concurrent-")
                .build();
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        List<CompletableFuture<String>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < 100; i++) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> factory.newThread(() -> Thread.currentThread().getName()).getName(),
                        pool));
            }

            List<String> names = new ArrayList<>();
            for (CompletableFuture<String> future : futures) {
                names.add(future.join());
            }

            assertThat(names)
                    .as("100 次并发调用应产生 100 个线程名")
                    .hasSize(100)
                    .doesNotHaveDuplicates()
                    .allSatisfy(name -> assertThat(name).startsWith("concurrent-"));
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("通过工厂创建的线程能正确执行 Runnable 至完成")
    void newThread_taskExecutionRunsToCompletion() throws InterruptedException {
        VirtualThreadFactory factory = VirtualThreadFactory.of("task-");
        AtomicInteger counter = new AtomicInteger();
        CountDownLatch finished = new CountDownLatch(1);

        Thread thread = factory.newThread(() -> {
            counter.incrementAndGet();
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                counter.incrementAndGet();
                finished.countDown();
            }
        });
        thread.start();

        boolean done = finished.await(5, TimeUnit.SECONDS);
        thread.join();

        assertThat(done)
                .as("任务应在限定时间内完成")
                .isTrue();
        assertThat(counter.get())
                .as("Runnable 中的两处 incrementAndGet 都应执行完毕")
                .isEqualTo(2);
        assertThat(thread.getState())
                .as("join 之后线程应已退出")
                .isEqualTo(Thread.State.TERMINATED);
    }

    // =====================================================================
    // 4. 静态工厂
    // =====================================================================

    @Test
    @DisplayName("VirtualThreadFactory.of(prefix) 创建带前缀的工厂且无绑定")
    void of_validPrefix_createsFactoryWithPrefix() throws InterruptedException {
        VirtualThreadFactory factory = VirtualThreadFactory.of("of-prefix-");

        assertThat(factory)
                .as("of(...) 应返回非空工厂实例")
                .isNotNull();

        AtomicReference<Boolean> wasBound = new AtomicReference<>(Boolean.TRUE);
        Thread thread = factory.newThread(() -> wasBound.set(STRING_KEY.isBound()));
        thread.start();
        thread.join();

        assertThat(thread.getName())
                .as("of(prefix) 创建的工厂应使用指定前缀")
                .startsWith("of-prefix-");
        assertThat(wasBound.get())
                .as("of(...) 不应携带任何 ScopedValue 绑定")
                .isFalse();
    }

    @Test
    @DisplayName("无配置的 Builder 创建默认前缀且无绑定的工厂")
    void builder_noConfig_createsFactoryWithDefaults() throws InterruptedException {
        VirtualThreadFactory factory = VirtualThreadFactory.builder().build();

        assertThat(factory)
                .as("默认 Builder 应生成有效工厂")
                .isNotNull();

        AtomicReference<Boolean> wasBound = new AtomicReference<>(Boolean.TRUE);
        Thread thread = factory.newThread(() -> wasBound.set(STRING_KEY.isBound()));
        thread.start();
        thread.join();

        assertThat(thread.getName())
                .as("无配置时的默认前缀应为 vt-")
                .isEqualTo("vt-1");
        assertThat(thread.isVirtual())
                .as("默认 Builder 仍应生成虚拟线程")
                .isTrue();
        assertThat(wasBound.get())
                .as("无配置时不应携带任何 ScopedValue 绑定")
                .isFalse();
    }

    // =====================================================================
    // 5. 边界与异常
    // =====================================================================

    @Test
    @DisplayName("任务抛出异常时不影响调用方线程")
    void newThread_runnableThrowsOtherThreadNotAffected() throws InterruptedException {
        VirtualThreadFactory factory = VirtualThreadFactory.builder()
                .namePrefix("err-")
                .build();
        AtomicReference<Throwable> captured = new AtomicReference<>();
        boolean callerStillRunsNormally = false;

        Thread thread = factory.newThread(() -> {
            throw new IllegalStateException("task failed");
        });
        thread.setUncaughtExceptionHandler((t, ex) -> captured.set(ex));
        thread.start();
        thread.join();
        callerStillRunsNormally = true;

        assertThat(callerStillRunsNormally)
                .as("调用方线程不应受子线程异常影响而中止")
                .isTrue();
        assertThat(captured.get())
                .as("子线程未捕获异常应被处理器捕获")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("task failed");
        assertThat(Thread.currentThread().isInterrupted())
                .as("调用方线程的中断状态不应改变")
                .isFalse();
        assertThat(thread.getState())
                .as("异常抛出后子线程应已终止")
                .isEqualTo(Thread.State.TERMINATED);

        // 调用方线程仍能正常驱动后续逻辑：批量依次创建 3 个空任务线程并 join。
        List<Thread> followers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Thread follow = factory.newThread(() -> { /* no-op */ });
            followers.add(follow);
            follow.start();
        }
        for (Thread follow : followers) {
            follow.join();
        }
        IntStream.range(0, followers.size())
                .forEach(i -> assertThat(followers.get(i).getState())
                        .as("后续创建的线程也应正常终止")
                        .isEqualTo(Thread.State.TERMINATED));
    }
}
