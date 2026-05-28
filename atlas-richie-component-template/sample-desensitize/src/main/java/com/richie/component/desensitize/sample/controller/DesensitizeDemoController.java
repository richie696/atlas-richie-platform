package com.richie.component.desensitize.sample.controller;

import com.richie.component.desensitize.core.model.MaskType;
import com.richie.component.desensitize.core.support.SensitiveLogArg;
import com.richie.component.desensitize.sample.model.UserProfileView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 脱敏功能演示接口。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/desensitize-sample")
public class DesensitizeDemoController {

    /**
     * 演示 API 返回值脱敏（注解字段 + Map 键名）。
     */
    @GetMapping("/api")
    public UserProfileView api() {
        UserProfileView view = new UserProfileView();
        view.setUserId("U1001");
        view.setPhone("13812348000");
        view.setIdCard("110101199001011234");
        view.setExtra(new LinkedHashMap<>(Map.of(
                "phone", "13812348000",
                "orderId", "ORDER-13812348000",
                "idCard", "110101199001011234"
        )));
        return view;
    }

    /**
     * 演示日志脱敏：参数、JSON 文本、MDC。
     */
    @GetMapping("/log")
    public Map<String, Object> logDemo() {
        // 1) 参数脱敏（TurboFilter + desensitizeMsg）
        log.info("login phone={}, orderId={}",
                SensitiveLogArg.phone("13812348000"),
                "ORDER-13812348000");

        // 2) JSON message 脱敏（desensitizeJsonMsg）
        log.info("{\"phone\":\"13812348000\",\"idCard\":\"110101199001011234\",\"bizNo\":\"BIZ-1\"}");

        // 3) MDC 脱敏（MDC TurboFilter）
        MDC.put("phone", "13812348000");
        MDC.put("traceId", "TRACE-10001");
        try {
            log.info("mdc sample");
        } finally {
            MDC.clear();
        }

        return Map.of("success", true, "tips", "请查看日志输出观察脱敏效果");
    }
}

