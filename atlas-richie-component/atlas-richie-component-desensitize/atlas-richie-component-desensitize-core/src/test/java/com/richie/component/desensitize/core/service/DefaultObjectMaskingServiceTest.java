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
