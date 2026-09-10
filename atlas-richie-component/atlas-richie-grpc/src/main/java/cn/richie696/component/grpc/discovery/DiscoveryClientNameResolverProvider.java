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

import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import io.grpc.Status;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Resolves {@code discovery:///service-name} through Spring Cloud discovery. */
public final class DiscoveryClientNameResolverProvider extends NameResolverProvider {

    private final ObjectProvider<DiscoveryClient> discoveryClientProvider;

    public DiscoveryClientNameResolverProvider(ObjectProvider<DiscoveryClient> discoveryClientProvider) {
        this.discoveryClientProvider = discoveryClientProvider;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public int priority() {
        return 6;
    }

    @Override
    public String getDefaultScheme() {
        return "discovery";
    }

    @Override
    public String getScheme() {
        return "discovery";
    }

    @Override
    public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
        String serviceName = targetUri.getPath();
        if (serviceName == null) {
            return null;
        }
        serviceName = serviceName.startsWith("/") ? serviceName.substring(1) : serviceName;
        if (serviceName.isBlank()) {
            return null;
        }
        DiscoveryClient discoveryClient = discoveryClientProvider.getIfAvailable();
        if (discoveryClient == null) {
            return null;
        }
        return new DiscoveryClientNameResolver(serviceName, args, discoveryClient);
    }

    private static final class DiscoveryClientNameResolver extends NameResolver {

        private final String serviceName;
        private final Args args;
        private final DiscoveryClient discoveryClient;
        private volatile Listener2 listener;
        private volatile ScheduledFuture<?> refreshTask;

        private DiscoveryClientNameResolver(String serviceName, Args args, DiscoveryClient discoveryClient) {
            this.serviceName = serviceName;
            this.args = args;
            this.discoveryClient = discoveryClient;
        }

        @Override
        public String getServiceAuthority() {
            return serviceName;
        }

        @Override
        public void start(Listener2 listener) {
            this.listener = listener;
            resolve();
            refreshTask = args.getScheduledExecutorService()
                    .scheduleAtFixedRate(this::resolve, 5, 5, TimeUnit.SECONDS);
        }

        @Override
        public void refresh() {
            resolve();
        }

        private void resolve() {
            try {
                List<EquivalentAddressGroup> groups = discoveryClient.getInstances(serviceName).stream()
                        .map(DiscoveryClientNameResolver::toAddressGroup)
                        .toList();
                Listener2 currentListener = listener;
                if (currentListener == null) {
                    return;
                }
                if (groups.isEmpty()) {
                    currentListener.onError(Status.UNAVAILABLE.withDescription(
                            "No instances for " + serviceName));
                    return;
                }
                currentListener.onResult(NameResolver.ResolutionResult.newBuilder()
                        .setAddresses(groups)
                        .setAttributes(Attributes.EMPTY)
                        .build());
            } catch (Exception exception) {
                Listener2 currentListener = listener;
                if (currentListener != null) {
                    currentListener.onError(Status.UNAVAILABLE
                            .withDescription("Discovery failed for " + serviceName)
                            .withCause(exception));
                }
            }
        }

        private static EquivalentAddressGroup toAddressGroup(ServiceInstance instance) {
            Map<String, String> metadata = instance.getMetadata();
            String metadataPort = metadata.get("grpc-port");
            if (metadataPort == null) {
                metadataPort = metadata.get("gRPC_port");
            }
            if (metadataPort == null) {
                metadataPort = metadata.get("GRPC_port");
            }
            int port = metadataPort == null ? instance.getPort() : parsePort(metadataPort, instance.getPort());
            return new EquivalentAddressGroup(new InetSocketAddress(instance.getHost(), port));
        }

        private static int parsePort(String candidate, int fallback) {
            try {
                return Integer.parseInt(candidate);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        @Override
        public void shutdown() {
            listener = null;
            ScheduledFuture<?> task = refreshTask;
            if (task != null) {
                task.cancel(false);
            }
        }
    }
}
