/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.gateway.error;

import cn.richie696.contract.model.ApiResult;
import cn.richie696.gateway.filter.common.infrastructure.RequestIdGlobalFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;
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

    private final ErrorStrategy strategy = new ErrorStrategy.DefaultErrorStrategy();
    private final Environment environment;

    public ErrorStrategyContext(Environment environment) {
        this.environment = environment;
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
        return strategy.handle(httpStatus, errorAttributes, isDevOrTestEnvironment(), null);
    }

    public ApiResult<Void> handleError(Map<String, Object> errorAttributes, org.springframework.web.server.ServerWebExchange exchange) {
        Integer status = (Integer) errorAttributes.get("status");
        HttpStatus httpStatus = status != null ? HttpStatus.valueOf(status) : HttpStatus.INTERNAL_SERVER_ERROR;
        String requestId = exchange == null ? null : exchange.getAttribute(RequestIdGlobalFilter.ATTRIBUTE_KEY);
        return strategy.handle(httpStatus, errorAttributes, isDevOrTestEnvironment(), requestId);
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
