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
package com.richie.component.nats.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * NATS 消息处理管道构建器
 *
 * <p>使用装饰器模式将横切关注点（追踪 → 上下文恢复 → 去重）链式包装在业务 Handler 外层。
 * 装饰器按添加顺序从外到内包装：先添加的在最外层执行。</p>
 *
 * <p>示例构建顺序（外层到内层）：</p>
 * <pre>{@code
 * pipeline.addDecorator(tracing)      // 最外层：追踪
 *         .addDecorator(context)      // 中间层：上下文恢复
 *         .addDecorator(idempotent)   // 内层：去重
 *         .build(businessHandler);    // 核心：业务处理
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 */
public class NatsMessageHandlerPipeline {

    private final List<Function<NatsMessageHandler, NatsMessageHandler>> decorators = new ArrayList<>();

    /**
     * 添加装饰器
     *
     * @param decorator 装饰器工厂函数：接受内层 Handler，返回包装后的 Handler
     * @return 当前构建器（链式调用）
     */
    public NatsMessageHandlerPipeline addDecorator(
            Function<NatsMessageHandler, NatsMessageHandler> decorator) {
        decorators.add(decorator);
        return this;
    }

    /**
     * 构建最终管道：装饰器按添加顺序从外到内包装业务 Handler
     *
     * @param businessHandler 业务处理 Handler
     * @return 完整的管道 Handler
     */
    public NatsMessageHandler build(NatsMessageHandler businessHandler) {
        NatsMessageHandler current = businessHandler;
        // 从最后一个装饰器开始包装，使第一个添加的在最外层
        for (int i = decorators.size() - 1; i >= 0; i--) {
            current = decorators.get(i).apply(current);
        }
        return current;
    }
}
