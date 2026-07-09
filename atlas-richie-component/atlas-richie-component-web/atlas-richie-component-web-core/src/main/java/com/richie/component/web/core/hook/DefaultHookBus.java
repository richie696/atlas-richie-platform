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
package com.richie.component.web.core.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 默认 {@link HookBus} 实现（README.md §4.5 HookBus）。
 * <p>
 * 同步发布 + 异常隔离 + 多订阅者 fan-out。线程安全（订阅者列表 CopyOnWriteArrayList，
 * 类型映射 ConcurrentHashMap）。
 *
 * <h2>实现细节</h2>
 * <ul>
 *   <li>publish 遍历订阅者列表，逐个调用 {@code onEvent}，异常捕获 + 日志</li>
 *   <li>subscribe 返 {@link Subscription}，调其 {@code unsubscribe()} 反注册</li>
 *   <li>diagnosticView 返 "[eventType]→N" 形式</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
public class DefaultHookBus implements HookBus {

    private static final Logger log = LoggerFactory.getLogger(DefaultHookBus.class);

    private final Map<Class<? extends HookEvent>, CopyOnWriteArrayList<HookSubscriber<? extends HookEvent>>> subscribers =
            new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <E extends HookEvent> Subscription subscribe(Class<E> eventType, HookSubscriber<E> subscriber) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(subscriber, "subscriber must not be null");
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(subscriber);
        log.debug("HookBus.subscribe: type={} subscriber={} (size now={})",
                eventType.getSimpleName(), subscriber.getClass().getSimpleName(),
                subscribers.get(eventType).size());
        return () -> unsubscribe(eventType, subscriber);
    }

    @Override
    public void publish(HookEvent event) {
        if (event == null) {
            return;
        }
        CopyOnWriteArrayList<HookSubscriber<? extends HookEvent>> list = subscribers.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return;
        }
        for (HookSubscriber<? extends HookEvent> subscriber : list) {
            try {
                publishOne(event, subscriber);
            } catch (RuntimeException e) {
                log.warn("HookBus subscriber threw (event={}, subscriber={}): {}",
                        event.getClass().getSimpleName(),
                        subscriber.getClass().getSimpleName(),
                        e.getMessage(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends HookEvent> void publishOne(HookEvent event, HookSubscriber<? extends HookEvent> subscriber) {
        ((HookSubscriber<E>) subscriber).onEvent((E) event);
    }

    private <E extends HookEvent> void unsubscribe(Class<E> eventType, HookSubscriber<E> subscriber) {
        CopyOnWriteArrayList<HookSubscriber<? extends HookEvent>> list = subscribers.get(eventType);
        if (list != null) {
            list.remove(subscriber);
        }
    }

    @Override
    public List<String> diagnosticView() {
        return subscribers.entrySet().stream()
                .map(e -> e.getKey().getSimpleName() + "→" + e.getValue().size())
                .sorted()
                .toList();
    }
}