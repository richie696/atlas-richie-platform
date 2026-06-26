package com.richie.component.tenant.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataSourceContextHolder — Database 模式数据源 key 持有器")
class DataSourceContextHolderTest {

    @AfterEach
    void tearDown() {
        DataSourceContextHolder.clear();
    }

    @Test
    @DisplayName("初始 get() 返回 null")
    void initialGetReturnsNull() {
        assertThat(DataSourceContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("set 后 get 返回对应 key")
    void setAndGet() {
        DataSourceContextHolder.set("shared");
        assertThat(DataSourceContextHolder.get()).isEqualTo("shared");
    }

    @Test
    @DisplayName("多次 set 覆盖前值")
    void multipleSetOverrides() {
        DataSourceContextHolder.set("shared");
        DataSourceContextHolder.set("1001");
        assertThat(DataSourceContextHolder.get()).isEqualTo("1001");
    }

    @Test
    @DisplayName("clear 后 get 返回 null")
    void clearRemovesValue() {
        DataSourceContextHolder.set("1001");
        DataSourceContextHolder.clear();
        assertThat(DataSourceContextHolder.get()).isNull();
    }
}
