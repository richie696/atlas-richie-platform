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
package com.richie.component.desensitize.jackson.config;

import com.richie.context.utils.data.JsonUtils;
import com.richie.context.utils.data.config.JsonUtilsModuleAutoConfiguration;
import com.richie.component.desensitize.core.annotation.Sensitive;
import com.richie.component.desensitize.core.config.DesensitizeAutoConfiguration;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
/**
 * JacksonDesensitizeAutoConfigurationTest 测试类。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
class JacksonDesensitizeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JsonUtilsModuleAutoConfiguration.class,
                    DesensitizeAutoConfiguration.class,
                    JacksonDesensitizeAutoConfiguration.class));

    @Test
    void shouldRegisterDesensitizeJacksonModule() {
        contextRunner
                .withPropertyValues("platform.component.desensitize.sensitive-keys.phone=PHONE")
                .run(context -> assertThat(context).hasSingleBean(JacksonModule.class));
    }

    @Test
    void objectMapperWithModuleMasksApiVo() throws Exception {
        contextRunner
                .withPropertyValues("platform.component.desensitize.sensitive-keys.phone=PHONE")
                .run(context -> {
                    JacksonModule module = context.getBean(JacksonModule.class);
                    ObjectMapper mapper = JsonMapper.builder().addModule(module).build();
                    ApiUserVo vo = new ApiUserVo();
                    vo.phone = "13812348000";
                    try {
                        String json = mapper.writeValueAsString(vo);
                        assertThat(json).contains("138****8000");
                        assertThat(json).doesNotContain("13812348000");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Test
    void jsonUtilsShouldAutoRegisterDesensitizeModule() {
        contextRunner
                .withPropertyValues("platform.component.desensitize.sensitive-keys.phone=PHONE")
                .run(context -> {
                    ApiUserVo vo = new ApiUserVo();
                    vo.phone = "13812348000";
                    String json = JsonUtils.getInstance().serialize(vo);
                    assertThat(json).contains("138****8000");
                    assertThat(json).doesNotContain("13812348000");
                });
    }

    static class ApiUserVo {
        @Sensitive(type = MaskType.PHONE, scenes = MaskScene.API_RESPONSE)
        public String phone;
    }
}
