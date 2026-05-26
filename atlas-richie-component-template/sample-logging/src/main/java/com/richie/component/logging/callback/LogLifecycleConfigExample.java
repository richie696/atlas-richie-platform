package com.richie.component.logging.callback;

import com.richie.contract.model.ApiResult;
import com.richie.component.logging.domain.AccessLogInfo;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 日志生命周期回调配置示例
 * <p>
 * 组件会自动从 Spring 容器中扫描实现了回调接口的 Bean。
 * 只需在实现类上添加 {@code @Component} 注解即可，无需任何 YAML 配置。
 * 
 * <p>
 * 如果存在多个实现，组件会按以下优先级选择：
 * <ol>
 *   <li>{@code @Primary} 标记的实现（最高优先级）</li>
 *   <li>{@code @Order} 注解值最小的实现</li>
 *   <li>默认按 Bean 名称排序</li>
 * </ol>
 * 
 *
 * @author richie696
 * @version 2.0
 * @since 2025-11-20
 */
public class LogLifecycleConfigExample {

    /**
     * 示例1: 日志记录前回调实现
     * <p>
     * 使用方式：只需添加 {@code @Component} 注解，组件会自动扫描并注册。
     * 无需任何 YAML 配置。
     * 
     * <p>
     * 功能说明：
     * <ul>
     *   <li>在日志记录前执行，可以自定义生成日志信息</li>
     *   <li>如果返回 null，则使用默认的日志生成逻辑</li>
     *   <li>如果返回非 null，则使用返回的日志信息</li>
     * </ul>
     * 
     */
    @Slf4j
    @Component // 自动扫描，无需配置
    public static class CustomBeforeLogCallback implements LogLifecycleCallback.BeforeLogCallback {
        @Override
        public AccessLogInfo apply(Map<String, Object> requestData, ApiResult<?> responseData) {
            // 自定义逻辑：可以修改请求数据或响应数据，然后生成日志信息
            // 如果返回null，则使用默认的日志生成逻辑
            log.info("[自定义回调] 日志记录前回调被调用，请求数据: {}, 响应代码: {}",
                    requestData, responseData != null ? responseData.getCode() : "null");
            // 返回null使用默认实现
            return null;
        }
    }

    /**
     * 示例2: 日志记录后回调实现
     * <p>
     * 使用方式：只需添加 {@code @Component} 注解，组件会自动扫描并注册。
     * 
     * <p>
     * 功能说明：
     * <ul>
     *   <li>在日志记录完成后执行，可以执行后续处理（如发送通知、记录到其他系统等）</li>
     *   <li>返回 true 表示继续执行后续流程，false 表示终止执行</li>
     * </ul>
     * 
     */
    @Slf4j
    @Component // 自动扫描，无需配置
    public static class CustomAfterLogCallback implements LogLifecycleCallback.AfterLogCallback {
        @Override
        public boolean apply(AccessLogInfo logInfo, Throwable throwable) {
            // 自定义逻辑：例如发送通知、记录到其他系统等
            // 返回true继续执行，false终止执行
            if (throwable != null) {
                // 发生异常时的处理
                log.error("[自定义回调] 日志记录时发生异常: {}", throwable.getMessage(), throwable);
            } else {
                log.info("[自定义回调] 日志记录完成，操作人: {}, URL: {}",
                        logInfo.getOperator(), logInfo.getUrl());
            }
            return true; // 继续执行
        }
    }

