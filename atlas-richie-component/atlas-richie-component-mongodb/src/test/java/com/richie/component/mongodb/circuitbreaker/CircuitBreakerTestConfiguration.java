package com.richie.component.mongodb.circuitbreaker;

import com.richie.component.mongodb.config.MongodbAutoConfiguration;
import com.richie.component.mongodb.circuitbreaker.MongodbCircuitBreakerProperties;
import com.richie.component.mongodb.circuitbreaker.MongodbSentinelAutoConfiguration;
import com.richie.component.mongodb.circuitbreaker.MongodbSentinelAspect;
import com.richie.component.mongodb.core.AuditFieldHandler;
import com.richie.component.mongodb.core.EntityIntrospector;
import com.richie.component.mongodb.core.TenantHandler;
import com.richie.component.mongodb.observability.MongodbMetricsRecorder;
import com.richie.component.mongodb.observability.MongodbSlowQueryLogger;
import com.richie.component.mongodb.observability.MongodbTracing;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({MongodbAutoConfiguration.class, MongodbSentinelAutoConfiguration.class})
@EnableConfigurationProperties(MongodbCircuitBreakerProperties.class)
public class CircuitBreakerTestConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MongodbTracing mongodbTracing() {
        return null;
    }

    @Bean
    @ConditionalOnMissingBean
    public MongodbMetricsRecorder mongodbMetricsRecorder() {
        return null;
    }

    @Bean
    @ConditionalOnMissingBean
    public MongodbSlowQueryLogger mongodbSlowQueryLogger() {
        return null;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditFieldHandler auditFieldHandler() {
        return new AuditFieldHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantHandler tenantHandler() {
        return new TenantHandler();
    }
}
