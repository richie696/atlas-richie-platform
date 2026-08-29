/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.web.jetty;

import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the starter selects embedded Jetty in an actual Servlet application context.
 * This guards against an application accidentally reintroducing Tomcat through a direct
 * {@code spring-boot-starter-web} dependency.
 */
@SpringBootTest(
        classes = JettyServletContainerIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=servlet")
class JettyServletContainerIntegrationTest {

    @Autowired
    private WebServerApplicationContext applicationContext;

    @Autowired
    private Server jettyServer;

    @Test
    void startsEmbeddedJettyAndExposesItsServerBean() {
        assertThat(applicationContext.getWebServer().getClass().getName())
                .isEqualTo("org.springframework.boot.jetty.servlet.JettyServletWebServer");
        assertThat(applicationContext.getWebServer().getPort()).isPositive();
        assertThat(jettyServer.isStarted()).isTrue();
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
