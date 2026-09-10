/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.grpc.discovery;

import io.grpc.NameResolverProvider;
import io.grpc.NameResolverRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Bridges Spring Cloud service discovery to gRPC {@code discovery:///} targets.
 *
 * <p>The resolver depends only on {@link DiscoveryClient}; applications remain free to
 * select Nacos, Consul, or another Spring Cloud registry implementation.</p>
 */
@AutoConfiguration
@ConditionalOnClass({NameResolverProvider.class, DiscoveryClient.class})
@Import(GrpcOnlyServiceRegistrationConfiguration.class)
public class GrpcDiscoveryAutoConfiguration {

    /**
     * Registers before gRPC client stubs can be created. Spring gRPC may create a blocking
     * stub from {@code @ImportGrpcClients} before ordinary {@code @Bean} initialization.
     */
    @Bean
    static BeanFactoryPostProcessor grpcDiscoveryNameResolverRegistration() {
        return beanFactory -> {
            ObjectProvider<DiscoveryClient> discoveryClientProvider =
                    beanFactory.getBeanProvider(DiscoveryClient.class);
            NameResolverRegistry.getDefaultRegistry().register(
                    new DiscoveryClientNameResolverProvider(discoveryClientProvider));
        };
    }
}
