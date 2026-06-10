package com.richie.component.tenant.config;

import com.richie.component.tenant.web.TenantContextInitializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 租户组件自动配置
 *
 * <p>在 Servlet Web 环境下注册 {@link TenantContextInitializer} 拦截器，
 * 自动从请求头解析租户 ID 并设置到 {@link com.richie.component.tenant.context.TenantContextHolder}。</p>
 *
 * @author richie696
 * @since 1.0
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TenantAutoConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantContextInitializer())
                .addPathPatterns("/**");
    }

    @Bean
    public TenantContextInitializer tenantContextInitializer() {
        return new TenantContextInitializer();
    }
}
