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
package cn.richie696.component.mfa.core.support;

import cn.richie696.component.tenant.config.MultiTenancyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class MfaTenantSupportTest {

    @Test
    void isTenantEnabled_readsUnifiedTenantProperties() {
        MfaTenantSupport support = new MfaTenantSupport();
        MultiTenancyProperties properties = new MultiTenancyProperties();
        properties.setEnable(true);
        ReflectionTestUtils.setField(support, "properties", properties);

        assertThat(support.isTenantEnabled()).isTrue();
    }

    @Test
    void isTenantEnabled_defaultsToFalseWhenNoConfig() {
        assertThat(new MfaTenantSupport().isTenantEnabled()).isFalse();
    }
}
