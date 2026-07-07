package com.richie.component.web.core.reload;

import com.richie.component.web.core.hook.DefaultHookBus;
import com.richie.component.web.core.hook.HookBus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HotReloadIntegrationTest {

    @Test
    void reload_publishesEventToHookBus() {
        HookBus hookBus = new DefaultHookBus();
        DefaultHotReloadRegistry registry = new DefaultHotReloadRegistry(hookBus);
        List<ReloadEvent> events = new ArrayList<>();
        hookBus.subscribe(ReloadEvent.class, events::add);

        registry.register("interceptor-a", new Reloadable<String>() {
            @Override public String currentState() { return "s"; }
            @Override public void accept(String newState) {}
            @Override public String name() { return "interceptor-a"; }
        });

        registry.reload("interceptor-a");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).name()).isEqualTo("interceptor-a");
    }

    @Test
    void reloadAll_publishesForEach() {
        HookBus hookBus = new DefaultHookBus();
        DefaultHotReloadRegistry registry = new DefaultHotReloadRegistry(hookBus);
        AtomicInteger eventCount = new AtomicInteger();
        hookBus.subscribe(ReloadEvent.class, e -> eventCount.incrementAndGet());

        registry.register("a", stub());
        registry.register("b", stub());
        registry.register("c", stub());

        int reloaded = registry.reloadAll();
        assertThat(reloaded).isEqualTo(3);
        assertThat(eventCount.get()).isEqualTo(3);
    }

    @Test
    void reload_subscriberException_doesNotBreakReload() {
        HookBus hookBus = new DefaultHookBus();
        DefaultHookBus realBus = (DefaultHookBus) hookBus;
        DefaultHotReloadRegistry registry = new DefaultHotReloadRegistry(realBus);

        hookBus.subscribe(ReloadEvent.class, e -> { throw new RuntimeException("boom"); });

        registry.register("a", stub());
        registry.reload("a");
    }

    private static Reloadable<String> stub() {
        return new Reloadable<String>() {
            @Override public String currentState() { return "x"; }
            @Override public void accept(String newState) {}
        };
    }
}