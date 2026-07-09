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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

@DisplayName("DynamicTenantDataSource — 动态租户数据源路由")
class DynamicTenantDataSourceTest {

    private DataSource sharedDs;
    private DataSource tenantDs;
    private DynamicTenantDataSource dynamicDs;

    @BeforeEach
    void setUp() throws Exception {
        sharedDs = mock(DataSource.class);
        tenantDs = mock(DataSource.class);
        // 绕过 AbstractRoutingDataSource.afterPropertiesSet() 对 targetDataSources 的强制校验
        dynamicDs = mock(DynamicTenantDataSource.class, withSettings()
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        setField(dynamicDs, "sharedDataSource", sharedDs);
        setField(dynamicDs, "tenantDataSources", new ConcurrentHashMap<>());
        setField(dynamicDs, "lock", new Object());
        // add/removeTenantDataSource 内部调用 rebuildTargetDataSources → afterPropertiesSet，
        // 测试只需验证 tenantDataSources 管理逻辑，stub 掉父类初始化
        doNothing().when(dynamicDs).setTargetDataSources(Mockito.anyMap());
        doNothing().when(dynamicDs).afterPropertiesSet();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> cls = target.getClass();
        while (cls != null) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    @AfterEach
    void tearDown() {
        DataSourceContextHolder.clear();
    }

    @Test
    @DisplayName("determineCurrentLookupKey() 默认返回 shared")
    void defaultLookupKeyIsShared() {
        assertThat(dynamicDs.determineCurrentLookupKey()).isEqualTo("shared");
    }

    @Test
    @DisplayName("determineCurrentLookupKey() 使用 DataSourceContextHolder 中的 key")
    void lookupKeyFromContext() {
        DataSourceContextHolder.set("1001");
        assertThat(dynamicDs.determineCurrentLookupKey()).isEqualTo("1001");
    }

    @Test
    @DisplayName("addTenantDataSource 添加后可在 tenantDataSources 中找到")
    void addTenantDataSource() {
        dynamicDs.addTenantDataSource("1001", tenantDs);
        Map<String, DataSource> all = dynamicDs.getTenantDataSources();
        assertThat(all).containsKey("1001");
        assertThat(all.get("1001")).isSameAs(tenantDs);
    }

    @Test
    @DisplayName("removeTenantDataSource 后从 tenantDataSources 中移除")
    void removeTenantDataSource() {
        dynamicDs.addTenantDataSource("1001", tenantDs);
        dynamicDs.removeTenantDataSource("1001");
        assertThat(dynamicDs.getTenantDataSources()).doesNotContainKey("1001");
    }

    @Test
    @DisplayName("getSharedDataSource 返回构造时注入的数据源")
    void getSharedDataSourceReturnsInjected() {
        assertThat(dynamicDs.getSharedDataSource()).isSameAs(sharedDs);
    }

    @Test
    @DisplayName("getTenantDataSources 返回只读副本")
    void getTenantDataSourcesIsReadOnly() {
        dynamicDs.addTenantDataSource("2001", tenantDs);
        Map<String, DataSource> map = dynamicDs.getTenantDataSources();
        // 尝试修改不应影响内部状态
        try { map.put("3001", sharedDs); } catch (UnsupportedOperationException ignored) {}
        assertThat(dynamicDs.getTenantDataSources()).doesNotContainKey("3001");
    }
}
