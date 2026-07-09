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
package com.richie.component.dao.config;

import com.baomidou.mybatisplus.annotation.DbType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DaoPropertiesTest {

    @Test
    void defaults_matchComponentContract() {
        DaoProperties properties = new DaoProperties();

        assertThat(properties.getDbType()).isEqualTo(DbType.MYSQL);
        assertThat(properties.getBatchUpdateLimit()).isEqualTo(1000);
        assertThat(properties.isEnableDefaultFieldHandler()).isTrue();
        assertThat(properties.isEnableBlockAttack()).isTrue();
    }

    @Test
    void setDbType_shouldUpdateValue() {
        DaoProperties properties = new DaoProperties();
        properties.setDbType(DbType.POSTGRE_SQL);
        assertThat(properties.getDbType()).isEqualTo(DbType.POSTGRE_SQL);
    }

    @Test
    void setBatchUpdateLimit_shouldUpdateValue() {
        DaoProperties properties = new DaoProperties();
        properties.setBatchUpdateLimit(500);
        assertThat(properties.getBatchUpdateLimit()).isEqualTo(500);
    }

    @Test
    void setEnableDefaultFieldHandler_shouldUpdateValue() {
        DaoProperties properties = new DaoProperties();
        properties.setEnableDefaultFieldHandler(false);
        assertThat(properties.isEnableDefaultFieldHandler()).isFalse();
    }

    @Test
    void setEnableBlockAttack_shouldUpdateValue() {
        DaoProperties properties = new DaoProperties();
        properties.setEnableBlockAttack(false);
        assertThat(properties.isEnableBlockAttack()).isFalse();
    }

    @Test
    void allSetters_shouldWorkCorrectly() {
        DaoProperties properties = new DaoProperties();
        properties.setDbType(DbType.ORACLE);
        properties.setBatchUpdateLimit(2000);
        properties.setEnableDefaultFieldHandler(false);
        properties.setEnableBlockAttack(false);

        assertThat(properties.getDbType()).isEqualTo(DbType.ORACLE);
        assertThat(properties.getBatchUpdateLimit()).isEqualTo(2000);
        assertThat(properties.isEnableDefaultFieldHandler()).isFalse();
        assertThat(properties.isEnableBlockAttack()).isFalse();
    }
}
