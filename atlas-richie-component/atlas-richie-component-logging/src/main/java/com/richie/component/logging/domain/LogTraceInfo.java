package com.richie.component.logging.domain;

import com.richie.component.logging.annotations.LogMethodTrace;
import com.richie.component.logging.annotations.LogTrace;
import com.richie.component.logging.handler.LogTraceAspect;
import com.richie.context.utils.data.JsonUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * 方法链路追踪日志的信息载体。
 * <p>
 * 由 {@link LogTraceAspect} 使用 {@link LogTrace}
 * 与 {@link LogMethodTrace} 收集并序列化输出。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-03 16:27:18
 */
@Builder(builderClassName = "Builder", toBuilder = true)
public class LogTraceInfo {

    /**
     * 目标类型标签
     */
    @JsonProperty("目标类型标签")
    private String targetClassLabel;
    /**
     * 目标类型
     */
    @JsonProperty("目标类型")
    private String targetClass;
    /**
     * 目标方法标签
     */
    @JsonProperty("目标方法标签")
    private String targetMethodLabel;
    /**
     * 目标方法
     */
    @JsonProperty("目标方法")
    private String targetMethod;
    /**
     * 参数
     */
    @JsonProperty("目标方法入参")
    private String arguments;
    /**
     * 返回值
     */
    @JsonProperty("目标方法返回值")
    private String result;
    /**
     * 方法开始行
     */
    @JsonProperty("目标方法代码行范围")
    private String codeLine;
    /**
     * 耗时
     */
    @JsonProperty("目标方法执行耗时")
    private String costTimeMillis;
    /**
     * 异常信息
     */
    @JsonProperty("异常信息堆栈")
    private String stacktrace;
    /**
     * 异常行号
     */
    @JsonProperty("异常代码行号")
    private Integer stacktraceLine;
    /**
     * 线程ID
     */
    @JsonProperty("执行线程ID")
    private long threadId;
    /**
     * 线程名称
     */
    @JsonProperty("执行线程名称")
    private String threadName;
    /**
     * 执行开始时间
     */
    @JsonProperty("记录开始时间(含时区信息)")
    private String execStartTime;
    /**
     * 执行结束时间
     */
    @JsonProperty("记录结束时间(含时区信息)")
    private String execEndTime;



    @Override
    public String toString() {
        return JsonUtils.getInstance().serialize(this);
    }
}
