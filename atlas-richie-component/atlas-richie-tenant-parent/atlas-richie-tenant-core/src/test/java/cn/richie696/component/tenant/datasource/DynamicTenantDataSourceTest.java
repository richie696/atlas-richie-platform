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
package cn.richie696.component.tenant.datasource;

import cn.richie696.component.tenant.context.DataSourceContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * DynamicTenantDataSource 单元测试。
 *
 * <p>直接用真实父构造器，验证生产代码在 Spring 7.x 下不再抛
 * {@code IllegalArgumentException("Property 'targetDataSources' is required")}。</p>
 */
@DisplayName("DynamicTenantDataSource — 动态租户数据源路由")
class DynamicTenantDataSourceTest {

    private DataSource sharedDs;
    private DataSource tenantDs;
    private DynamicTenantDataSource dynamicDs;

    @BeforeEach
    void setUp() {
        sharedDs = mock(DataSource.class);
        tenantDs = mock(DataSource.class);
        assertThatCode(() -> dynamicDs = new DynamicTenantDataSource(sharedDs))
                .doesNotThrowAnyException();
    }

    @AfterEach
    void tearDown() {
        DataSourceContextHolder.clear();
    }

    @Test
    @DisplayName("构造器注入的 shared 数据源暴露给 getSharedDataSource()")
    void getSharedDataSourceReturnsInjected() {
        assertThat(dynamicDs.getSharedDataSource()).isSameAs(sharedDs);
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
    @DisplayName("getTenantDataSources 返回只读副本")
    void getTenantDataSourcesIsReadOnly() {
        dynamicDs.addTenantDataSource("2001", tenantDs);
        Map<String, DataSource> map = dynamicDs.getTenantDataSources();
        // 尝试修改不应影响内部状态
        try {
            map.put("3001", sharedDs);
        } catch (UnsupportedOperationException ignored) {
            // 也允许抛出 UnsupportedOperationException
        }
        assertThat(dynamicDs.getTenantDataSources()).doesNotContainKey("3001");
    }
}
