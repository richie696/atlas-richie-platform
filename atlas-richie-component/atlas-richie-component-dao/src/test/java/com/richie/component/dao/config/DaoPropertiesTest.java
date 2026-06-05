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
}
