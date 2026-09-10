package cn.richie696.component.grpc.discovery;

import io.grpc.NameResolver;
import io.grpc.Status;
import io.grpc.SynchronizationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscoveryClientNameResolverProviderTest {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void resolvesGrpcPortMetadataBeforeServicePort() {
        DiscoveryClient client = mock(DiscoveryClient.class);
        DefaultServiceInstance instance = new DefaultServiceInstance(
                "instance-1", "tool-service", "127.0.0.1", 8080, false, Map.of("grpc-port", "9552"));
        when(client.getInstances("tool-service")).thenReturn(List.of(instance));

        NameResolver resolver = provider(client).newNameResolver(URI.create("discovery:///tool-service"), arguments());
        CapturingListener listener = new CapturingListener();
        resolver.start(listener);

        InetSocketAddress address = (InetSocketAddress) listener.result.getAddresses().getFirst().getAddresses().getFirst();
        assertThat(address.getHostString()).isEqualTo("127.0.0.1");
        assertThat(address.getPort()).isEqualTo(9552);
        resolver.shutdown();
    }

    @Test
    void reportsUnavailableWhenDiscoveryHasNoInstances() {
        DiscoveryClient client = mock(DiscoveryClient.class);
        when(client.getInstances("missing-service")).thenReturn(List.of());

        NameResolver resolver = provider(client).newNameResolver(URI.create("discovery:///missing-service"), arguments());
        CapturingListener listener = new CapturingListener();
        resolver.start(listener);

        assertThat(listener.error.getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(listener.error.getDescription()).contains("missing-service");
        resolver.shutdown();
    }

    @Test
    void declinesBlankDiscoveryTarget() {
        DiscoveryClientNameResolverProvider provider = provider(mock(DiscoveryClient.class));

        assertThat(provider.newNameResolver(URI.create("discovery:///"), arguments())).isNull();
    }

    private NameResolver.Args arguments() {
        return NameResolver.Args.newBuilder()
                .setDefaultPort(443)
                .setProxyDetector(target -> null)
                .setSynchronizationContext(new SynchronizationContext((thread, error) -> { }))
                .setServiceConfigParser(new NameResolver.ServiceConfigParser() {
                    @Override
                    public NameResolver.ConfigOrError parseServiceConfig(Map<String, ?> serviceConfig) {
                        return NameResolver.ConfigOrError.fromConfig(serviceConfig);
                    }
                })
                .setScheduledExecutorService(executor)
                .build();
    }

    private DiscoveryClientNameResolverProvider provider(DiscoveryClient client) {
        @SuppressWarnings("unchecked")
        ObjectProvider<DiscoveryClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        return new DiscoveryClientNameResolverProvider(provider);
    }

    private static final class CapturingListener extends NameResolver.Listener2 {
        private NameResolver.ResolutionResult result;
        private Status error;

        @Override
        public void onResult(NameResolver.ResolutionResult resolutionResult) {
            this.result = resolutionResult;
        }

        @Override
        public void onError(Status status) {
            this.error = status;
        }
    }
}
