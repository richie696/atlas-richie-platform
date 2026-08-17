package cn.richie696.gateway.error;

import cn.richie696.contract.model.ApiResult;
import cn.richie696.component.i18n.resolver.I18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 网关错误处理策略接口。
 * <p>
 * 默认实现 {@link DefaultErrorStrategy} 统一通过 {@link GatewayErrorRegistry}
 * 决定错误码、HTTP 状态、是否可重试、详情页相对路径（helpUrl）、i18n 键空间根，
 * 并写入响应体的 {@code requestId}（来自
 * {@link cn.richie696.gateway.filter.common.infrastructure.RequestIdGlobalFilter}）
 * 与 {@code helpUrl} 字段。
 * </p>
 *
 * <h3>环境行为差异</h3>
 * <ul>
 *   <li>开发 / 测试环境：返回详细异常信息（含堆栈），用于本地调试。</li>
 *   <li>生产环境：根据 HTTP 状态码从 Registry 选条目，使用 i18n key
 *       {@code <i18nKey>.meaning} 渲染用户消息，并将 requestId / helpUrl 写入响应体。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-04
 */
public interface ErrorStrategy {

    Logger log = LoggerFactory.getLogger(ErrorStrategy.class);

    /**
     * 处理错误并返回统一格式的响应体。
     *
     * @param statusCode      HTTP 状态码
     * @param errorAttributes Spring 错误属性（dev 环境使用）
     * @param isDevOrTest     是否开发或测试环境
     * @param requestId       全链路请求 ID，可能为 {@code null}
     * @return 错误响应 {@link ApiResult}
     */
    ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest, String requestId);

    /**
     * 提取 dev/test 环境的详细异常信息（含堆栈）。
     *
     * @param statusCode      HTTP 状态码
     * @param errorAttributes 错误属性
     * @param isDevOrTest     是否开发或测试环境
     * @return 错误消息字符串
     */
    default String extractErrorMessage(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest) {
        if (isDevOrTest) {
            StringBuilder errorMessage = new StringBuilder();
            Object message = errorAttributes.get("message");
            if (Objects.nonNull(message)) {
                errorMessage.append(I18n.get("ERROR_MESSAGE", message)).append("\n");
            }
            Object error = errorAttributes.get("error");
            if (Objects.nonNull(error)) {
                errorMessage.append(I18n.get("ERROR_TYPE", error)).append("\n");
            }
            Object trace = errorAttributes.get("trace");
            if (Objects.nonNull(trace)) {
                errorMessage.append(I18n.get("ERROR_STACK_TRACE")).append("\n").append(trace);
            } else {
                Object exception = errorAttributes.get("exception");
                if (Objects.nonNull(exception)) {
                    errorMessage.append(I18n.get("ERROR_EXCEPTION_CLASS", exception)).append("\n");
                }
            }
            return errorMessage.length() > 0 ? errorMessage.toString() : I18n.get("ERROR_UNKNOWN");
        }
        String errorId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        logErrorDetails(errorId, statusCode, errorAttributes);
        String errorKey = getErrorKeyByStatusCode(statusCode);
        String defaultMessage = I18n.get(errorKey);
        return I18n.get("ERROR_WITH_ID", defaultMessage, errorId);
    }

    /**
     * 记录生产环境的完整错误日志（含 errorId、path、exception、stack trace）。
     *
     * @param errorId         错误 ID
     * @param statusCode      HTTP 状态码
     * @param errorAttributes 错误属性
     */
    default void logErrorDetails(String errorId, HttpStatus statusCode, Map<String, Object> errorAttributes) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("错误ID: ").append(errorId).append("\n");
        logMessage.append("HTTP状态码: ").append(statusCode != null ? statusCode.value() : "未知").append("\n");
        Object path = errorAttributes.get("path");
        if (Objects.nonNull(path)) {
            logMessage.append("请求路径: ").append(path).append("\n");
        }
        Object message = errorAttributes.get("message");
        if (Objects.nonNull(message)) {
            logMessage.append("错误信息: ").append(message).append("\n");
        }
        Object error = errorAttributes.get("error");
        if (Objects.nonNull(error)) {
            logMessage.append("异常类型: ").append(error).append("\n");
        }
        Object exception = errorAttributes.get("exception");
        if (Objects.nonNull(exception)) {
            logMessage.append("异常类: ").append(exception).append("\n");
        }
        Object trace = errorAttributes.get("trace");
        if (Objects.nonNull(trace)) {
            logMessage.append("堆栈跟踪:\n").append(trace);
        }
        log.error("Gateway错误详情 - {}", logMessage.toString());
    }

    /**
     * 根据 HTTP 状态码返回旧的 {@code ERROR_*} i18n key（dev/test 环境使用）。
     *
     * @param statusCode HTTP 状态码
     * @return i18n key
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

    /**
     * 默认错误策略：dev/test 返回详细异常，prod 通过 Registry 选 GW-* 错误码。
     */
    class DefaultErrorStrategy implements ErrorStrategy {

        /**
         * 系统内部错误兜底码（Registry 找不到匹配时使用）。
         */
        private static final String FALLBACK_ERROR_CODE = "GW-SYSTEM-0001";

        @Override
        public ApiResult<Void> handle(HttpStatus statusCode, Map<String, Object> errorAttributes, boolean isDevOrTest, String requestId) {
            if (isDevOrTest) {
                String detailMessage = extractErrorMessage(statusCode, errorAttributes, true);
                return ApiResult.<Void>error(String.valueOf(statusCode != null ? statusCode.value() : HttpStatus.INTERNAL_SERVER_ERROR.value()), detailMessage)
                        .setRequestId(requestId);
            }
            int httpStatusValue = statusCode != null ? statusCode.value() : HttpStatus.INTERNAL_SERVER_ERROR.value();
            GatewayErrorEntry entry = GatewayErrorRegistry.getByHttpStatus(httpStatusValue);
            if (entry == null) {
                entry = GatewayErrorRegistry.getByCode(FALLBACK_ERROR_CODE);
            }
            String meaningKey = entry.getI18nKey() + ".meaning";
            String meaning = I18n.get(meaningKey);
            return ApiResult.<Void>error(entry.getErrorCode(), meaning)
                    .setRequestId(requestId)
                    .setHelpUrl(entry.getHelpUrl());
        }
    }
}
