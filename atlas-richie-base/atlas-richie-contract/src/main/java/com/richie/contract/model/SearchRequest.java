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
package com.richie.contract.model;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Getter;
import lombok.Setter;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 查询参数模型类
 *
 * @param <T> 查询参数模型类
 * @author richie696
 * @version 1.0
 * @since 2024-12-12 11:04:37
 */
public class SearchRequest<T> {

    /**
     * 查询参数
     */
    @Getter
    @Setter
    protected T data;

    /**
     * 分页信息
     */
    @Setter
    protected PageInfo page;

    /**
     * 默认构造函数
     */
    public SearchRequest() {
    }

    /**
     * 获取分页信息
     * @return 分页信息
     */
    @Nullable
    public Page<T> getPage() {
        if (page == null) {
            return null;
        }
        Page<T> page = new Page<>(this.page.currentPage, this.page.pageSize);
        Optional.ofNullable(this.page.orderFields).ifPresent(orderFields -> page.addOrder(orderFields.stream()
                .map(field -> new OrderItem().setColumn(field.column).setAsc(field.asc))
                .toArray(OrderItem[]::new)));
        return page;
    }

    /**
     * 分页信息
     * @param currentPage   当前页
     * @param pageSize      每页显示的条数
     * @param orderFields   排序字段
     */
    public record PageInfo(
            int currentPage,
            int pageSize,
            List<OrderField> orderFields
    ) {}

    /**
     * 排序字段
     * @param column   字段名
     * @param asc      是否升序
     */
    public record OrderField(
            String column,
            boolean asc
    ) {}
}
