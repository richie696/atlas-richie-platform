package com.richie.component.web.core.degrade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DefaultDegradeStrategyRegistry} 测试。
 */
class DefaultDegradeStrategyRegistryTest {

    private DefaultDegradeStrategyRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DefaultDegradeStrategyRegistry();
    }

    @Test
    void register_unregister_select_basic() {
        DegradeStrategy s = strategy("a", 0, Trigger.EXCEPTION);
        registry.register("a", s);

        assertThat(registry.all()).hasSize(1);
        Optional<DegradeStrategy> hit = registry.select(Trigger.EXCEPTION);
        assertThat(hit).isPresent();
        assertThat(hit.get().name()).isEqualTo("a");

        registry.unregister("a");
        assertThat(registry.all()).isEmpty();
        assertThat(registry.select(Trigger.EXCEPTION)).isEmpty();
    }

    @Test
    void select_orderAscending() {
        registry.register("z", strategy("z", 100, Trigger.EXCEPTION));
        registry.register("a", strategy("a", 0, Trigger.EXCEPTION));
        registry.register("m", strategy("m", 50, Trigger.EXCEPTION));

        Optional<DegradeStrategy> hit = registry.select(Trigger.EXCEPTION);
        assertThat(hit).isPresent();
        assertThat(hit.get().name()).isEqualTo("a");
    }

    @Test
    void select_noMatch_returnsEmpty() {
        registry.register("e-only", strategy("e-only", 0, Trigger.EXCEPTION));
        assertThat(registry.select(Trigger.CUSTOM)).isEmpty();
        assertThat(registry.select(Trigger.HIGH_LATENCY)).isEmpty();
    }

    @Test
    void select_tieBreakByName() {
        DegradeStrategy a = strategy("a", 0, Trigger.EXCEPTION);
        DegradeStrategy b = strategy("b", 0, Trigger.EXCEPTION);
        registry.register("b", b);
        registry.register("a", a);

        Optional<DegradeStrategy> hit = registry.select(Trigger.EXCEPTION);
        assertThat(hit).isPresent();
        assertThat(hit.get().name()).isEqualTo("a");
    }

    @Test
    void register_replaceOverwrites() {
        DegradeStrategy first = strategy("s", 0, Trigger.EXCEPTION);
        DegradeStrategy second = strategy("s", 0, Trigger.EXCEPTION);
        registry.register("s", first);
        registry.register("s", second);

        assertThat(registry.all()).hasSize(1);
        assertThat(registry.select(Trigger.EXCEPTION).get()).isSameAs(second);
    }

    @Test
    void register_nullArg_throws() {
        assertThatThrownBy(() -> registry.register(null, strategy("x", 0, Trigger.EXCEPTION)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registry.register("x", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void all_returnsSortedView() {
        registry.register("zzz", strategy("zzz", 100, Trigger.CUSTOM));
        registry.register("aaa", strategy("aaa", 0, Trigger.EXCEPTION));

        var all = registry.all();
        assertThat(all.get(0).name()).isEqualTo("aaa");
        assertThat(all.get(1).name()).isEqualTo("zzz");
    }

    @Test
    void emptyRegistry_select_returnsEmpty() {
        assertThat(registry.select(Trigger.EXCEPTION)).isEmpty();
        assertThat(registry.all()).isEmpty();
    }

    // ─────────────────────── helpers ───────────────────────

    private static DegradeStrategy strategy(String name, int order, Trigger trigger) {
        return new DegradeStrategy() {
            @Override public String name() { return name; }
            @Override public Set<Trigger> triggers() { return Set.of(trigger); }
            @Override public int order() { return order; }
            @Override public boolean matches(Trigger t) { return t == trigger; }
            @Override public DegradeResult build(Trigger t, Map<String, Object> ctx) {
                return DegradeResult.of(503, "degraded:" + name, name);
            }
        };
    }
}