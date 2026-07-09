/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mongodb.circuitbreaker;

import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;


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

        return switch (methodName) {
            case "query" -> {
                if (firstArgClass != null) {
                    yield DefaultFallbacks.queryBuilder((Class<?>) firstArgClass);
                }
                yield DefaultFallbacks.query(null);
            }
            case "update" -> {
                if (firstArgClass != null) {
                    yield DefaultFallbacks.updateBuilder((Class<?>) firstArgClass);
                }
                yield DefaultFallbacks.updateExecute();
            }
            case "delete" -> {
                if (firstArgClass != null) {
                    yield DefaultFallbacks.deleteBuilder((Class<?>) firstArgClass);
                }
                yield DefaultFallbacks.deleteExecute();
            }
            case "save" -> DefaultFallbacks.save();
            case "insert" -> DefaultFallbacks.insert();
            case "insertAll" -> DefaultFallbacks.insertAll();
            case "findById", "findByIdOrThrow" -> DefaultFallbacks.findById();
            case "existsById" -> DefaultFallbacks.existsById();
            case "deleteById" -> DefaultFallbacks.deleteById();
            case "dropCollection" -> DefaultFallbacks.dropCollection();
            default -> null;
        };
    }

    private String mapToResourceName(String methodName) {
        return switch (methodName) {
            case "query", "update", "delete" -> "mongodb." + methodName;
            case "save", "insert", "insertAll", "findById", "findByIdOrThrow", "existsById", "deleteById",
                 "dropCollection" -> "mongodb." + methodName;
            default -> "mongodb." + methodName;
        };
    }
}
