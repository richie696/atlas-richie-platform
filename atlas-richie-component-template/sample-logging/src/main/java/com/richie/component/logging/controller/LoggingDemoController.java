package com.richie.component.logging.controller;

import com.richie.contract.model.ApiResult;
import com.richie.component.logging.annotations.AccessLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 日志组件演示控制器
 * 演示访问日志和生命周期回调的使用
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-XX
 */
@Slf4j
@RestController
@RequestMapping("/api/logging")
public class LoggingDemoController {

    /**
     * 示例1: 普通查询接口（记录日志，不持久化）
     */
    @GetMapping("/info")
    @AccessLog("查询日志信息")
    public ApiResult<Map<String, String>> getInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("message", "这是一个日志组件演示接口");
        info.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return ApiResult.success(info);
    }

    /**
     * 示例2: 创建操作（记录日志并持久化）
     */
    @PostMapping("/create")
    @AccessLog(value = "创建日志记录", persistent = true)
    public ApiResult<Map<String, Object>> create(@RequestBody Map<String, Object> data) {
        log.info("创建操作，数据: {}", data);
        Map<String, Object> result = new HashMap<>();
        result.put("id", System.currentTimeMillis());
        result.put("data", data);
        result.put("status", "created");
        return ApiResult.success(result);
    }

    /**
     * 示例3: 登录接口（包含敏感信息，会触发脱敏回调）
     */
    @PostMapping("/login")
    @AccessLog(value = "用户登录", persistent = true)
    public ApiResult<Map<String, String>> login(@RequestBody Map<String, String> credentials) {
        log.info("用户登录，用户名: {}", credentials.get("username"));
        Map<String, String> result = new HashMap<>();
        result.put("token", "mock-token-" + System.currentTimeMillis());
        result.put("username", credentials.get("username"));
        return ApiResult.success(result);
    }

    /**
     * 示例4: 异常接口（用于测试异常处理回调）
     */
    @GetMapping("/error")
    @AccessLog("测试异常处理")
    public ApiResult<String> testError(@RequestParam(required = false) String type) {
        if ("illegal".equals(type)) {
            throw new IllegalArgumentException("这是一个参数错误示例");
        } else if ("null".equals(type)) {
            throw new NullPointerException("这是一个空指针异常示例");
        } else {
            throw new RuntimeException("这是一个运行时异常示例");
        }
    }

    /**
     * 示例5: 带路径参数的查询
     */
    @GetMapping("/detail/{id}")
    @AccessLog("查询详情")
    public ApiResult<Map<String, Object>> getDetail(@PathVariable String id) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("id", id);
        detail.put("name", "示例数据");
        detail.put("description", "这是一个带路径参数的查询示例");
        return ApiResult.success(detail);
    }
}

