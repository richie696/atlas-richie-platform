/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.testkit;

import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StaticSecretBootstrapProviderFactoryTest {

    @Test
    void deeplyCopiesMutableSecretsAtInputAndAtEveryLoadBoundary() {
        byte[] sourceBytes = {1, 2};
        char[] sourceChars = {'a', 'b'};
        StaticSecretBootstrapProviderFactory factory = new StaticSecretBootstrapProviderFactory(
                "test",
                Map.of("bytes", sourceBytes, "nested", List.of(Map.of("chars", sourceChars))));
        sourceBytes[0] = 9;
        sourceChars[0] = 'z';
        var client = factory.create(
                new BootstrapSecretProperties(),
                new SecretBootstrapContext(new MockEnvironment(), getClass().getClassLoader()));
        SecretBootstrapRequest request = new SecretBootstrapRequest(
                "app", "test", List.of("common"), List.of());

        Map<String, Object> first = client.load(request).values();
        ((byte[]) first.get("bytes"))[0] = 8;
        @SuppressWarnings("unchecked")
        char[] firstChars = (char[]) ((Map<String, Object>) ((List<?>) first.get("nested")).getFirst())
                .get("chars");
        firstChars[0] = 'y';
        Map<String, Object> second = client.load(request).values();
        @SuppressWarnings("unchecked")
        char[] secondChars = (char[]) ((Map<String, Object>) ((List<?>) second.get("nested")).getFirst())
                .get("chars");

        assertThat((byte[]) second.get("bytes")).containsExactly(1, 2);
        assertThat(secondChars).containsExactly('a', 'b');
    }
}
