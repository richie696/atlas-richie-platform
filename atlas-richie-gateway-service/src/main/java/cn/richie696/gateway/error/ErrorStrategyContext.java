package cn.richie696.gateway.error;

import cn.richie696.contract.model.ApiResult;
import cn.richie696.gateway.filter.common.infrastructure.RequestIdGlobalFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.Arrays;
import java.util.Map;

/**
 * 错误处理策略上下文。
 * <p>
 * 使用单例 {@link ErrorStrategy.DefaultErrorStrategy}，根据 HTTP 状态码从
 * {@link GatewayErrorRegistry} 选条目，并从
 * {@link ServerWebExchange#getAttribute(String)} 读取全链路
 * {@code requestId}（由 {@link RequestIdGlobalFilter} 写入），最终写入响应体的
 * {@code requestId} 与 {@code helpUrl} 字段。
 * </p>
 *
 * @author richie696
 * @since 2026-08-04
 */
@Slf4j
@Component
public class ErrorStrategyContext {

    /**
     * 默认错误策略单例。
     */
    private final ErrorStrategy strategy = new ErrorStrategy.DefaultErrorStrategy();

    /**
     * Spring 环境（用于判断 dev / test）。
     */
    private final Environment environment;

    /**
     * 构造方法。
     *
     * @param environment Spring 环境
     */
    public ErrorStrategyContext(Environment environment) {
        this.environment = environment;
    }

    /**
     * 处理错误并返回统一格式的响应体。
     * <p>
     * 从 {@code ServerWebExchange} 读取 requestId（无 exchange 时为 {@code null}）。
     * </p>
     *
     * @param errorAttributes Spring 错误属性
     * @param exchange        ServerWebExchange（可为 {@code null}）
     * @return 错误响应
     */
    public ApiResult<Void> handleError(Map<String, Object> errorAttributes, ServerWebExchange exchange) {
        Integer status = (Integer) errorAttributes.get("status");
        HttpStatus httpStatus = (status != null) ? HttpStatus.valueOf(status) : HttpStatus.INTERNAL_SERVER_ERROR;
        String requestId = resolveRequestId(exchange);
        return strategy.handle(httpStatus, errorAttributes, isDevOrTestEnvironment(), requestId);
    }

    /**
     * 处理错误（不携带 exchange 的兼容版本）。
     *
     * @param errorAttributes Spring 错误属性
     * @return 错误响应
     */
    public ApiResult<Void> handleError(Map<String, Object> errorAttributes) {
        return handleError(errorAttributes, null);
    }

    /**
     * 从 exchange 读取 requestId。
     *
     * @param exchange ServerWebExchange
     * @return requestId，可能为 {@code null}
     */
    private String resolveRequestId(ServerWebExchange exchange) {
        if (exchange == null) {
            return null;
        }
        return exchange.getAttribute(RequestIdGlobalFilter.ATTRIBUTE_KEY);
    }

    /**
     * 判断当前是否为开发或测试环境。
     *
     * @return true 表示开发或测试环境
     */
    private boolean isDevOrTestEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            String[] defaultProfiles = environment.getDefaultProfiles();
            return Arrays.stream(defaultProfiles).anyMatch(profile ->
                    "dev".equalsIgnoreCase(profile) || "test".equalsIgnoreCase(profile));
        }
        return Arrays.stream(activeProfiles).anyMatch(profile ->
                "dev".equalsIgnoreCase(profile) || "test".equalsIgnoreCase(profile));
    }
}
