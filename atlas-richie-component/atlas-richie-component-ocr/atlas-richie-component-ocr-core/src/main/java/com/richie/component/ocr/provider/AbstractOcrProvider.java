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
package com.richie.component.ocr.provider;

import com.richie.component.ocr.exception.OcrException;
import com.richie.component.ocr.model.OcrImage;
import com.richie.component.ocr.model.OcrOptions;
import com.richie.component.ocr.model.OcrResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider 抽象基类 —— 模板方法模式（L2 单 vendor 模式）。
 *
 * <p>Provider 实现只做"参数翻译 + 协议适配"，不持有任何横切原语。
 * Provider 实现不调用 {@code RateLimiter} / {@code CircuitBreaker} / {@code Retryer}。
 * Provider 内部不持有 {@code ExecutorService} / {@code ThreadPoolExecutor}。
 *
 * <p>L2 实现契约:
 * <ul>
 *   <li>vendor 私有配置（typed {@code *Properties}）由 vendor 子类构造器读取; core 完全不感知 vendor 字段</li>
 *   <li>{@link #recognize} 模板: 翻译 → 调用 → 翻译; 异常原样抛出 (由调用方处理)</li>
 * </ul>
 *
 * <p><b>可见性说明</b>: 本类保持 {@code public} —— vendor Maven 模块（如 {@code atlas-richie-component-ocr-aliyun}）
 * 的 Provider 必须 extends 本类, 跨 package 子类化要求父类 public。业务侧不应直接继承本类,
 * 而应通过 {@link OcrProvider} 接口交互（直接 {@code @Autowired AliyunOcrProvider} 即可）。
 *
 * @param <REQ> Provider 专用请求类型
 * @param <RES> Provider 专用响应类型
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public abstract class AbstractOcrProvider<REQ, RES> implements OcrProvider {

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected AbstractOcrProvider() {
    }

    /**
     * 子类日志访问器（避免每个 vendor Provider 都写一遍 {@code LoggerFactory.getLogger}）。
     *
     * @return 与本类同名的 SLF4J Logger
     */
    protected final Logger log() {
        return log;
    }

    // ---- 子类必须实现 ----

    /**
     * 调底层 Provider（HTTP API / 本地进程 / gRPC）。
     *
     * @param request Provider 专用请求对象
     * @return Provider 专用响应对象
     */
    protected abstract RES callProvider(REQ request);

    /**
     * Provider 特有参数 ← → 通用 OcrOptions 转换。
     *
     * @param image   待识别图像
     * @param options 通用选项
     * @return Provider 专用请求对象
     */
    protected abstract REQ toProviderRequest(OcrImage image, OcrOptions options);

    /**
     * Provider 特有响应 ← → 通用 OcrResult 转换。
     *
     * @param response Provider 专用响应对象
     * @return 统一 {@link OcrResult}
     */
    protected abstract OcrResult fromProviderResponse(RES response);

    // ---- 模板方法 ----

    /**
     * 模板方法：解析 → 调用 → 翻译 —— L2 版本不维护任何 health 状态。
     *
     * <p>异常传递策略:
     * <ul>
     *   <li>{@link OcrException} 直接抛出 —— 这是 vendor 已识别的业务异常</li>
     *   <li>其他 checked / unchecked 异常包装成 {@link OcrException.ProviderUnavailable}</li>
     * </ul>
     *
     * @param image   待识别图像
     * @param options 通用选项
     * @return 识别结果
     * @throws OcrException 调用或翻译失败时抛出
     */
    @Override
    public OcrResult recognize(OcrImage image, OcrOptions options) {
        REQ request;
        try {
            request = toProviderRequest(image, options);
        } catch (RuntimeException e) {
            throw new OcrException.ProviderUnavailable("ocr", null, e);
        }
        RES response;
        try {
            response = callProvider(request);
        } catch (OcrException e) {
            throw e;
        } catch (Exception e) {
            throw new OcrException.ProviderUnavailable("ocr", null, e);
        }
        try {
            return fromProviderResponse(response);
        } catch (RuntimeException e) {
            throw new OcrException.ProviderUnavailable("ocr", null, e);
        }
    }

}
