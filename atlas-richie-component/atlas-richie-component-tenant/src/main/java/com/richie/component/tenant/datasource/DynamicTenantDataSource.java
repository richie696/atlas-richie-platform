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
package com.richie.component.tenant.datasource;

import com.richie.component.tenant.context.DataSourceContextHolder;
import lombok.Getter;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态租户数据源。
 *
 * <p>基于 Spring {@link AbstractRoutingDataSource}，根据 {@link DataSourceContextHolder}
 * 中的 key 路由到对应的数据源。key 约定为 {@code "shared"}（共享库）或
 * {@code String.valueOf(tenantId)}（租户独立库）。</p>
 *
 * <p>支持运行时动态增删数据源，操作受 {@code synchronized} 保护，
 * 并通过 {@code afterPropertiesSet()} 刷新内部解析缓存。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class DynamicTenantDataSource extends AbstractRoutingDataSource {

    private final Map<String, DataSource> tenantDataSources = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    /**
     *  获取共享数据源
     */
    @Getter
    private final DataSource sharedDataSource;

    public DynamicTenantDataSource(DataSource sharedDataSource) {
        this.sharedDataSource = sharedDataSource;
        setDefaultTargetDataSource(sharedDataSource);
        afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        String key = DataSourceContextHolder.get();
        return key != null ? key : "shared";
    }

    /**
     * 动态添加租户数据源（运行时）。
     *
     * @param dataSourceKey 数据源 key（{@code String.valueOf(tenantId)}）
     * @param dataSource    数据源实例
     */
    public void addTenantDataSource(String dataSourceKey, DataSource dataSource) {
        synchronized (lock) {
            tenantDataSources.put(dataSourceKey, dataSource);
            rebuildTargetDataSources();
        }
    }

    /**
     * 删除租户数据源（谨慎操作）。
     *
     * @param dataSourceKey 数据源 key
     */
    public void removeTenantDataSource(String dataSourceKey) {
        synchronized (lock) {
            tenantDataSources.remove(dataSourceKey);
            rebuildTargetDataSources();
        }
    }

    /**
     * 获取所有已注册的数据源（只读视图）。
     *
     * @return 数据源 Map（key → DataSource）
     */
    public Map<String, DataSource> getTenantDataSources() {
        return Map.copyOf(tenantDataSources);
    }

    private void rebuildTargetDataSources() {
        Map<Object, Object> targetMap = new HashMap<>(tenantDataSources);
        targetMap.put("shared", sharedDataSource);
        setTargetDataSources(targetMap);
        afterPropertiesSet();
    }
}
