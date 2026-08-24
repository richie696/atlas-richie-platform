/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.aws;

import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.KmsClientBuilder;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;

import java.time.Duration;

final class AwsClientFactory {

    AwsSecretClient create(
            AwsSecretConfigurationResolver.ResolvedAwsConfiguration resolved,
            BootstrapSecretProperties bootstrapProperties) {
        AwsSecretProperties properties = resolved.properties();
        AwsCredentialsProvider credentials = credentials(properties.getAuthentication());
        Region region = Region.of(properties.getRegion());
        Duration attemptTimeout = bootstrapProperties.getResilience().getReadTimeout();
        int maxAttempts = Math.max(1, bootstrapProperties.getResilience().getMaxAttempts());
        ClientOverrideConfiguration override = ClientOverrideConfiguration.builder()
                .apiCallAttemptTimeout(attemptTimeout)
                .apiCallTimeout(attemptTimeout.multipliedBy(maxAttempts))
                .retryStrategy(builder -> builder.maxAttempts(maxAttempts))
                .build();
        UrlConnectionHttpClient.Builder http = UrlConnectionHttpClient.builder()
                .connectionTimeout(bootstrapProperties.getResilience().getConnectTimeout())
                .socketTimeout(attemptTimeout);

        SecretsManagerClientBuilder secretsBuilder = SecretsManagerClient.builder()
                .region(region)
                .credentialsProvider(credentials)
                .overrideConfiguration(override)
                .httpClientBuilder(http);
        if (properties.getEndpoints().getSecretsManager() != null) {
            secretsBuilder.endpointOverride(properties.getEndpoints().getSecretsManager());
        }
        KmsClientBuilder kmsBuilder = KmsClient.builder()
                .region(region)
                .credentialsProvider(credentials)
                .overrideConfiguration(override)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(bootstrapProperties.getResilience().getConnectTimeout())
                        .socketTimeout(attemptTimeout));
        if (properties.getEndpoints().getKms() != null) {
            kmsBuilder.endpointOverride(properties.getEndpoints().getKms());
        }
        SecretsManagerClient secrets = secretsBuilder.build();
        KmsClient kms = kmsBuilder.build();
        return new AwsSecretClient(
                resolved,
                bootstrapProperties,
                secrets,
                kms,
                new ObjectMapper(),
                () -> close(secrets, kms, credentials));
    }

    private AwsCredentialsProvider credentials(AwsSecretProperties.Authentication properties) {
        return switch (properties.getType()) {
            case DEFAULT_CHAIN -> DefaultCredentialsProvider.builder()
                    .asyncCredentialUpdateEnabled(true)
                    .build();
            case PROFILE -> ProfileCredentialsProvider.builder()
                    .profileName(properties.getProfileName())
                    .build();
        };
    }

    private void close(
            SecretsManagerClient secrets,
            KmsClient kms,
            AwsCredentialsProvider credentials) {
        try {
            secrets.close();
        } finally {
            try {
                kms.close();
            } finally {
                if (credentials instanceof AutoCloseable closeable) {
                    try {
                        closeable.close();
                    } catch (Exception ignored) {
                        // Context shutdown continues without logging credentials.
                    }
                }
            }
        }
    }
}
