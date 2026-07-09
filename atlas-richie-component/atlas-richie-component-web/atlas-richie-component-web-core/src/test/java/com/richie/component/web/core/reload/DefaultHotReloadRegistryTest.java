/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.reload;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultHotReloadRegistryTest {

    @Test
    void register_andGet() {
        DefaultHotReloadRegistry registry = new DefaultHotReloadRegistry();
        Reloadable<String> r = new StringReloadable("initial");
        registry.register("test", r);
        assertThat(registry.get("test")).isSameAs(r);
        assertThat(registry.names()).containsExactly("test");
    }

    @Test
    void register_nullName_throws() {
        DefaultHotReloadRegistry registry = new DefaultHotReloadRegistry();
        assertThatThrownBy(() -> registry.register(null, new StringReloadable("x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.register("", new StringReloadable("x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void register_nullReloadable_throws() {
        DefaultHotReloadRegistry registry = new DefaultHotReloadRegistry();
        assertThatThrownBy(() -> registry.register("test", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unregister_removes() {
        DefaultHotReloadRegistry registry = new DefaultHotReloadRegistry();
        registry.register("test", new StringReloadable("x"));
        registry.unregister("test");
        assertThat(registry.names()).isEmpty();
    }

    @Test
    void reload_triggersAccept() {
        DefaultHotReloadRegistry registry = new DefaultHotReloadRegistry();
        AtomicInteger accepts = new AtomicInteger();
        Reloadable<String> r = new Reloadable<>() {
            private String current = "initial";
            @Override public String currentState() { return current; }
            @Override public void accept(String newState) {
                accepts.incrementAndGet();
                this.current = newState;
            }
        };
        registry.register("test", r);
        assertThat(registry.reload("test")).isTrue();
        assertThat(accepts.get()).isEqualTo(1);
        assertThat(r.currentState()).isEqualTo("initial");
    }

    @Test
    void reload_unknownName_returnsFalse() {
        DefaultHotReloadRegistry registry = new DefaultHotReloadRegistry();
        assertThat(registry.reload("nope")).isFalse();
    }

    @Test
    void reloadAll_returnsCount() {
        DefaultHotReloadRegistry registry = new DefaultHotReloadRegistry();
        registry.register("a", new StringReloadable("a"));
        registry.register("b", new StringReloadable("b"));
        registry.register("c", new StringReloadable("c"));
        assertThat(registry.reloadAll()).isEqualTo(3);
    }

    @Test
    void register_replacesExisting_warns() {
        DefaultHotReloadRegistry registry = new DefaultHotReloadRegistry();
        registry.register("test", new StringReloadable("first"));
        registry.register("test", new StringReloadable("second"));
        assertThat(registry.names()).hasSize(1);
    }

    private static class StringReloadable implements Reloadable<String> {
        private final String state;
        StringReloadable(String state) { this.state = state; }
        @Override public String currentState() { return state; }
        @Override public void accept(String newState) {}
        @Override public String name() { return "StringReloadable"; }
    }
}