    /**
     * 示例3: 持久化前回调实现
     * <p>
     * 使用方式：只需添加 {@code @Component} 注解，组件会自动扫描并注册。
     * 
     * <p>
     * 功能说明：
     * <ul>
     *   <li>在持久化前执行，可以修改日志信息（如脱敏敏感信息、添加额外字段等）</li>
     *   <li>如果返回 null，则使用原始日志信息</li>
     *   <li>如果返回非 null，则使用返回的修改后的日志信息</li>
     * </ul>
     * 
     */
    @Slf4j
    @Component // 自动扫描，无需配置
    public static class CustomBeforePersistCallback implements LogLifecycleCallback.BeforePersistCallback {
        @Override
        public AccessLogInfo apply(AccessLogInfo logInfo, ProceedingJoinPoint joinPoint) {
            // 自定义逻辑：在持久化前修改日志信息
            // 例如添加额外字段、脱敏敏感信息等
            log.info("[自定义回调] 持久化前回调被调用");

            // 示例：脱敏敏感信息（如果请求体中包含密码）
            String requestBody = logInfo.getRequestBody();
            if (requestBody != null && requestBody.contains("password")) {
                // 简单的脱敏处理（实际应用中应使用更完善的脱敏工具）
                requestBody = requestBody.replaceAll("\"password\"\\s*:\\s*\"[^\"]+\"", "\"password\":\"******\"");
                logInfo.setRequestBody(requestBody);
            }

            // 添加自定义扩展信息
            logInfo.setExtra("custom data added before persist");
            return logInfo; // 返回修改后的日志信息
        }
    }

    /**
     * 示例4: 异常处理回调实现
     * <p>
     * 使用方式：只需添加 {@code @Component} 注解，组件会自动扫描并注册。
     * 
     * <p>
     * 功能说明：
     * <ul>
     *   <li>在发生异常时执行，可以自定义错误响应</li>
     *   <li>如果返回 null，则使用默认的错误响应</li>
     *   <li>如果返回非 null，则使用返回的自定义错误响应</li>
     * </ul>
     * 
     */
    @Slf4j
    @Component // 自动扫描，无需配置
    public static class CustomOnErrorCallback implements LogLifecycleCallback.OnErrorCallback {
        @Override
        public ApiResult<?> apply(Map<String, Object> requestData, Throwable throwable) {
            // 自定义错误处理逻辑
            // 返回null则使用默认的错误响应
            log.error("[自定义回调] 异常处理回调被调用，异常类型: {}, 异常消息: {}",
                    throwable.getClass().getName(), throwable.getMessage());

            // 可以根据异常类型返回不同的错误响应
            if (throwable instanceof IllegalArgumentException) {
                return ApiResult.error("ILLEGAL_ARGUMENT", "参数错误: " + throwable.getMessage());
            } else if (throwable instanceof NullPointerException) {
                return ApiResult.error("NULL_POINTER", "空指针异常: " + throwable.getMessage());
            }

            // 默认错误响应
            return ApiResult.error("CUSTOM_ERROR", "自定义错误信息: " + throwable.getMessage());
        }
    }

    /**
     * 示例5: 使用 @Primary 标记优先级的回调实现
     * <p>
     * 当存在多个实现时，使用 {@code @Primary} 注解可以标记该实现为优先使用。
     * 
     */
    @Slf4j
    @Component
    @Primary // 标记为优先使用（当存在多个实现时）
    public static class PrimaryBeforeLogCallback implements LogLifecycleCallback.BeforeLogCallback {
        @Override
        public AccessLogInfo apply(Map<String, Object> requestData, ApiResult<?> responseData) {
            log.info("[Primary回调] 这是优先使用的回调实现");
            return null;
        }
    }

    /**
     * 示例6: 使用 @Order 标记优先级的回调实现
     * <p>
     * 当存在多个实现且都没有 {@code @Primary} 注解时，使用 {@code @Order} 注解可以控制优先级。
     * Order 值越小，优先级越高。
     * 
     */
    @Slf4j
    @Component
    @Order(1) // Order 值越小，优先级越高
    public static class OrderedBeforeLogCallback implements LogLifecycleCallback.BeforeLogCallback {
        @Override
        public AccessLogInfo apply(Map<String, Object> requestData, ApiResult<?> responseData) {
            log.info("[Ordered回调] Order=1 的回调实现");
            return null;
        }
    }
}

