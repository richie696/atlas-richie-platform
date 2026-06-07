package com.richie.component.mongodb.circuitbreaker;

import com.richie.component.mongodb.Mongodb;
import com.richie.component.mongodb.builder.DeleteBuilder;
import com.richie.component.mongodb.builder.PageResult;
import com.richie.component.mongodb.builder.QueryBuilder;
import com.richie.component.mongodb.builder.UpdateBuilder;
import com.richie.component.mongodb.observability.MongodbTracing;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Aspect
@Component
public class MongodbSentinelAspect {

    @Pointcut("execution(public * com.richie.component.mongodb.Mongodb.*(..))")
    public void mongodbOperations() {
    }

    @Around("mongodbOperations()")
    public Object aroundMongodbOperations(ProceedingJoinPoint pjp) throws Throwable {
        String methodName = pjp.getSignature().getName();
        String resourceName = mapToResourceName(methodName);

        try {
            com.alibaba.csp.sentinel.Entry entry = SphU.entry(resourceName, EntryType.OUT);
            try {
                return pjp.proceed();
            } finally {
                entry.exit();
            }
        } catch (BlockException ex) {
            return handleBlockException(pjp, methodName, ex);
        }
    }

    private Object handleBlockException(ProceedingJoinPoint pjp, String methodName, BlockException ex) {
        Span currentSpan = Span.current();
        if (currentSpan != null) {
            currentSpan.setStatus(StatusCode.ERROR, "Circuit breaker degraded: " + ex.getClass().getSimpleName());
            currentSpan.setAttribute("error.type", "degraded");
            currentSpan.setAttribute("circuit.breaker.resource", mapToResourceName(methodName));
        }

        return invokeDefaultFallback(pjp, methodName);
    }

    private Object invokeDefaultFallback(ProceedingJoinPoint pjp, String methodName) {
        Object[] args = pjp.getArgs();
        Class<?> firstArgClass = args.length > 0 && args[0] != null ? args[0].getClass() : null;

        switch (methodName) {
            case "query":
                if (firstArgClass != null) {
                    return DefaultFallbacks.queryBuilder((Class<?>) firstArgClass);
                }
                return DefaultFallbacks.query(null);
            case "update":
                if (firstArgClass != null) {
                    return DefaultFallbacks.updateBuilder((Class<?>) firstArgClass);
                }
                return DefaultFallbacks.updateExecute();
            case "delete":
                if (firstArgClass != null) {
                    return DefaultFallbacks.deleteBuilder((Class<?>) firstArgClass);
                }
                return DefaultFallbacks.deleteExecute();
            case "save":
                return DefaultFallbacks.save();
            case "insert":
                return DefaultFallbacks.insert();
            case "insertAll":
                return DefaultFallbacks.insertAll();
            case "findById":
                return DefaultFallbacks.findById();
            case "findByIdOrThrow":
                return DefaultFallbacks.findById();
            case "existsById":
                return DefaultFallbacks.existsById();
            case "deleteById":
                return DefaultFallbacks.deleteById();
            case "dropCollection":
                return DefaultFallbacks.dropCollection();
            default:
                return null;
        }
    }

    private String mapToResourceName(String methodName) {
        switch (methodName) {
            case "query":
            case "update":
            case "delete":
                return "mongodb." + methodName;
            case "save":
            case "insert":
            case "insertAll":
            case "findById":
            case "findByIdOrThrow":
            case "existsById":
            case "deleteById":
            case "dropCollection":
                return "mongodb." + methodName;
            default:
                return "mongodb." + methodName;
        }
    }
}
