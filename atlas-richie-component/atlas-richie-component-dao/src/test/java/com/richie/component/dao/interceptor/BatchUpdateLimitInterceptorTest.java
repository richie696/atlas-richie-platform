package com.richie.component.dao.interceptor;

import com.richie.component.dao.config.DaoProperties;
import com.richie.contract.exception.BusinessException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchUpdateLimitInterceptorTest {

    @Test
    void beforeUpdate_rejectsCollectionExceedingLimit() {
        DaoProperties properties = new DaoProperties();
        properties.setBatchUpdateLimit(2);
        BatchUpdateLimitInterceptor interceptor = new BatchUpdateLimitInterceptor(properties);
        MappedStatement ms = mappedUpdateStatement();

        assertThatThrownBy(() -> interceptor.beforeUpdate(null, ms, List.of("a", "b", "c")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("批量更新操作被拒绝");
    }

    @Test
    void beforeUpdate_allowsCollectionWithinLimit() {
        DaoProperties properties = new DaoProperties();
        properties.setBatchUpdateLimit(5);
        BatchUpdateLimitInterceptor interceptor = new BatchUpdateLimitInterceptor(properties);
        MappedStatement ms = mappedUpdateStatement();

        assertThatCode(() -> interceptor.beforeUpdate(null, ms, List.of("a", "b")))
                .doesNotThrowAnyException();
    }

    @Test
    void beforeUpdate_treatsSingleEntityMapAsOneRecord() {
        DaoProperties properties = new DaoProperties();
        properties.setBatchUpdateLimit(1);
        BatchUpdateLimitInterceptor interceptor = new BatchUpdateLimitInterceptor(properties);
        MappedStatement ms = mappedUpdateStatement();

        assertThatCode(() -> interceptor.beforeUpdate(null, ms, Map.of("param1", new Object())))
                .doesNotThrowAnyException();
    }

    @Test
    void beforeUpdate_ignoresNonUpdateStatements() {
        DaoProperties properties = new DaoProperties();
        properties.setBatchUpdateLimit(1);
        BatchUpdateLimitInterceptor interceptor = new BatchUpdateLimitInterceptor(properties);
        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getSqlCommandType()).thenReturn(SqlCommandType.SELECT);

        assertThatCode(() -> interceptor.beforeUpdate(null, ms, List.of("a", "b", "c")))
                .doesNotThrowAnyException();
    }

    private static MappedStatement mappedUpdateStatement() {
        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getSqlCommandType()).thenReturn(SqlCommandType.UPDATE);
        when(ms.getId()).thenReturn("com.example.UserMapper.updateBatch");
        return ms;
    }
}
