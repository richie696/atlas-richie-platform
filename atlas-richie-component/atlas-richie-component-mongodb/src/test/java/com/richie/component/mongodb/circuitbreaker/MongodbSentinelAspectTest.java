package com.richie.component.mongodb.circuitbreaker;

import com.richie.component.mongodb.Mongodb;
import com.richie.component.mongodb.builder.DeleteBuilder;
import com.richie.component.mongodb.builder.QueryBuilder;
import com.richie.component.mongodb.builder.UpdateBuilder;
import com.richie.component.mongodb.core.AuditFieldHandler;
import com.richie.component.mongodb.core.EntityIntrospector;
import com.richie.component.mongodb.core.TenantHandler;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MongodbSentinelAspectTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private EntityIntrospector entityIntrospector;

    @Mock
    private ProceedingJoinPoint pjp;

    private Mongodb mongodb;

    @BeforeEach
    void setUp() {
        AuditFieldHandler auditFieldHandler = new AuditFieldHandler();
        TenantHandler tenantHandler = new TenantHandler();
        mongodb = new Mongodb(mongoTemplate, entityIntrospector, auditFieldHandler, tenantHandler, null, null, null);
    }

    @Test
    void query_returnsQueryBuilder() {
        QueryBuilder<?> result = mongodb.query(String.class);
        assertThat(result).isNotNull();
    }

    @Test
    void update_returnsUpdateBuilder() {
        UpdateBuilder<?> result = mongodb.update(String.class);
        assertThat(result).isNotNull();
    }

    @Test
    void delete_returnsDeleteBuilder() {
        DeleteBuilder<?> result = mongodb.delete(String.class);
        assertThat(result).isNotNull();
    }

    @Test
    void findById_returnsOptional() {
        when(mongoTemplate.findById(any(), any())).thenReturn("test");
        Optional<?> result = mongodb.findById(String.class, "123");
        assertThat(result).isPresent();
    }

    @Test
    void findById_whenNotFound_returnsEmptyOptional() {
        when(mongoTemplate.findById(any(), any())).thenReturn(null);
        Optional<?> result = mongodb.findById(String.class, "123");
        assertThat(result).isEmpty();
    }

    @Test
    void existsById_whenExists_returnsTrue() {
        when(mongoTemplate.exists(any(), eq(String.class))).thenReturn(true);
        boolean result = mongodb.existsById(String.class, "123");
        assertThat(result).isTrue();
    }

    @Test
    void deleteById_callsMongoTemplate() {
        mongodb.deleteById(String.class, "123");
        verify(mongoTemplate).remove(any(), eq(String.class));
    }

    @Test
    void dropCollection_callsMongoTemplate() {
        mongodb.dropCollection(String.class);
        verify(mongoTemplate).dropCollection(String.class);
    }

    @Test
    void insertAll_returnsList() {
        when(mongoTemplate.insert(anyList(), eq(String.class))).thenReturn(java.util.List.of("a", "b"));
        var result = mongodb.insertAll(java.util.List.of("a", "b"));
        assertThat(result).hasSize(2);
    }
}
