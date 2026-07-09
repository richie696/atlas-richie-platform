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
package com.richie.component.nats.connection;

import com.richie.component.nats.config.NatsProperties;
import com.richie.component.nats.enums.AuthType;
import com.richie.component.nats.exception.NatsConnectionException;
import io.nats.client.Options;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NatsAuthConfigurator} 单元测试
 */
class NatsAuthConfiguratorTest {

    private NatsAuthConfigurator configurator;

    @BeforeEach
    void setUp() {
        configurator = new NatsAuthConfigurator();
    }

    @Test
    void configure_withNoneAuth_shouldNotModifyBuilder() {
        Options.Builder builder = new Options.Builder();
        NatsProperties.Auth auth = new NatsProperties.Auth();
        auth.setType(AuthType.NONE);

        assertThatCode(() -> configurator.configure(builder, auth))
                .doesNotThrowAnyException();
    }

    @Test
    void configure_withNullAuth_shouldNotThrow() {
        Options.Builder builder = new Options.Builder();

        assertThatCode(() -> configurator.configure(builder, null))
                .doesNotThrowAnyException();
    }

    @Test
    void configure_withTokenAuth_shouldSetToken() {
        Options.Builder builder = new Options.Builder();
        NatsProperties.Auth auth = new NatsProperties.Auth();
        auth.setType(AuthType.TOKEN);
        auth.setToken("my-secret-token");

        assertThatCode(() -> configurator.configure(builder, auth))
                .doesNotThrowAnyException();
    }

    @Test
    void configure_withTokenAuth_emptyToken_shouldThrow() {
        Options.Builder builder = new Options.Builder();
        NatsProperties.Auth auth = new NatsProperties.Auth();
        auth.setType(AuthType.TOKEN);
        auth.setToken("");

        assertThatThrownBy(() -> configurator.configure(builder, auth))
                .isInstanceOf(NatsConnectionException.class)
                .hasMessageContaining("token");
    }

    @Test
    void configure_withUserPassAuth_shouldSetUserInfo() {
        Options.Builder builder = new Options.Builder();
        NatsProperties.Auth auth = new NatsProperties.Auth();
        auth.setType(AuthType.USERPASS);
        auth.setUsername("user");
        auth.setPassword("pass");

        assertThatCode(() -> configurator.configure(builder, auth))
                .doesNotThrowAnyException();
    }

    @Test
    void configure_withUserPassAuth_missingPassword_shouldThrow() {
        Options.Builder builder = new Options.Builder();
        NatsProperties.Auth auth = new NatsProperties.Auth();
        auth.setType(AuthType.USERPASS);
        auth.setUsername("user");
        // password is null

        assertThatThrownBy(() -> configurator.configure(builder, auth))
                .isInstanceOf(NatsConnectionException.class)
                .hasMessageContaining("username/password");
    }

    @Test
    void configure_withCredentialsAuth_shouldSetCredentialPath() {
        Options.Builder builder = new Options.Builder();
        NatsProperties.Auth auth = new NatsProperties.Auth();
        auth.setType(AuthType.CREDENTIALS);
        auth.setCredentialsFile("/path/to/creds");

        assertThatCode(() -> configurator.configure(builder, auth))
                .doesNotThrowAnyException();
    }

    @Test
    void configure_withCredentialsAuth_emptyPath_shouldThrow() {
        Options.Builder builder = new Options.Builder();
        NatsProperties.Auth auth = new NatsProperties.Auth();
        auth.setType(AuthType.CREDENTIALS);
        auth.setCredentialsFile("");

        assertThatThrownBy(() -> configurator.configure(builder, auth))
                .isInstanceOf(NatsConnectionException.class)
                .hasMessageContaining("credentials-file");
    }
}
