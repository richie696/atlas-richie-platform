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
package com.richie.gateway.error;

import com.richie.contract.model.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 错误处理策略上下文
 * 根据 HTTP 状态码选择对应的错误处理策略，并根据环境（dev/test/prod）决定是否返回详细错误信息
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-16 18:03:18
 */
@Slf4j
@Component
public class ErrorStrategyContext {

    private final Map<HttpStatus, ErrorStrategy> strategies = new HashMap<>();
    private final Environment environment;

    public ErrorStrategyContext(Environment environment) {
        this.environment = environment;
        strategies.put(HttpStatus.BAD_REQUEST, new ErrorStrategy.BadRequestErrorStrategy());
        strategies.put(HttpStatus.UNAUTHORIZED, new ErrorStrategy.UnauthorizedErrorStrategy());
        strategies.put(HttpStatus.FORBIDDEN, new ErrorStrategy.ForbiddenErrorStrategy());
        strategies.put(HttpStatus.NOT_FOUND, new ErrorStrategy.NotFoundErrorStrategy());
        strategies.put(HttpStatus.METHOD_NOT_ALLOWED, new ErrorStrategy.MethodNotAllowedErrorStrategy());
        strategies.put(HttpStatus.NOT_ACCEPTABLE, new ErrorStrategy.NotAcceptableErrorStrategy());
        strategies.put(HttpStatus.PROXY_AUTHENTICATION_REQUIRED, new ErrorStrategy.ProxyAuthenticationRequiredErrorStrategy());
        strategies.put(HttpStatus.REQUEST_TIMEOUT, new ErrorStrategy.RequestTimeoutErrorStrategy());
        strategies.put(HttpStatus.UNSUPPORTED_MEDIA_TYPE, new ErrorStrategy.UnsupportedMediaTypeErrorStrategy());
        strategies.put(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, new ErrorStrategy.RequestedRangeNotSatisfiableErrorStrategy());
        strategies.put(HttpStatus.EXPECTATION_FAILED, new ErrorStrategy.ExpectationFailedErrorStrategy());
        strategies.put(HttpStatus.I_AM_A_TEAPOT, new ErrorStrategy.ImATeapotErrorStrategy());
        strategies.put(HttpStatus.UNPROCESSABLE_ENTITY, new ErrorStrategy.UnprocessableEntityErrorStrategy());
        strategies.put(HttpStatus.TOO_EARLY, new ErrorStrategy.TooEarlyErrorStrategy());
        strategies.put(HttpStatus.UPGRADE_REQUIRED, new ErrorStrategy.UpgradeRequiredErrorStrategy());
        strategies.put(HttpStatus.PRECONDITION_REQUIRED, new ErrorStrategy.PreconditionRequiredErrorStrategy());
        strategies.put(HttpStatus.TOO_MANY_REQUESTS, new ErrorStrategy.TooManyRequestsErrorStrategy());
        strategies.put(HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE, new ErrorStrategy.RequestHeaderFieldsTooLargeErrorStrategy());
        strategies.put(HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS, new ErrorStrategy.UnavailableForLegalReasonsErrorStrategy());
        strategies.put(HttpStatus.INTERNAL_SERVER_ERROR, new ErrorStrategy.InternalServerErrorErrorStrategy());
        strategies.put(HttpStatus.NOT_IMPLEMENTED, new ErrorStrategy.NotImplementedErrorStrategy());
        strategies.put(HttpStatus.BAD_GATEWAY, new ErrorStrategy.BadGatewayErrorStrategy());
        strategies.put(HttpStatus.SERVICE_UNAVAILABLE, new ErrorStrategy.ServiceUnavailableErrorStrategy());
        strategies.put(HttpStatus.GATEWAY_TIMEOUT, new ErrorStrategy.GatewayTimeoutErrorStrategy());
    }

    /**
     * 处理错误
     *
     * @param errorAttributes 错误属性，包含异常信息、堆栈跟踪等
     * @return 错误响应结果
     */
    public ApiResult<Void> handleError(Map<String, Object> errorAttributes) {
        Integer status = (Integer) errorAttributes.get("status");
        HttpStatus httpStatus = (status != null) ? HttpStatus.valueOf(status) : HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorStrategy strategy = strategies.getOrDefault(httpStatus, new ErrorStrategy.DefaultErrorStrategy());
        
        // 判断是否为开发或测试环境
        boolean isDevOrTest = isDevOrTestEnvironment();
        
        return strategy.handle(httpStatus, errorAttributes, isDevOrTest);
    }

    /**
     * 判断当前是否为开发或测试环境
     *
     * @return true 表示开发或测试环境，false 表示生产环境
     */
    private boolean isDevOrTestEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            // 如果没有配置 profile，检查默认 profile
            String[] defaultProfiles = environment.getDefaultProfiles();
            return Arrays.stream(defaultProfiles).anyMatch(profile -> 
                "dev".equalsIgnoreCase(profile) || "test".equalsIgnoreCase(profile));
        }
        return Arrays.stream(activeProfiles).anyMatch(profile -> 
            "dev".equalsIgnoreCase(profile) || "test".equalsIgnoreCase(profile));
    }
}
