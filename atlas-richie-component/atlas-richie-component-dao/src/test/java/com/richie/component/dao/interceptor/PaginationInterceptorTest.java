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
package com.richie.component.dao.interceptor;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.dialects.MySqlDialect;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaginationInterceptorTest {

    @Test
    void beforeQuery_returnsEarlyWhenPageMissing() {
        PaginationInterceptor interceptor = new PaginationInterceptor(DbType.MYSQL);
        MappedStatement ms = mock(MappedStatement.class);

        assertThatCode(() -> interceptor.beforeQuery(
                mock(Executor.class), ms, Map.of(), RowBounds.DEFAULT, null, mock(BoundSql.class)))
                .doesNotThrowAnyException();
    }

    @Test
    void getColumn_convertsCamelCaseToSnakeCase() throws Exception {
        PaginationInterceptor interceptor = new PaginationInterceptor(DbType.MYSQL);

        assertThat(invokeGetColumn(interceptor, "createTime")).isEqualTo("create_time");
        assertThat(invokeGetColumn(interceptor, "userId")).isEqualTo("user_id");
    }

    @Test
    void convertOrderItem_usesResultMappingColumnWhenPropertyMatches() throws Exception {
        PaginationInterceptor interceptor = new PaginationInterceptor(DbType.MYSQL);
        OrderItem order = OrderItem.desc("createTime");

        ResultMapping mapping = mock(ResultMapping.class);
        when(mapping.getProperty()).thenReturn("createTime");
        when(mapping.getColumn()).thenReturn("t_create_time");

        invokeConvertOrderItem(interceptor, List.of(order), List.of(mapping));

        assertThat(order.getColumn()).isEqualTo("t_create_time");
    }

    @Test
    void convertOrderItem_fallsBackToSnakeCaseWhenMappingMissing() throws Exception {
        PaginationInterceptor interceptor = new PaginationInterceptor(DbType.MYSQL);
        OrderItem order = OrderItem.asc("updateTime");

        invokeConvertOrderItem(interceptor, List.of(order), Collections.emptyList());

        assertThat(order.getColumn()).isEqualTo("update_time");
    }

    @Test
    void constructor_acceptsDbTypeAndDialect() {
        assertThat(new PaginationInterceptor(DbType.POSTGRE_SQL).getDbType()).isEqualTo(DbType.POSTGRE_SQL);
        assertThat(new PaginationInterceptor(new MySqlDialect()).getDialect()).isNotNull();
        assertThat(new PaginationInterceptor()).isNotNull();
    }

    @Test
    void convertOrderItem_keepsColumnWhenPropertyDoesNotMatchMapping() throws Exception {
        PaginationInterceptor interceptor = new PaginationInterceptor(DbType.MYSQL);
        OrderItem order = OrderItem.asc("unknownField");

        ResultMapping mapping = mock(ResultMapping.class);
        when(mapping.getProperty()).thenReturn("createTime");
        when(mapping.getColumn()).thenReturn("t_create_time");

        invokeConvertOrderItem(interceptor, List.of(order), List.of(mapping));

        assertThat(order.getColumn()).isEqualTo("unknown_field");
    }

    @Test
    void beforeQuery_usesResultMappingsWhenPresent() throws Exception {
        PaginationInterceptor interceptor = new PaginationInterceptor(DbType.MYSQL);
        OrderItem order = OrderItem.asc("createTime");

        ResultMapping resultMapping = mock(ResultMapping.class);
        when(resultMapping.getProperty()).thenReturn("createTime");
        when(resultMapping.getColumn()).thenReturn("t_create_time");

        invokeConvertOrderItem(interceptor, List.of(order), List.of(resultMapping));
        assertThat(order.getColumn()).isEqualTo("t_create_time");
    }

    private static String invokeGetColumn(PaginationInterceptor interceptor, String property) throws Exception {
        Method method = PaginationInterceptor.class.getDeclaredMethod("getColumn", String.class);
        method.setAccessible(true);
        return (String) method.invoke(interceptor, property);
    }

    @SuppressWarnings("unchecked")
    private static void invokeConvertOrderItem(
            PaginationInterceptor interceptor, List<OrderItem> orders, List<ResultMapping> mappings) throws Exception {
        Method method = PaginationInterceptor.class.getDeclaredMethod(
                "convertOrderItem", List.class, List.class);
        method.setAccessible(true);
        method.invoke(interceptor, orders, mappings);
    }
}
