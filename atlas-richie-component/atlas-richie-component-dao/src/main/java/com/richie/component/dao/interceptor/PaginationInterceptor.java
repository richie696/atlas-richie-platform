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
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.ParameterUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.dialects.IDialect;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.List;

/**
 * 分页拦截器，继承 MyBatis-Plus PaginationInnerInterceptor，支持按 DbType 或自定义方言分页。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-09
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PaginationInterceptor extends PaginationInnerInterceptor {

    public PaginationInterceptor(DbType dbType) {
        setDbType(dbType);
    }

    public PaginationInterceptor(IDialect dialect) {
        setDialect(dialect);
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        IPage<?> page = ParameterUtils.findPage(parameter).orElse(null);
        if (page == null) {
            return;
        }
        var orders = page.orders();
        // 如果没有ResultMap则直接转换驼峰
        if (ms.getResultMaps().isEmpty()) {
            orders.forEach(item -> item.setColumn(getColumn(item.getColumn())));
            super.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, boundSql);
            return;
        }
        // 获取映射关系
        var resultMappings = ms.getResultMaps().getFirst().getResultMappings();
        if (resultMappings.isEmpty()) {
            orders.forEach(item -> item.setColumn(getColumn(item.getColumn())));
            super.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, boundSql);
            return;
        }
        convertOrderItem(orders, resultMappings);
        super.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, boundSql);
    }

    private void convertOrderItem(List<OrderItem> orderItems, List<ResultMapping> resultMappings) {
        orderItems.forEach(item -> {
            var resultMapping = resultMappings.stream().filter(o -> o.getProperty().equals(item.getColumn())).findFirst().orElse(null);
            if (resultMapping != null) {
                // 如果存在映射关系则直接使用
                item.setColumn(resultMapping.getColumn());
            } else {
                // 否则自动转换驼峰
                item.setColumn(getColumn(item.getColumn()));
            }
        });
    }

    private String getColumn(String property) {
        return property.replaceAll("([A-Z])", "_$1").toLowerCase();
    }
}
