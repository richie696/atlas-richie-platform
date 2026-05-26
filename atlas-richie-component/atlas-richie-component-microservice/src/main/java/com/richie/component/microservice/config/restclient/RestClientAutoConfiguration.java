package com.richie.component.microservice.config.restclient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * RestClient 自动配置，注册带请求头透传等拦截器的 RestClient Bean。
 *
 * @author richie696
 * @since 2025-10-28
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(RestClient.class)
public class RestClientAutoConfiguration {

    /**
     * 注册 RestClient Bean，并注入所有 ClientHttpRequestInterceptor（如头透传、日志）。
     *
     * @param interceptors 已注册的拦截器列表
     * @return 构建好的 RestClient
     */
    @Bean
    @ConditionalOnMissingBean(RestClient.class)
    public RestClient restClient(List<ClientHttpRequestInterceptor> interceptors) {
        return RestClient.builder()
                .requestInterceptors(list -> list.addAll(interceptors))
                .build();
    }

}
