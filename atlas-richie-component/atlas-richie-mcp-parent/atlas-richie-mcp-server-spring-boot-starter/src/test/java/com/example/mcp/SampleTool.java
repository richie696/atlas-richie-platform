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
package com.example.mcp;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.McpCancellationToken;
import cn.richie696.component.mcp.api.McpProgressReporter;
import cn.richie696.component.mcp.api.annotation.McpArgument;
import cn.richie696.component.mcp.api.annotation.McpHeader;
import cn.richie696.component.mcp.api.annotation.McpTool;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 覆盖 {@link cn.richie696.component.mcp.server.spring.boot.McpAnnotatedToolRegistrar}
 * 各类注解分支的 fixture；package 必须以非 {@code cn.richie696.component.mcp} 开头以避免
 * 启动器内的扫描过滤。
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class SampleTool {
    public static final McpToolResponse RESPONSE = new McpToolResponse(List.of(),
            "marker", false);

    @McpTool(name = "asyncGreet")
    public CompletionStage<String> asyncGreet(@McpArgument(name = "name") String name) {
        return CompletableFuture.completedFuture("hello:" + name);
    }

    @McpTool(name = "monoGreet")
    public Mono<String> monoGreet(@McpArgument(name = "name") String name) {
        return Mono.just("mono:" + name);
    }

    @McpTool(name = "plainGreet")
    public String plainGreet(@McpArgument(name = "name") String name) {
        return "plain:" + name;
    }

    @McpTool(name = "responseGreet")
    public McpToolResponse responseGreet(@McpArgument(name = "name") String name) {
        return RESPONSE;
    }

    @McpTool(name = "throwGreet")
    public String throwGreet(@McpArgument(name = "name") String name) {
        throw new IllegalStateException("boom");
    }

    @McpTool(name = "contextAware")
    public String contextAware(
            @McpArgument(name = "name") String name,
            McpCallContext context,
            McpCancellationToken cancellationToken,
            McpProgressReporter progressReporter) {
        return "CALL|" + context.protocolVersion() + "|"
                + (cancellationToken.isCancellationRequested() ? "X" : "NONE")
                + "|" + (progressReporter == McpProgressReporter.NOOP ? "NOOP" : "PROG");
    }

    @McpTool(description = "unannotated")
    public String unannotatedGreet(@McpArgument(name = "name") String name) {
        return name;
    }

    @McpTool(
            name = "fullyAnnotatedGreet",
            title = "title",
            description = "desc",
            idempotent = true,
            readOnly = true,
            destructive = true,
            openWorld = true,
            enabled = true,
            audit = true,
            group = "annotated",
            timeoutMs = 5000,
            requiredScopes = {"read", "write"})
    public String fullyAnnotatedGreet(@McpArgument(name = "secret", sensitive = true) String secret) {
        return secret;
    }

    @McpTool(name = "headerGreet")
    public String headerGreet(@McpArgument(name = "name") @McpHeader("X-Auth") String name) {
        return name;
    }

    @McpTool(name = "richArgGreet")
    public String richArgGreet(
            @McpArgument(
                    name = "name",
                    description = "param desc",
                    format = "uuid",
                    example = "sample",
                    enumValues = {"a", "b", "c"},
                    minLength = 1,
                    maxLength = 16)
            String name) {
        return name;
    }

    @McpTool(name = "numericGreet")
    public int numericGreet(
            @McpArgument(name = "qty", minimum = "1", maximum = "10") int qty) {
        return qty;
    }

    @McpTool(name = "defaultIntegerGreet")
    public int defaultIntegerGreet(
            @McpArgument(name = "qty", defaultValue = "42") int qty) {
        return qty;
    }

    @McpTool(name = "defaultStringGreet")
    public String defaultStringGreet(
            @McpArgument(name = "name", defaultValue = "hello") String name) {
        return name;
    }

    @McpTool(name = "defaultBooleanGreet")
    public boolean defaultBooleanGreet(
            @McpArgument(name = "flag", defaultValue = "true") boolean flag) {
        return flag;
    }

    @McpTool(name = "optionalPrimitiveGreet")
    public int optionalPrimitiveGreet(
            @McpArgument(name = "qty", required = false, defaultValue = "5") int qty) {
        return qty;
    }

    @McpTool(name = "voidGreet")
    public void voidGreet(@McpArgument(name = "name") String name) {
    }

    @McpTool(name = "requiredPrimitiveGreet")
    public int requiredPrimitiveGreet(@McpArgument(name = "qty") int qty) {
        return qty;
    }
}
