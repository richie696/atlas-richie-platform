package com.richie.component.logging.handler;

import com.richie.component.concurrency.measurement.Stopwatch;
import com.richie.component.logging.annotations.LogMethodTrace;
import com.richie.component.logging.annotations.LogTrace;
import com.richie.component.logging.domain.LogTraceInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.springframework.core.annotation.Order;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 链路日志追踪切面
 *
 * @author richie696
 * @version 1.0
 * @since 2022-05-16 11:20:48
 */
@Slf4j
@Aspect
@Order(3)
@Component
@RequiredArgsConstructor
public class LogTraceAspect {

    /**
     * 切点：带 @LogTrace 的类或所有 *ServiceImpl 的 public 方法（需类上带 @LogTrace、方法上带 @LogMethodTrace 才记录）。
     */
    @Pointcut("@annotation(com.richie.component.logging.annotations.LogTrace) || execution(public * com.richie..*.service..*ServiceImpl.*(..))")
    private void useMethod() {
    }

    /**
     * 环绕增强：对带 @LogTrace 与 @LogMethodTrace 的方法记录入参、返回值、耗时、代码行等追踪日志。
     *
     * @param joinPoint 切点
     * @return 原方法返回值
     * @throws Throwable 原方法抛出的异常
     */
    @Around("useMethod()")
    public Object recordLogTrace(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取请求类型class
        var targetClass = joinPoint.getTarget().getClass();
        // 获取类上的注解
        var typeAnnotation = targetClass.getAnnotation(LogTrace.class);
        // 如果当前类没有日志追踪的注解则直接跳过本切面
        if (typeAnnotation == null) {
            return joinPoint.proceed();
        }
        // 获取方法签名
        var methodSignature = (MethodSignature) joinPoint.getSignature();
        // 获取方法上的注解
        var methodAnnotation = methodSignature.getMethod().getAnnotation(LogMethodTrace.class);
        if (methodAnnotation == null) {
            return joinPoint.proceed();
        }
        // 记录日志
        var logBuilder = LogTraceInfo.builder()
                .targetClass(targetClass.getName())
                .targetClassLabel(typeAnnotation.value());
        // 记录调用方法
        logBuilder
                .targetMethod(methodSignature.getMethod().getName())
                .targetMethodLabel(methodAnnotation.value());
        // 获取请求参数
        var args = joinPoint.getArgs();
        if (!methodAnnotation.ignoreArgs()) {
            logBuilder.arguments(Arrays.toString(args));
        }
        // 获取执行方法的代码行数
        logBuilder.codeLine(getLineNumber(methodSignature.getMethod()));
        // 获取当前执行线程的ID
        logBuilder
                .threadId(Thread.currentThread().threadId())
                .threadName(Thread.currentThread().getName());
        // 计时
        Stopwatch stopwatch = Stopwatch.createStarted();
        Instant startInstant = Instant.now();
        // 执行结果
        Object result;
        try {
            result = joinPoint.proceed();
            // 记录执行时间
            logBuilder
                    .costTimeMillis(stopwatch.stop().elapsed(TimeUnit.MILLISECONDS) + "ms")
                    .execStartTime(startInstant.atZone(ZoneId.systemDefault()).toString())
                    .execEndTime(Instant.now().atZone(ZoneId.systemDefault()).toString());
            if (!methodAnnotation.ignoreResult()) {
                logBuilder.result(result.toString());
            }
            // 根据日志级别记录日志
            switch (methodAnnotation.level()) {
                case TRACE -> log.trace(logBuilder.build().toString());
                case DEBUG -> log.debug(logBuilder.build().toString());
                case INFO -> log.info(logBuilder.build().toString());
                case WARN -> log.warn(logBuilder.build().toString());
                case ERROR -> log.error(logBuilder.build().toString());
                default -> System.out.println("Unknown log level: " + methodAnnotation.level());
            }
            return result;
        } catch (Throwable e) {
            // 记录执行时间
            logBuilder
                    .costTimeMillis(stopwatch.stop().elapsed(TimeUnit.MILLISECONDS) + "ms")
                    .execStartTime(startInstant.atZone(ZoneId.systemDefault()).toString())
                    .execEndTime(Instant.now().atZone(ZoneId.systemDefault()).toString());
            // 获取执行异常的代码行数
            var stackTrace = e.getStackTrace();
            // 记录异常堆栈
            logBuilder
                    .stacktrace(ExceptionUtils.getStackTrace(e))
                    .stacktraceLine(stackTrace.length > 0 ? stackTrace[0].getLineNumber() : null);
            log.error(logBuilder.build().toString());
            throw e;
        }
    }

    /**
     * 获取方法的代码行数
     *
     * @param method 要访问的切面方法
     * @return 返回方法的代码行数
     */
    private String getLineNumber(@Nonnull Method method) {
        try {
            var declaringClass = method.getDeclaringClass();
            var resource = declaringClass.getResource(declaringClass.getSimpleName() + ".class");
            if (resource == null) {
                log.error("Class resource not found for method: " + method.getName());
                return "";
            }

            StringBuilder builder = new StringBuilder();

            // 使用 try-with-resources 确保流在使用完毕后自动关闭
            try (var inputStream = resource.openStream()) {
                if (inputStream == null) {
                    log.error("Input stream is null for method: " + method.getName());
                    return "";
                }
                var byteCode = inputStream.readAllBytes();
                var classReader = new ClassReader(byteCode);
                var classNode = new ClassNode();
                classReader.accept(classNode, 0);

                var methodNode = classNode.methods.stream()
                        .filter(m -> m.name.equals(method.getName()) && m.desc.equals(org.objectweb.asm.Type.getMethodDescriptor(method)))
                        .findFirst().orElse(null);

                if (methodNode != null) {
                    var instructions = methodNode.instructions.iterator();
                    LineNumberNode startNode = null;
                    LineNumberNode endNode = null;

                    while (instructions.hasNext()) {
                        var node = instructions.next();
                        if (node instanceof LineNumberNode lineNumberNode) {
                            if (startNode == null) {
                                startNode = lineNumberNode;
                            }
                            endNode = lineNumberNode;
                        }
                    }
                    if (startNode == null) {
                        return "";
                    }
                    return builder.append(startNode.line).append(" ~ ").append(endNode.line).toString();
                }
            }
        } catch (Exception e) {
            log.error("Failed to get line number for method: " + method.getName(), e);
        }
        return "";
    }
}


