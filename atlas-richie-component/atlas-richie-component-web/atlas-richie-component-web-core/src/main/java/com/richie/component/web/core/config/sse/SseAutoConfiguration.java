package com.richie.component.web.core.config.sse;

import com.richie.component.web.core.metrics.WebMetrics;
import com.richie.component.web.core.sse.SseManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * SSE 长连接装配（README.md §4.4）。
 * <p>
 * 仅在 Servlet Web 应用下生效（{@link ConditionalOnWebApplication}），{@link SseManager}
 * 需 servlet 容器支持 {@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter}。
 *
 * @author richie696
 * @since 2026-07
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SseManager.class)
public class SseAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public SseManager sseManager(WebMetrics webMetrics) {
        return new SseManager(webMetrics);
    }
}