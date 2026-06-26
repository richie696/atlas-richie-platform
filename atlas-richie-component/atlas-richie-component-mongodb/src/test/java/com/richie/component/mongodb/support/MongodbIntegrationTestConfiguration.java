package com.richie.component.mongodb.support;

import com.mongodb.event.ServerListener;
import com.mongodb.event.ServerMonitorListener;
import com.richie.component.mongodb.config.MongodbAutoConfiguration;
import com.richie.component.mongodb.listener.DefaultMongoServerListener;
import com.richie.component.mongodb.listener.DefaultMongoServerMonitorListener;
import com.richie.component.tenant.config.TenantAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = TenantAutoConfiguration.class)
@Import(MongodbAutoConfiguration.class)
public class MongodbIntegrationTestConfiguration {

    @Bean
    @ConditionalOnMissingBean(ServerListener.class)
    ServerListener mongoServerListener() {
        return new DefaultMongoServerListener();
    }

    @Bean
    @ConditionalOnMissingBean(ServerMonitorListener.class)
    ServerMonitorListener mongoServerMonitorListener() {
        return new DefaultMongoServerMonitorListener();
    }
}
