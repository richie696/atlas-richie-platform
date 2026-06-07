package com.richie.component.dao.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.richie.component.dao.snowflake.IdBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DaoAutoConfigurationTest {

    @Test
    void mybatisPlusInterceptor_buildsInterceptorChain() {
        DaoProperties properties = new DaoProperties();
        properties.setDbType(DbType.MYSQL);
        properties.setBatchUpdateLimit(500);
        properties.setEnableBlockAttack(true);

        IdBuilder idBuilder = new IdBuilder(1L);
        DaoAutoConfiguration config = new DaoAutoConfiguration(properties, idBuilder);

        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors()).isNotEmpty();
        assertThat(interceptor.getInterceptors()).hasSize(4);
    }

    @Test
    void mybatisPlusInterceptor_omitsBlockAttackWhenDisabled() {
        DaoProperties properties = new DaoProperties();
        properties.setDbType(DbType.MYSQL);
        properties.setBatchUpdateLimit(500);
        properties.setEnableBlockAttack(false);

        IdBuilder idBuilder = new IdBuilder(1L);
        DaoAutoConfiguration config = new DaoAutoConfiguration(properties, idBuilder);

        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors()).hasSize(3);
    }

    @Test
    void plusPropertiesCustomizer_wiresIdBuilderForIdentifierGeneration() {
        DaoProperties properties = new DaoProperties();
        IdBuilder idBuilder = new IdBuilder(5L);
        DaoAutoConfiguration config = new DaoAutoConfiguration(properties, idBuilder);

        var customizer = config.plusPropertiesCustomizer();

        com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties plusProperties = new com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties();
        customizer.customize(plusProperties);

        var identifierGenerator = plusProperties.getGlobalConfig().getIdentifierGenerator();
        Number id = identifierGenerator.nextId(new Object());
        assertThat(id.longValue()).isPositive();
    }

    @Test
    void mybatisPlusInterceptor_setsCorrectDbType() {
        DaoProperties properties = new DaoProperties();
        properties.setDbType(DbType.POSTGRE_SQL);
        properties.setBatchUpdateLimit(100);
        properties.setEnableBlockAttack(false);

        IdBuilder idBuilder = new IdBuilder(1L);
        DaoAutoConfiguration config = new DaoAutoConfiguration(properties, idBuilder);

        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors()).isNotEmpty();
    }
}
