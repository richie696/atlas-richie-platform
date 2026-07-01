package com.richie.component.storage.observability;

import com.richie.component.storage.core.StorageEngine;
import com.richie.component.storage.core.StorageEngineRegistry;
import com.richie.component.storage.enums.StorageEngineEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 存储引擎健康指示器（Spring Boot Actuator）
 * <p>
 * 通过 {@code /actuator/health/storage} 端点暴露存储引擎整体健康状态。
 * <ul>
 *   <li>未初始化 → {@code DOWN}（包含 {@code reason=uninitialized}）</li>
 *   <li>已注册至少一个引擎 → {@code UP}（details 列出每个类型与实现类名）</li>
 * </ul>
 * 引擎实例可达性不在本类检查范围内（避免每次健康检查都发起远端请求）。
 *
 * @author richie696
 */
@Slf4j
@RequiredArgsConstructor
public class StorageHealthIndicator implements HealthIndicator {

    private final StorageEngineRegistry registry;

    @Override
    public Health health() {
        if (!registry.isInitialized()) {
            return Health.down()
                    .withDetail("reason", "uninitialized")
                    .build();
        }

        Map<StorageEngineEnum, StorageEngine> snapshot = registry.snapshot();
        Map<String, Object> engines = new LinkedHashMap<>();
        snapshot.forEach((type, engine) ->
                engines.put(type.name(), engine.getClass().getSimpleName()));

        return Health.up()
                .withDetail("defaultEngine", registry.getCurrentEngineType())
                .withDetail("defaultEngineId", registry.getDefaultEngineId())
                .withDetail("engines", engines)
                .build();
    }
}
