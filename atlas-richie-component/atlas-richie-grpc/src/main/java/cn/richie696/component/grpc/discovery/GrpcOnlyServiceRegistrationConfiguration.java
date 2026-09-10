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

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Registers gRPC-only applications through the application's selected Spring Cloud registry.
 *
 * <p>Spring Cloud's default auto-registration waits for a {@code WebServerInitializedEvent}.
 * A pure gRPC process has no WebServer, so it registers after the application is ready and
 * deregisters during shutdown. HTTP applications keep their existing auto-registration path.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.main.web-application-type", havingValue = "none")
@ConditionalOnBean({ServiceRegistry.class, Registration.class})
public class GrpcOnlyServiceRegistrationConfiguration {

    private final ObjectProvider<ServiceRegistry> registryProvider;
    private final ObjectProvider<Registration> registrationProvider;
    private Registration registered;

    public GrpcOnlyServiceRegistrationConfiguration(ObjectProvider<ServiceRegistry> registryProvider,
                                                     ObjectProvider<Registration> registrationProvider) {
        this.registryProvider = registryProvider;
        this.registrationProvider = registrationProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void registerGrpcService() {
        if (registered != null) {
            return;
        }
        ServiceRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }
        Registration registration = registrationProvider.getIfAvailable();
        if (registration == null) {
            return;
        }
        registry.register(registration);
        registered = registration;
    }

    @PreDestroy
    public synchronized void deregisterGrpcService() {
        if (registered == null) {
            return;
        }
        ServiceRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            registry.deregister(registered);
        }
        registered = null;
    }
}
