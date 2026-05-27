package com.richie.component.http.restclient.config;

import com.richie.component.http.restclient.RestClientAdapter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * RestClient Provider 自动配置。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@ConditionalOnProperty(prefix = "platform.component.http", name = "provider", havingValue = "rest_client")
public class HttpAutoConfiguration {

    @Bean
    RestClientAdapter httpClient(ObjectProvider<RestClient.Builder> builderProvider) {
        // 支持外部注入自定义 Builder；未提供时使用默认构造。
        RestClient.Builder builder = builderProvider.getIfAvailable(RestClient::builder);
        return new RestClientAdapter(builder.build());
    }

}
