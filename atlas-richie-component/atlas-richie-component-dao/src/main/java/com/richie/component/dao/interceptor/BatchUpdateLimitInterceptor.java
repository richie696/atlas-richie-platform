/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.dao.interceptor;

import com.richie.contract.exception.BusinessException;
import com.richie.component.dao.config.DaoProperties;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;

/**
 * 批量更新边界保护拦截器
 * <p>
 * 用于限制批量更新操作的记录数，防止误操作导致大量数据被更新。
 * 当批量更新的记录数超过配置的阈值时，将拒绝执行并抛出异常。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-09
 */
@Slf4j
@RequiredArgsConstructor
public class BatchUpdateLimitInterceptor implements InnerInterceptor {

    /**
     * 批量更新限制阈值
     */
    private final DaoProperties properties;

    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter) throws SQLException {
        // 只处理 UPDATE 操作
        if (ms.getSqlCommandType() != SqlCommandType.UPDATE) {
            return;
        }

        int updateCount = getBatchUpdateCount(ms, parameter);
        if (updateCount > properties.getBatchUpdateLimit()) {
            String errorMsg = String.format(
                "批量更新操作被拒绝：更新记录数 (%d) 超过配置的限制阈值 (%d)。请分批执行或调整配置参数 platform.component.dao.batch-update-limit",
                updateCount, properties.getBatchUpdateLimit()
            );
            log.warn("批量更新操作被拦截: MappedStatement={}, 更新记录数={}, 限制阈值={}",
                ms.getId(), updateCount, properties.getBatchUpdateLimit());
            throw new BusinessException(errorMsg);
        }

        if (updateCount > 1) {
            log.debug("批量更新操作通过检查: MappedStatement={}, 更新记录数={}, 限制阈值={}",
                ms.getId(), updateCount, properties.getBatchUpdateLimit());
        }
    }

    /**
     * 获取批量更新的记录数
     * <p>
     * 支持以下批量更新场景：
     * 1. updateBatchById(Collection) - 通过 Collection 参数传递多个实体
     * 2. update(Entity, Wrapper) - 通过 Wrapper 条件可能影响多行（通过 SQL 分析）
     * 3. 自定义批量更新 SQL - 通过 SQL 中的 IN 条件检测
     * 
     *
     * @param ms        MappedStatement
     * @param parameter 参数对象
     * @return 更新的记录数，如果无法确定则返回 1（单条更新）
     */
    private int getBatchUpdateCount(MappedStatement ms, Object parameter) {
        switch (parameter) {
            case null -> {
                return 1;
            }
            // 情况1: 参数是 Collection 类型（如 List），表示批量更新
            // 这是 MyBatis Plus 的 updateBatchById 方法的常见场景
            case Collection<?> collection -> {
                return collection.size();
            }
            // 情况2: 参数是 Map，需要区分单条更新和批量更新
            // - updateById(Entity) 等单条更新：Map 中包含 param1/et 等键，值为实体对象
            // - updateBatchById(Collection) 等批量更新：Map 中包含 collection/list 等键，值为 Collection
            case Map<?, ?> map -> {
                // 先安全地检查是否存在批量更新的 Collection 参数
                // 使用 containsKey 避免触发 MyBatis 的参数绑定检查
                Object collectionParam = null;
                if (map.containsKey("collection")) {
                    collectionParam = map.get("collection");
                } else if (map.containsKey("list")) {
                    collectionParam = map.get("list");
                } else if (map.containsKey("coll")) {
                    collectionParam = map.get("coll");
                }

                // 如果找到 Collection 类型的参数，说明是批量更新
                if (collectionParam instanceof Collection<?> collection) {
                    return collection.size();
                }

                // 遍历所有值，查找 Collection 类型（处理其他可能的批量更新场景）
                for (Object value : map.values()) {
                    if (value instanceof Collection<?> collection) {
                        int size = collection.size();
                        if (size > 1) {
                            return size;
                        }
                    }
                }

                // 如果没有找到 Collection 类型，且 Map 中包含常见的单条更新参数（param1, et 等）
                // 则认为是单条更新，直接返回 1
                // 这样可以避免对 updateById 等方法的误判
                if (map.containsKey("param1") || map.containsKey("et") || map.containsKey("ew")) {
                    return 1;
                }
            }
            default -> {
            }
        }

        // 情况3: 通过 BoundSql 分析 SQL，检查是否有 IN 条件（可能表示批量更新）
        // 注意：这种方式不够精确，因为 IN 条件可能只匹配一条记录
        // 但为了安全起见，如果检测到 IN 条件，我们尝试从参数中提取数量
        try {
            BoundSql boundSql = ms.getBoundSql(parameter);
            String sql = boundSql.getSql().toLowerCase();

            // 检查 SQL 中是否有明显的批量更新模式
            // 例如：WHERE id IN (?, ?, ?) 或 WHERE id IN (#{ids})
            if (sql.contains(" in ") || sql.contains(" in(")) {
                // 尝试从参数中提取 IN 条件的值数量
                int inCount = extractInClauseCount(parameter);
                if (inCount > 0) {
                    return inCount;
                }
                // 如果无法确定，返回 1，避免误拦截
                // 对于通过 Wrapper 的批量更新，可能无法准确检测，需要业务层控制
                return 1;
            }
        } catch (Exception e) {
            log.debug("分析 SQL 时发生异常，忽略: {}", e.getMessage());
        }

        // 默认返回 1，表示单条更新
        return 1;
    }

    /**
     * 从参数中提取 IN 条件的值数量
     *
     * @param parameter 参数对象
     * @return IN 条件的值数量，如果无法确定则返回 0
     */
    private int extractInClauseCount(Object parameter) {
        try {
            // 尝试从参数映射中查找 Collection 类型的值
            if (parameter instanceof Map<?, ?> map) {
                // 检查常见的 IN 条件参数名，使用 containsKey 安全检查
                String[] commonInParamNames = {"ids", "idList", "idCollection", "list", "collection"};
                for (String paramName : commonInParamNames) {
                    if (map.containsKey(paramName)) {
                        Object value = map.get(paramName);
                        if (value instanceof Collection<?> collection) {
                            return collection.size();
                        }
                    }
                }

                // 遍历所有值，查找 Collection 类型
                for (Object value : map.values()) {
                    if (value instanceof Collection<?> collection) {
                        return collection.size();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("提取 IN 条件数量时发生异常: {}", e.getMessage());
        }
        return 0;
    }
}
