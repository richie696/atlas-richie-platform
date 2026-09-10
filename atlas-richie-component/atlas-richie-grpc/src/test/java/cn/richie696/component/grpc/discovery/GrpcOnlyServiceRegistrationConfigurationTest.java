package cn.richie696.component.grpc.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class GrpcOnlyServiceRegistrationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GrpcOnlyServiceRegistrationConfiguration.class))
            .withBean(ServiceRegistry.class, () -> mock(ServiceRegistry.class))
            .withBean(Registration.class, () -> mock(Registration.class));

    @Test
    void createsTheRegistrationBridgeOnlyForGrpcOnlyApplications() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(GrpcOnlyServiceRegistrationConfiguration.class));

        contextRunner.withPropertyValues("spring.main.web-application-type=none").run(context ->
                assertThat(context).hasSingleBean(GrpcOnlyServiceRegistrationConfiguration.class));
    }

    @Test
    void registersOnceAndDeregistersTheSameGrpcRegistration() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ServiceRegistry> registryProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Registration> registrationProvider = mock(ObjectProvider.class);
        ServiceRegistry registry = mock(ServiceRegistry.class);
        Registration registration = mock(Registration.class);
        when(registryProvider.getIfAvailable()).thenReturn(registry);
        when(registrationProvider.getIfAvailable()).thenReturn(registration);
        GrpcOnlyServiceRegistrationConfiguration configuration =
                new GrpcOnlyServiceRegistrationConfiguration(registryProvider, registrationProvider);

        configuration.registerGrpcService();
        configuration.registerGrpcService();
        configuration.deregisterGrpcService();

        verify(registry).register(registration);
        verify(registry).deregister(registration);
    }

    @Test
    void doesNothingWhenTheApplicationDoesNotProvideARegistry() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ServiceRegistry> registryProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Registration> registrationProvider = mock(ObjectProvider.class);
        when(registryProvider.getIfAvailable()).thenReturn(null);
        GrpcOnlyServiceRegistrationConfiguration configuration =
                new GrpcOnlyServiceRegistrationConfiguration(registryProvider, registrationProvider);

        configuration.registerGrpcService();
        configuration.deregisterGrpcService();

        verify(registrationProvider, never()).getIfAvailable();
    }
}
