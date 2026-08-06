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
package cn.richie696.component.web.jetty.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JettyMetricsBeanTest {

    @Nested
    @DisplayName("Constructor & getter")
    class ConstructorAndGetter {

        @Test
        void shouldWrapProvidedMetricsInstance() {
            JettyProperties.Metrics metrics = new JettyProperties.Metrics();
            metrics.setPrefix("custom_jetty");
            metrics.setEnabled(false);

            JettyMetricsBean bean = new JettyMetricsBean(metrics);

            assertThat(bean.getMetrics()).isSameAs(metrics);
        }

        @Test
        void shouldPreserveDefaultMetricsConfiguration() {
            JettyMetricsBean bean = new JettyMetricsBean(new JettyProperties.Metrics());

            assertThat(bean.getMetrics().getPrefix()).isEqualTo("atlas_jetty");
            assertThat(bean.getMetrics().isEnabled()).isTrue();
        }

        @Test
        void shouldReturnCustomizedMetrics() {
            JettyProperties.Metrics metrics = new JettyProperties.Metrics();
            metrics.setPrefix("tenant_jetty_");
            metrics.setEnabled(false);

            JettyMetricsBean bean = new JettyMetricsBean(metrics);

            assertThat(bean.getMetrics().getPrefix()).isEqualTo("tenant_jetty_");
            assertThat(bean.getMetrics().isEnabled()).isFalse();
        }

        @Test
        void shouldExposeUpdatedMetricsAfterPropertyChange() {
            JettyProperties.Metrics metrics = new JettyProperties.Metrics();
            JettyMetricsBean bean = new JettyMetricsBean(metrics);

            metrics.setPrefix("mutated_prefix");
            metrics.setEnabled(false);

            assertThat(bean.getMetrics().getPrefix()).isEqualTo("mutated_prefix");
            assertThat(bean.getMetrics().isEnabled()).isFalse();
        }
    }
}
