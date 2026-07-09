/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.degrade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link DegradeStrategyRegistry} 默认实现（README.md §4.7）。
 * <p>
 * 内部维护：
 * <ul>
 *   <li>{@code Map<String, DegradeStrategy>}：name → strategy</li>
 *   <li>{@code CopyOnWriteArrayList<DegradeStrategy>}：按 {@code order} 升序的快照</li>
 * </ul>
 * <p>
 * 每次 {@link #register} / {@link #unregister} 都重排序快照；{@link #select} 在快照上遍历，O(N)。
 *
 * @author richie696
 * @since 2026-07
 */
public class DefaultDegradeStrategyRegistry implements DegradeStrategyRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultDegradeStrategyRegistry.class);

    private final Map<String, DegradeStrategy> byName = new ConcurrentHashMap<>();
    private final List<DegradeStrategy> sorted = new CopyOnWriteArrayList<>();

    @Override
    public void register(String name, DegradeStrategy strategy) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");
        DegradeStrategy previous = byName.put(name, strategy);
        if (previous != null) {
            log.info("DegradeStrategyRegistry: replaced name={} ({} -> {})",
                    name, previous.getClass().getSimpleName(), strategy.getClass().getSimpleName());
        } else {
            log.info("DegradeStrategyRegistry: registered name={} type={} order={}",
                    name, strategy.getClass().getSimpleName(), strategy.order());
        }
        rebuild();
    }

    @Override
    public void unregister(String name) {
        if (byName.remove(name) != null) {
            rebuild();
            log.info("DegradeStrategyRegistry: unregistered name={}", name);
        }
    }

    @Override
    public Optional<DegradeStrategy> select(Trigger trigger) {
        for (DegradeStrategy s : sorted) {
            if (s.matches(trigger)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<DegradeStrategy> all() {
        return new ArrayList<>(sorted);
    }

    private void rebuild() {
        List<DegradeStrategy> next = new ArrayList<>(byName.values());
        next.sort(Comparator.comparingInt(DegradeStrategy::order)
                .thenComparing(DegradeStrategy::name));
        sorted.clear();
        sorted.addAll(next);
    }
}