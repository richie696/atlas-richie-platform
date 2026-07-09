/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.tenant.strategy;

import com.richie.contract.exception.BusinessException;
import com.richie.component.tenant.exception.TenantErrorCode;

import java.util.regex.Pattern;

/**
 * 统一命名规范校验器。
 *
 * <p>所有多租户相关的 SQL 标识符（schema 名、table 后缀、dataSource key 等）
 * 在被拼接进 SQL 字符串或用于 DataSource 路由前，必须通过本校验器检查。</p>
 *
 * <p><b>白名单规则</b>（兼容所有主流关系型数据库的 SQL 标识符规范）：</p>
 * <ul>
 *   <li>首字符：字母（A-Z / a-z）或下划线（{@code _}） — 数字开头在标准 SQL 中被拒绝</li>
 *   <li>后续字符：字母 / 数字 / 下划线</li>
 *   <li>长度：1-128 字符（SQL 标准上限，MySQL / Oracle / PostgreSQL / SQLServer 全部支持）</li>
 *   <li><b>大小写保留原样</b>：不同数据库的大小写折叠策略不同（PG 默认小写、Oracle 默认大写、
 *       MySQL 8+ 依赖 lower_case_table_names），跨库场景需业务方保证一致</li>
 * </ul>
 *
 * <p>不通过校验的标识符会以 {@link TenantErrorCode#TENANT_INVALID_NAMING} 抛出，
 * 防止 SQL 注入风险、特殊字符导致的 SQL 解析错误、跨库大小写不一致的隐蔽 bug。</p>
 *
 * @author richie696
 * @since 2.0
 */
public final class NamingConventionValidator {

    /**
     * SQL 标识符白名单正则（与 ANSI/ISO SQL:1999 标识符规则一致）。
     */
    public static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /**
     * 标识符最小长度。
     */
    public static final int MIN_LENGTH = 1;

    /**
     * 标识符最大长度（SQL 标准上限）。
     * <ul>
     *   <li>PostgreSQL：63 字符</li>
     *   <li>Oracle：128 字符</li>
     *   <li>MySQL：64 字符</li>
     *   <li>SQL Server：128 字符</li>
     *   <li>本组件取最小公约数 128，确保所有主流关系型数据库兼容</li>
     * </ul>
     */
    public static final int MAX_LENGTH = 128;

    private NamingConventionValidator() {
    }

    /**
     * 校验 SQL 标识符合法性。
     *
     * @param name  待校验的标识符（schema 名、table 后缀、dataSource key 等）
     * @param label 标识符用途标签（用于错误信息，如 {@code "schemaName"} / {@code "tableSuffix"}）
     * @throws BusinessException 当标识符为 null、空、长度越界或字符不在白名单中
     */
    public static void validate(String name, String label) {
        if (name == null) {
            throw new BusinessException(
                TenantErrorCode.TENANT_INVALID_NAMING.name(),
                label + " must not be null");
        }
        if (name.length() < MIN_LENGTH) {
            throw new BusinessException(
                TenantErrorCode.TENANT_INVALID_NAMING.name(),
                label + " must not be empty");
        }
        if (name.length() > MAX_LENGTH) {
            throw new BusinessException(
                TenantErrorCode.TENANT_INVALID_NAMING.name(),
                label + " exceeds max length " + MAX_LENGTH + ": actual=" + name.length());
        }
        if (!IDENTIFIER_PATTERN.matcher(name).matches()) {
            throw new BusinessException(
                TenantErrorCode.TENANT_INVALID_NAMING.name(),
                label + " validation failed: '" + name + "'. Must match ^[A-Za-z_][A-Za-z0-9_]*$ "
                    + "(letters/digits/underscore, must start with letter or underscore)");
        }
    }
}