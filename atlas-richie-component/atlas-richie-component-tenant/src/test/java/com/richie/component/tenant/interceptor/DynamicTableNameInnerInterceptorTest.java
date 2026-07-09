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
package com.richie.component.tenant.interceptor;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TableSuffixHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DynamicTableNameInnerInterceptor — 表名改写 (Table 模式)")
class DynamicTableNameInnerInterceptorTest {

    private MultiTenancyProperties props;
    private DynamicTableNameInnerInterceptor interceptor;

    @BeforeEach
    void setUp() {
        props = new MultiTenancyProperties();
        interceptor = new DynamicTableNameInnerInterceptor(props);
    }

    @AfterEach
    void tearDown() {
        TableSuffixHolder.clear();
    }

    /**
     * 直接测试 rewriteTableNames 逻辑（通过反射调用 private 方法）。
     */
    private String rewriteTableNames(String sql, String suffix) throws Exception {
        var method = DynamicTableNameInnerInterceptor.class.getDeclaredMethod(
                "rewriteTableNames", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(interceptor, sql, suffix);
    }

    @Nested
    @DisplayName("SELECT 表名改写")
    class SelectRewrite {

        @Test
        @DisplayName("简单 SELECT FROM 追加表后缀")
        void simpleSelectAppendsSuffix() throws Exception {
            String result = rewriteTableNames("SELECT * FROM t_order", "_100");
            assertThat(result.toLowerCase()).contains("from t_order_100");
        }

        @Test
        @DisplayName("SELECT JOIN 多表均追加后缀")
        void selectWithJoinAppendsSuffixToAllTables() throws Exception {
            String result = rewriteTableNames(
                    "SELECT * FROM t_order o JOIN t_user u ON o.user_id = u.id", "_100");
            assertThat(result.toLowerCase()).contains("t_order_100");
            assertThat(result.toLowerCase()).contains("t_user_100");
        }

        @Test
        @DisplayName("多 JOIN 全部改写")
        void selectWithMultipleJoins() throws Exception {
            String result = rewriteTableNames(
                    "SELECT * FROM a JOIN b ON a.id = b.a_id JOIN c ON b.id = c.b_id", "_v2");
            assertThat(result.toLowerCase()).contains("from a_v2");
            assertThat(result.toLowerCase()).contains("join b_v2");
            assertThat(result.toLowerCase()).contains("join c_v2");
        }

        @Test
        @DisplayName("忽略表不追加后缀")
        void ignoredTableSkipped() throws Exception {
            props.setIgnoreTables(List.of("dict_table"));
            String result = rewriteTableNames(
                    "SELECT * FROM dict_table JOIN t_user u ON dict_table.id = u.dict_id", "_100");
            assertThat(result.toLowerCase()).contains("from dict_table ");
            assertThat(result.toLowerCase()).contains("join t_user_100");
            assertThat(result.toLowerCase()).doesNotContain("dict_table_100");
        }
    }

    @Nested
    @DisplayName("UPDATE 表名改写")
    class UpdateRewrite {

        @Test
        @DisplayName("UPDATE 追加表后缀")
        void updateAppendsSuffix() throws Exception {
            String result = rewriteTableNames("UPDATE t_order SET status = 2", "_100");
            assertThat(result.toLowerCase()).contains("update t_order_100");
        }

        @Test
        @DisplayName("UPDATE 带 WHERE 不影响表名改写")
        void updateWithWhereStillAppendsSuffix() throws Exception {
            String result = rewriteTableNames(
                    "UPDATE t_order SET status = 2 WHERE id = 100", "_v2");
            assertThat(result.toLowerCase()).contains("update t_order_v2");
            assertThat(result.toLowerCase()).contains("where id = 100");
        }
    }

    @Nested
    @DisplayName("DELETE 表名改写")
    class DeleteRewrite {

        @Test
        @DisplayName("DELETE 追加表后缀")
        void deleteAppendsSuffix() throws Exception {
            String result = rewriteTableNames("DELETE FROM t_order", "_100");
            assertThat(result.toLowerCase()).contains("delete from t_order_100");
        }

        @Test
        @DisplayName("DELETE 带 WHERE 不影响表名改写")
        void deleteWithWhereStillAppendsSuffix() throws Exception {
            String result = rewriteTableNames(
                    "DELETE FROM t_order WHERE id = 5", "_v2");
            assertThat(result.toLowerCase()).contains("from t_order_v2");
            assertThat(result.toLowerCase()).contains("where id = 5");
        }
    }

    @Nested
    @DisplayName("INSERT 表名改写")
    class InsertRewrite {

        @Test
        @DisplayName("INSERT INTO 追加表后缀")
        void insertAppendsSuffix() throws Exception {
            String result = rewriteTableNames(
                    "INSERT INTO t_order (id, name) VALUES (1, 'a')", "_100");
            assertThat(result.toLowerCase()).contains("into t_order_100");
        }
    }

    @Nested
    @DisplayName("幂等保护")
    class Idempotency {

        @Test
        @DisplayName("已含后缀的表名不重复追加")
        void alreadySuffixedNotDoubleAppended() throws Exception {
            String result = rewriteTableNames("SELECT * FROM t_order_100", "_100");
            assertThat(result.toLowerCase()).contains("from t_order_100");
            assertThat(result.toLowerCase()).doesNotContain("t_order_100_100");
        }
    }

    @Nested
    @DisplayName("边界场景")
    class EdgeCases {

        @Test
        @DisplayName("空后缀不抛异常，rewriteTableNames 直接拼接（intercept 层短路空后缀）")
        void emptySuffixStillAppends() throws Exception {
            String result = rewriteTableNames("SELECT * FROM t_order", "");
            assertThat(result.toLowerCase()).contains("from t_order");
            assertThat(result.toLowerCase()).doesNotContain("t_order_");
        }

        @Test
        @DisplayName("复杂 SQL 包含子查询——当前仅改写顶层 FROM（已知限制）")
        void subqueryNotRewrittenInCurrentVersion() throws Exception {
            String result = rewriteTableNames(
                    "SELECT * FROM t_order WHERE user_id IN (SELECT id FROM t_user)", "_100");
            // 顶层 t_order 改写
            assertThat(result.toLowerCase()).contains("from t_order_100");
            // 子查询中的 t_user 当前版本不改写（已知限制）
            assertThat(result.toLowerCase()).contains("(select id from t_user)");
        }
    }
}
