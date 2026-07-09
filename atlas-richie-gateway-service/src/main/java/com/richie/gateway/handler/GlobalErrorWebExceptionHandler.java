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
package com.richie.gateway.handler;

import com.richie.gateway.error.ErrorStrategyContext;
import jakarta.annotation.Nonnull;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webflux.autoconfigure.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.webflux.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {

    private final ErrorStrategyContext errorStrategyContext;

    /**
     * 全局异常处理
     *
     * @param errorAttributes 错误属性
     * @param resources 资源
     * @param applicationContext 应用上下文
     * @param serverCodecConfigurer 服务器编解码器配置
     * @param viewResolvers 视图解析器
     * @param errorStrategyContext 错误策略上下文
     */
    public GlobalErrorWebExceptionHandler(ErrorAttributes errorAttributes,
                                          WebProperties.Resources resources,
                                          ApplicationContext applicationContext,
                                          ServerCodecConfigurer serverCodecConfigurer,
                                          List<ViewResolver> viewResolvers,
                                          ErrorStrategyContext errorStrategyContext) {
        super(errorAttributes, resources, applicationContext);
        this.errorStrategyContext = errorStrategyContext;
        super.setMessageWriters(serverCodecConfigurer.getWriters());
        super.setMessageReaders(serverCodecConfigurer.getReaders());
        super.setViewResolvers(viewResolvers);
    }

    @Nonnull
    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(@Nonnull ErrorAttributes errorAttributes) {
        return RouterFunctions.route(_ -> true, this::renderErrorResponse);
    }

    private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        // 定义需要的错误属性（包含堆栈跟踪信息，用于开发/测试环境）
        var options = ErrorAttributeOptions.of(
                ErrorAttributeOptions.Include.MESSAGE,
                ErrorAttributeOptions.Include.EXCEPTION,
                ErrorAttributeOptions.Include.STACK_TRACE,
                ErrorAttributeOptions.Include.BINDING_ERRORS,
                ErrorAttributeOptions.Include.ERROR,
                ErrorAttributeOptions.Include.STATUS
        );
        // 获取异常属性内容
        var errorPropertiesMap = getErrorAttributes(request, options);
        // 调用错误策略上下文处理异常
        var result = errorStrategyContext.handleError(errorPropertiesMap);
        // 从错误属性中提取 HTTP 状态码，如果没有则使用 500
        Integer status = (Integer) errorPropertiesMap.get("status");
        HttpStatus httpStatus = status != null ? HttpStatus.valueOf(status) : HttpStatus.INTERNAL_SERVER_ERROR;
        // 返回结果给客户端
        return ServerResponse.status(httpStatus)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result);
    }


}
