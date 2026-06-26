package com.richie.component.mongodb.support;

import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.context.ThreadLocalHolder;
import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class MongodbIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        // TenantAutoConfiguration 被排除，需手动初始化 TenantContext
        TenantContext.init(new ThreadLocalHolder());

        SpringPropertyInitializer.applyIfAvailable(
                MongodbIntegrationTestSupport::isEnabled,
                pairs -> MongodbIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }
}
