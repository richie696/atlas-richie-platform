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
