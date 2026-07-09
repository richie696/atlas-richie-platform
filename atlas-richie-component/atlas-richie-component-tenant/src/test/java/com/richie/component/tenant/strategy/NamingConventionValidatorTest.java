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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NamingConventionValidator — SQL 标识符白名单校验")
class NamingConventionValidatorTest {

    @Nested
    @DisplayName("合法命名（不应抛异常）")
    class ValidNames {

        @Test
        @DisplayName("单字符字母")
        void singleLetter() {
            assertThatCode(() -> NamingConventionValidator.validate("a", "label"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("下划线开头")
        void startsWithUnderscore() {
            assertThatCode(() -> NamingConventionValidator.validate("_user", "label"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("字母+数字+下划线混合")
        void mixedAlphanumeric() {
            assertThatCode(() -> NamingConventionValidator.validate("tenant_1001", "label"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("保留大写")
        void uppercase() {
            assertThatCode(() -> NamingConventionValidator.validate("TENANT_A", "label"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("128 字符边界值")
        void maxLength() {
            String maxName = "a".repeat(NamingConventionValidator.MAX_LENGTH);
            assertThatCode(() -> NamingConventionValidator.validate(maxName, "label"))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("非法命名（应抛 BusinessException + TENANT_INVALID_NAMING）")
    class InvalidNames {

        @Test
        @DisplayName("null")
        void nullValue() {
            assertThatThrownBy(() -> NamingConventionValidator.validate(null, "schemaName"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(TenantErrorCode.TENANT_INVALID_NAMING.name());
        }

        @Test
        @DisplayName("空字符串")
        void emptyString() {
            assertThatThrownBy(() -> NamingConventionValidator.validate("", "tableSuffix"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(TenantErrorCode.TENANT_INVALID_NAMING.name());
        }

        @Test
        @DisplayName("数字开头")
        void startsWithDigit() {
            assertThatThrownBy(() -> NamingConventionValidator.validate("1001_tenant", "dataSourceKey"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(TenantErrorCode.TENANT_INVALID_NAMING.name());
        }

        @Test
        @DisplayName("含连字符")
        void containsHyphen() {
            assertThatThrownBy(() -> NamingConventionValidator.validate("tenant-1001", "tableSuffix"))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("含空格")
        void containsSpace() {
            assertThatThrownBy(() -> NamingConventionValidator.validate("tenant 1001", "schemaName"))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("含分号（SQL 注入）")
        void containsSemicolon() {
            assertThatThrownBy(() -> NamingConventionValidator.validate("tenant;DROP TABLE users", "dataSourceKey"))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("含单引号（SQL 注入）")
        void containsSingleQuote() {
            assertThatThrownBy(() -> NamingConventionValidator.validate("tenant'", "tableSuffix"))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("含中点（schema 注入）")
        void containsDot() {
            assertThatThrownBy(() -> NamingConventionValidator.validate("public.users", "schemaName"))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("含 Unicode")
        void containsUnicode() {
            assertThatThrownBy(() -> NamingConventionValidator.validate("租户", "schemaName"))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("长度超限（129 字符）")
        void exceedsMaxLength() {
            String tooLong = "a".repeat(NamingConventionValidator.MAX_LENGTH + 1);
            assertThatThrownBy(() -> NamingConventionValidator.validate(tooLong, "dataSourceKey"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exceeds max length");
        }
    }
}