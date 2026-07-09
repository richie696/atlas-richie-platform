/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.reload;

import com.richie.component.web.core.hook.HookBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认 {@link HotReloadRegistry} 实现（README.md §4.6）。
 * <p>
 * 线程安全（{@link ConcurrentHashMap}）；reload 时若传入 {@link HookBus}，
 * 自动发布 {@link ReloadEvent}。
 *
 * @author richie696
 * @since 2026-07
 */
public class DefaultHotReloadRegistry implements HotReloadRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultHotReloadRegistry.class);

    private final Map<String, Reloadable<?>> reloadableMap = new ConcurrentHashMap<>();
    private final HookBus hookBus;

    public DefaultHotReloadRegistry() {
        this(null);
    }

    public DefaultHotReloadRegistry(HookBus hookBus) {
        this.hookBus = hookBus;
    }

    @Override
    public void register(String name, Reloadable<?> reloadable) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (reloadable == null) {
            throw new IllegalArgumentException("reloadable must not be null");
        }
        Reloadable<?> previous = reloadableMap.put(name, reloadable);
        if (previous != null) {
            log.warn("HotReloadRegistry.register: replaced existing Reloadable for name={}", name);
        }
        log.info("HotReloadRegistry.register: name={} type={}", name, reloadable.getClass().getSimpleName());
    }

    @Override
    public void unregister(String name) {
        reloadableMap.remove(name);
    }

    @Override
    public Reloadable<?> get(String name) {
        return reloadableMap.get(name);
    }

    @Override
    public Set<String> names() {
        return Set.copyOf(reloadableMap.keySet());
    }

    @Override
    public boolean reload(String name) {
        Reloadable<?> r = reloadableMap.get(name);
        if (r == null) {
            log.warn("HotReloadRegistry.reload: no Reloadable for name={}", name);
            return false;
        }
        triggerAccept(r);
        publish(name);
        return true;
    }

    @Override
    public int reloadAll() {
        int count = 0;
        for (String name : reloadableMap.keySet()) {
            if (reload(name)) {
                count++;
            }
        }
        return count;
    }

    private static void triggerAccept(Reloadable<?> r) {
        try {
            Object state = r.currentState();
            acceptState(r, state);
        } catch (RuntimeException e) {
            log.error("HotReload accept failed for {}: {}", r.name(), e.getMessage(), e);
            throw e;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void acceptState(Reloadable r, Object state) {
        r.accept(state);
    }

    private void publish(String name) {
        if (hookBus != null) {
            try {
                hookBus.publish(ReloadEvent.of(name));
            } catch (RuntimeException e) {
                log.warn("ReloadEvent publish failed for name={}: {}", name, e.getMessage());
            }
        }
    }
}