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
package com.richie.component.desensitize.core.service;

import com.richie.component.desensitize.core.DesensitizeTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultObjectMaskingServiceTest {

    @Test
    void delegatesToSafeLogSerializer() {
        var properties = DesensitizeTestSupport.defaultProperties();
        var maskingService = DesensitizeTestSupport.maskingService(properties);
        ObjectMaskingService service = DesensitizeTestSupport.defaultObjectMaskingService(maskingService, properties);

        String json = service.toSafeJson(Map.of("phone", "13812348000"));
        assertThat(json).contains("138****8000");
        assertThat(service.toSafeString(Map.of("phone", "13812348000"))).contains("138****8000");
    }
}
