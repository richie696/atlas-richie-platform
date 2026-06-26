package com.richie.component.tenant.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TableSuffixHolder — Table 模式表名后缀持有器")
class TableSuffixHolderTest {

    @AfterEach
    void tearDown() {
        TableSuffixHolder.clear();
    }

    @Test
    @DisplayName("初始 get() 返回 null")
    void initialGetReturnsNull() {
        assertThat(TableSuffixHolder.get()).isNull();
    }

    @Test
    @DisplayName("set 后 get 返回对应后缀")
    void setAndGet() {
        TableSuffixHolder.set("_1001");
        assertThat(TableSuffixHolder.get()).isEqualTo("_1001");
    }

    @Test
    @DisplayName("clear 后 get 返回 null")
    void clearRemovesValue() {
        TableSuffixHolder.set("_1001");
        TableSuffixHolder.clear();
        assertThat(TableSuffixHolder.get()).isNull();
    }
}
