/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
