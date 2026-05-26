package com.richie.component.logging.callback;

import com.richie.contract.model.ApiResult;
import com.richie.component.logging.domain.AccessLogInfo;
import jakarta.annotation.Nullable;
import org.aspectj.lang.ProceedingJoinPoint;

import java.util.Map;

/**
 * 日志生命周期回调函数式接口
 * 用于在日志记录的不同阶段执行自定义逻辑
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-XX
 */
public final class LogLifecycleCallback {

    /**
     * 日志记录前回调函数式接口
     * 函数签名: (Map&lt;String, Object&gt; requestData, ResultVO&lt;?&gt; responseData) -&gt; AccessLogInfo
     */
    @FunctionalInterface
    public interface BeforeLogCallback {
        /**
         * 应用回调函数
         *
         * @param requestData  请求数据
         * @param responseData 响应数据
         * @return 返回修改后的日志信息，如果返回null则使用默认生成的日志信息
         */
        AccessLogInfo apply(Map<String, Object> requestData, @Nullable ApiResult<?> responseData);
    }

    /**
     * 日志记录后回调函数式接口
     * 函数签名: (AccessLogInfo logInfo, Throwable throwable) -&gt; Boolean
     */
    @FunctionalInterface
    public interface AfterLogCallback {
        /**
         * 应用回调函数
         *
         * @param logInfo   日志信息
         * @param throwable 异常信息（如果有）
         * @return 返回true表示继续执行后续流程，false表示终止执行
         */
        boolean apply(AccessLogInfo logInfo, Throwable throwable);
    }

    /**
     * 持久化前回调函数式接口
     * 函数签名: (AccessLogInfo logInfo, ProceedingJoinPoint joinPoint) -&gt; AccessLogInfo
     */
    @FunctionalInterface
    public interface BeforePersistCallback {
        /**
         * 应用回调函数
         *
         * @param logInfo   日志信息
         * @param joinPoint 切点
         * @return 返回修改后的日志信息，如果返回null则使用原始日志信息
         */
        AccessLogInfo apply(AccessLogInfo logInfo, ProceedingJoinPoint joinPoint);
    }

    /**
     * 异常处理回调函数式接口
     * 函数签名: (Map&lt;String, Object&gt; requestData, Throwable throwable) -&gt; ResultVO&lt;?&gt;
     */
    @FunctionalInterface
    public interface OnErrorCallback {
        /**
         * 应用回调函数
         *
         * @param requestData 请求数据
         * @param throwable   异常信息
         * @return 返回处理后的响应数据，如果返回null则使用默认错误响应
         */
        ApiResult<?> apply(Map<String, Object> requestData, Throwable throwable);
    }

    /**
     * 工具类，禁止实例化。
     */
    private LogLifecycleCallback() {
    }
}

