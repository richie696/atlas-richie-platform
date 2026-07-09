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
package com.richie.gateway.error;

import com.richie.contract.model.ApiResult;
import com.richie.component.i18n.resolver.I18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 错误处理策略接口
 * 根据 HTTP 状态码和错误属性生成错误响应
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-16 18:03:18
 */
public interface ErrorStrategy {

    Logger log = LoggerFactory.getLogger(ErrorStrategy.class);

    /**
     * 处理错误
     *
     * @param statusCode HTTP 状态码
     * @param errorAttributes 错误属性，包含异常信息、堆栈跟踪等
     * @param isDevOrTest 是否为开发或测试环境
     * @return 错误响应结果
     */
    ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest);

    /**
     * 从错误属性中提取错误消息
     * 开发/测试环境：返回详细异常信息和堆栈
     * 生产环境：返回封装后的通用错误信息，并生成唯一错误ID用于日志关联
     *
     * @param statusCode HTTP 状态码
     * @param errorAttributes 错误属性
     * @param isDevOrTest 是否为开发或测试环境
     * @return 错误消息（包含错误ID，如果是在生产环境）
     */
    default String extractErrorMessage(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
        if (isDevOrTest) {
            // 开发/测试环境：返回详细异常信息
            StringBuilder errorMessage = new StringBuilder();
            
            // 获取异常消息
            Object message = errorAttributes.get("message");
            if (Objects.nonNull(message)) {
                errorMessage.append(I18n.get("ERROR_MESSAGE", message)).append("\n");
            }
            
            // 获取异常类型
            Object error = errorAttributes.get("error");
            if (Objects.nonNull(error)) {
                errorMessage.append(I18n.get("ERROR_TYPE", error)).append("\n");
            }
            
            // 获取堆栈跟踪
            Object trace = errorAttributes.get("trace");
            if (Objects.nonNull(trace)) {
                errorMessage.append(I18n.get("ERROR_STACK_TRACE")).append("\n").append(trace);
            } else {
                // 如果没有 trace，尝试从 exception 字段获取
                Object exception = errorAttributes.get("exception");
                if (Objects.nonNull(exception)) {
                    errorMessage.append(I18n.get("ERROR_EXCEPTION_CLASS", exception)).append("\n");
                }
            }
            
            return errorMessage.length() > 0 ? errorMessage.toString() : I18n.get("ERROR_UNKNOWN");
        } else {
            // 生产环境：生成唯一错误ID，记录详细日志，返回包含错误ID的通用错误信息
            String errorId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            
            // 记录详细错误信息到日志（包含错误ID）
            logErrorDetails(errorId, statusCode, errorAttributes);
            
            // 根据 HTTP 状态码返回对应的国际化错误消息，并包含错误ID
            String errorKey = getErrorKeyByStatusCode(statusCode);
            String defaultMessage = I18n.get(errorKey);
            
            // 返回包含错误ID的错误消息
            return I18n.get("ERROR_WITH_ID", defaultMessage, errorId);
        }
    }

    /**
     * 记录详细错误信息到日志（仅在生产环境调用）
     *
     * @param errorId 错误ID
     * @param statusCode HTTP 状态码
     * @param errorAttributes 错误属性
     */
    default void logErrorDetails(String errorId, HttpStatus statusCode, Map<String, Object> errorAttributes) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("错误ID: ").append(errorId).append("\n");
        logMessage.append("HTTP状态码: ").append(statusCode != null ? statusCode.value() : "未知").append("\n");
        
        // 获取请求路径
        Object path = errorAttributes.get("path");
        if (Objects.nonNull(path)) {
            logMessage.append("请求路径: ").append(path).append("\n");
        }
        
        // 获取异常消息
        Object message = errorAttributes.get("message");
        if (Objects.nonNull(message)) {
            logMessage.append("错误信息: ").append(message).append("\n");
        }
        
        // 获取异常类型
        Object error = errorAttributes.get("error");
        if (Objects.nonNull(error)) {
            logMessage.append("异常类型: ").append(error).append("\n");
        }
        
        // 获取异常类
        Object exception = errorAttributes.get("exception");
        if (Objects.nonNull(exception)) {
            logMessage.append("异常类: ").append(exception).append("\n");
        }
        
        // 获取堆栈跟踪
        Object trace = errorAttributes.get("trace");
        if (Objects.nonNull(trace)) {
            logMessage.append("堆栈跟踪:\n").append(trace);
        }
        
        // 记录错误日志
        log.error("Gateway错误详情 - {}", logMessage.toString());
    }

    /**
     * 根据 HTTP 状态码获取对应的错误消息 key
     *
     * @param statusCode HTTP 状态码
     * @return 错误消息 key
     */
    default String getErrorKeyByStatusCode(HttpStatus statusCode) {
        if (statusCode == null) {
            return "ERROR_INTERNAL";
        }
        return switch (statusCode) {
            case BAD_REQUEST -> "ERROR_BAD_REQUEST";
            case UNAUTHORIZED -> "ERROR_UNAUTHORIZED";
            case FORBIDDEN -> "ERROR_FORBIDDEN";
            case NOT_FOUND -> "ERROR_NOT_FOUND";
            case METHOD_NOT_ALLOWED -> "ERROR_METHOD_NOT_ALLOWED";
            case INTERNAL_SERVER_ERROR -> "ERROR_INTERNAL_SERVER";
            case BAD_GATEWAY -> "ERROR_BAD_GATEWAY";
            case SERVICE_UNAVAILABLE -> "ERROR_SERVICE_UNAVAILABLE";
            case GATEWAY_TIMEOUT -> "ERROR_GATEWAY_TIMEOUT";
            default -> "ERROR_INTERNAL";
        };
    }


    class DefaultErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.BAD_REQUEST(400) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:08:32
     */
    class BadRequestErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.UNAUTHORIZED(401) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:08:52
     */
    class UnauthorizedErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.FORBIDDEN(403) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:09:03
     */
    class ForbiddenErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.NOT_FOUND(404) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:09:20
     */
    class NotFoundErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.METHOD_NOT_ALLOWED(405) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:09:36
     */
    class MethodNotAllowedErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.NOT_ACCEPTABLE(406) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:09:49
     */
    class NotAcceptableErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.PROXY_AUTHENTICATION_REQUIRED(407) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:09:49
     */
    class ProxyAuthenticationRequiredErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.REQUEST_TIMEOUT(408) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:09:52
     */
    class RequestTimeoutErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.UNSUPPORTED_MEDIA_TYPE(415) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:32:09
     */
    class UnsupportedMediaTypeErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE(416) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:32:09
     */
    class RequestedRangeNotSatisfiableErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.EXPECTATION_FAILED(417) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:32:09
     */
    class ExpectationFailedErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.IM_A_TEAPOT(418) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:32:09
     */
    class ImATeapotErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.UNPROCESSABLE_ENTITY(422) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:32:09
     */
    class UnprocessableEntityErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.TOO_EARLY(425) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:32:09
     */
    class TooEarlyErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.UPGRADE_REQUIRED(426) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:32:09
     */
    class UpgradeRequiredErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.PRECONDITION_REQUIRED(428) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:32:09
     */
    class PreconditionRequiredErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.TOO_MANY_REQUESTS(429) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:32:09
     */
    class TooManyRequestsErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE(431) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:32:09
     */
    class RequestHeaderFieldsTooLargeErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS(451) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:32:09
     */
    class UnavailableForLegalReasonsErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.INTERNAL_SERVER_ERROR(500) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:35:25
     */
    class InternalServerErrorErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.NOT_IMPLEMENTED(501) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:35:25
     */
    class NotImplementedErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.BAD_GATEWAY(502) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:35:25
     */
    class BadGatewayErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.SERVICE_UNAVAILABLE(503) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:35:25
     */
    class ServiceUnavailableErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.GATEWAY_TIMEOUT(504) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:35:25
     */
    class GatewayTimeoutErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

    /**
     * HttpStatus.HTTP_VERSION_NOT_SUPPORTED(505) 错误处理策略
     *
     * @author richie696
     * @version 1.0
     * @since 2025-01-16 18:35:25
     */
    class HttpVersionNotSupportedErrorStrategy implements ErrorStrategy {
        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
            String errorMessage = extractErrorMessage(statusCode, errorAttributes, isDevOrTest);
            return ApiResult.error(statusCode.value() + "", errorMessage);
        }
    }

}
