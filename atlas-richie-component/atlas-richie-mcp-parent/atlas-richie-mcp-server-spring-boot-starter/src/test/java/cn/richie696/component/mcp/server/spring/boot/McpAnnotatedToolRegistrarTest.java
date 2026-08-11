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
package cn.richie696.component.mcp.server.spring.boot;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpArgumentBinder;
import cn.richie696.component.mcp.api.server.McpToolHandler;
import cn.richie696.component.mcp.api.server.McpToolHandlerProvider;
import cn.richie696.component.mcp.schema.JacksonMcpTypeSchemaGenerator;
import cn.richie696.component.mcp.schema.McpTypeSchemaGenerator;
import cn.richie696.component.mcp.server.tool.McpToolRegistration;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;
import com.example.mcp.GreeterBean;
import com.example.mcp.InventoryToolFixture;
import com.example.mcp.InvalidBooleanDefaultTool;
import com.example.mcp.InvalidNumericTool;
import com.example.mcp.MissingArgumentTool;
import com.example.mcp.SampleTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 验证 {@link McpAnnotatedToolRegistrar} 的全流程：构造校验、扫描、过滤、缓存失效、
 * Handler 调用、Schema 增强、参数校验异常。所有 fixture 位于 {@code com.example.mcp}
 * 包，避免 {@code cn.richie696.component.mcp.*} 框架自包过滤。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpAnnotatedToolRegistrar 注解扫描")
class McpAnnotatedToolRegistrarTest {

    @Nested
    @DisplayName("构造校验")
    class Construction {

        @Test
        @DisplayName("任一参数为 null 都抛 NullPointerException")
        void nullArgsRejected() {
            McpArgumentBinder binder = new JacksonMcpArgumentBinder(realMapper());
            McpTypeSchemaGenerator generator = new JacksonMcpTypeSchemaGenerator();
            ApplicationContext context = new AnnotationConfigApplicationContext();

            assertThatThrownBy(() -> new McpAnnotatedToolRegistrar(null, binder, generator,
                    new McpServerProperties.Tools()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("applicationContext");
            assertThatThrownBy(() -> new McpAnnotatedToolRegistrar(context, null, generator,
                    new McpServerProperties.Tools()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("argumentBinder");
            assertThatThrownBy(() -> new McpAnnotatedToolRegistrar(context, binder, null,
                    new McpServerProperties.Tools()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("schemaGenerator");
            assertThatThrownBy(() -> new McpAnnotatedToolRegistrar(context, binder, generator, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("properties");
        }

        @Test
        @DisplayName("单参快捷构造使用默认 Jackson binder + schema 生成器")
        void singleArgConstructorUsesDefaults() {
            ApplicationContext context = new AnnotationConfigApplicationContext();
            McpAnnotatedToolRegistrar registrar = new McpAnnotatedToolRegistrar(context);
            assertThat(registrar).isNotNull();
            assertThat(registrar.registrations()).isEmpty();
        }
    }

    @Nested
    @DisplayName("扫描与过滤")
    class ScanAndFilter {

        @Test
        @DisplayName("扫描 com.example.mcp 包内带 @McpTool 的 Bean")
        void scansAnnotatedBean() {
            try (AnnotationConfigApplicationContext context = newContext(GreeterConfig.class)) {
                McpServerProperties properties = new McpServerProperties();
                properties.getTools().setScanPackages(List.of("com.example.mcp"));
                McpAnnotatedToolRegistrar registrar = newRegistrar(context, properties);

                List<McpToolRegistration> registrations = registrar.registrations();

                assertThat(registrations).hasSize(1);
                assertThat(registrations.getFirst().descriptor().name()).isEqualTo("greet");
            }
        }

        @Test
        @DisplayName("scanPackages 白名单生效")
        void scanPackagesWhitelist() {
            try (AnnotationConfigApplicationContext context = newContext(
                    GreeterConfig.class, InventoryConfig.class)) {
                McpServerProperties properties = new McpServerProperties();
                properties.getTools().setScanPackages(List.of("com.example.mcp"));
                properties.getTools().setBeanNames(Set.of("inventoryTool", "greetBean"));
                McpAnnotatedToolRegistrar registrar = newRegistrar(context, properties);

                assertThat(registrar.registrations())
                        .extracting(r -> r.descriptor().name())
                        .containsExactlyInAnyOrder("greet", "inventory.query");
            }
        }

        @Test
        @DisplayName("excludePackages 黑名单生效")
        void excludePackagesBlacklist() {
            try (AnnotationConfigApplicationContext context = newContext(
                    GreeterConfig.class, InventoryConfig.class)) {
                McpServerProperties properties = new McpServerProperties();
                properties.getTools().setScanPackages(List.of("com.example.mcp"));
                properties.getTools().setBeanNames(Set.of("inventoryTool", "greetBean"));
                properties.getTools().setExcludePackages(List.of("com.example.mcp"));
                McpAnnotatedToolRegistrar registrar = newRegistrar(context, properties);

                assertThat(registrar.registrations())
                        .extracting(r -> r.descriptor().name())
                        .isEmpty();
            }
        }

        @Test
        @DisplayName("beanNames 白名单生效")
        void beanNamesWhitelist() {
            try (AnnotationConfigApplicationContext context = newContext(
                    GreeterConfig.class, InventoryConfig.class)) {
                McpServerProperties properties = new McpServerProperties();
                properties.getTools().setScanPackages(List.of("com.example.mcp"));
                properties.getTools().setBeanNames(Set.of("greetBean"));
                McpAnnotatedToolRegistrar registrar = newRegistrar(context, properties);

                assertThat(registrar.registrations())
                        .extracting(r -> r.descriptor().name())
                        .containsExactly("greet");
            }
        }

        @Test
        @DisplayName("excludeBeanNames 黑名单生效")
        void excludeBeanNamesBlacklist() {
            try (AnnotationConfigApplicationContext context = newContext(
                    GreeterConfig.class, InventoryConfig.class)) {
                McpServerProperties properties = new McpServerProperties();
                properties.getTools().setScanPackages(List.of("com.example.mcp"));
                properties.getTools().setBeanNames(Set.of("greetBean", "inventoryTool"));
                properties.getTools().setExcludeBeanNames(Set.of("inventoryTool"));
                McpAnnotatedToolRegistrar registrar = newRegistrar(context, properties);

                assertThat(registrar.registrations())
                        .extracting(r -> r.descriptor().name())
                        .containsExactly("greet");
            }
        }

        @Test
        @DisplayName("过滤元 Bean：McpToolRegistry / McpToolHandlerProvider 都被跳过")
        void metaBeansSkipped() {
            try (AnnotationConfigApplicationContext context = newContext(MetaBeansConfig.class)) {
                McpServerProperties properties = new McpServerProperties();
                properties.getTools().setScanPackages(List.of("com.example.mcp"));
                McpAnnotatedToolRegistrar registrar = newRegistrar(context, properties);

                assertThat(registrar.registrations())
                        .extracting(r -> r.descriptor().name())
                        .isEmpty();
            }
        }

        @Test
        @DisplayName("registrations() 多次调用返回同一稳定列表（identity 相等）")
        void cachedListIsStable() {
            try (AnnotationConfigApplicationContext context = newContext(GreeterConfig.class)) {
                McpServerProperties properties = new McpServerProperties();
                properties.getTools().setScanPackages(List.of("com.example.mcp"));
                McpAnnotatedToolRegistrar registrar = newRegistrar(context, properties);

                List<McpToolRegistration> first = registrar.registrations();
                List<McpToolRegistration> second = registrar.registrations();

                assertThat(first).isSameAs(second);
            }
        }

        @Test
        @DisplayName("不支持 ConfigurableApplicationContext 时仍然走 getType 回退路径完成扫描")
        void nonConfigurableContextScan() {
            ApplicationContext context = mock(ApplicationContext.class);
            given(context.getBeanDefinitionNames()).willReturn(new String[]{"greetBean"});
            given(context.getType("greetBean")).willReturn((Class) GreeterBean.class);
            given(context.getBean("greetBean")).willReturn(new GreeterBean());

            McpServerProperties properties = new McpServerProperties();
            properties.getTools().setScanPackages(List.of("com.example.mcp"));
            McpAnnotatedToolRegistrar registrar = new McpAnnotatedToolRegistrar(
                    context, new JacksonMcpArgumentBinder(realMapper()),
                    new JacksonMcpTypeSchemaGenerator(), properties.getTools());

            assertThat(registrar.registrations())
                    .extracting(r -> r.descriptor().name())
                    .containsExactly("greet");
        }
    }

    @Nested
    @DisplayName("updateProperties 缓存失效")
    class UpdateProperties {

        @Test
        @DisplayName("scan 签名未变时缓存保持")
        void unchangedSignatureKeepsCache() {
            try (AnnotationConfigApplicationContext context = newContext(GreeterConfig.class)) {
                McpServerProperties properties = new McpServerProperties();
                properties.getTools().setScanPackages(List.of("com.example.mcp"));
                McpAnnotatedToolRegistrar registrar = newRegistrar(context, properties);
                List<McpToolRegistration> first = registrar.registrations();

                McpServerProperties.Tools sameSignature = new McpServerProperties.Tools();
                sameSignature.setScanPackages(List.of("com.example.mcp"));
                registrar.updateProperties(sameSignature);
                List<McpToolRegistration> second = registrar.registrations();

                assertThat(first).isSameAs(second);
            }
        }

        @Test
        @DisplayName("scan 包变更时缓存失效，结果集随之变化")
        void packageChangeInvalidatesCache() {
            try (AnnotationConfigApplicationContext context = newContext(
                    GreeterConfig.class, InventoryConfig.class)) {
                McpServerProperties properties = new McpServerProperties();
                properties.getTools().setScanPackages(List.of("com.example.mcp"));
                properties.getTools().setBeanNames(Set.of("inventoryTool", "greetBean"));
                McpAnnotatedToolRegistrar registrar = newRegistrar(context, properties);
                List<McpToolRegistration> first = registrar.registrations();

                McpServerProperties.Tools updated = new McpServerProperties.Tools();
                updated.setScanPackages(List.of("com.example.mcp"));
                updated.setBeanNames(Set.of("inventoryTool"));
                registrar.updateProperties(updated);
                List<McpToolRegistration> second = registrar.registrations();

                assertThat(first).isNotSameAs(second);
                assertThat(second)
                        .extracting(r -> r.descriptor().name())
                        .containsExactly("inventory.query");
            }
        }

        @Test
        @DisplayName("updateProperties(null) 抛 NullPointerException")
        void nullPropertiesRejected() {
            ApplicationContext context = new AnnotationConfigApplicationContext();
            McpAnnotatedToolRegistrar registrar = new McpAnnotatedToolRegistrar(context);
            assertThatThrownBy(() -> registrar.updateProperties(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("registerInto")
    class RegisterInto {

        @Test
        @DisplayName("registerInto 把扫描结果批量灌入 McpToolRegistry")
        void delegatesToRegistry() {
            try (AnnotationConfigApplicationContext context = newContext(GreeterConfig.class)) {
                McpServerProperties properties = new McpServerProperties();
                properties.getTools().setScanPackages(List.of("com.example.mcp"));
                McpAnnotatedToolRegistrar registrar = newRegistrar(context, properties);
                McpToolRegistry registry = new McpToolRegistry();

                registrar.registerInto(registry);

                assertThat(registry.snapshot(callContext()).tools())
                        .extracting(McpToolDescriptor::name)
                        .containsExactly("greet");
            }
        }
    }

    @Nested
    @DisplayName("Handler 调用分支")
    class HandlerInvocation {

        @Test
        @DisplayName("CompletionStage 返回值通过 thenApply(response)")
        void completionStageReturn() {
            withRegistration("asyncGreet", registration -> {
                McpToolResponse response = registration.handler()
                        .handle(Map.of("name", "world"), callContext())
                        .toCompletableFuture().join();
                assertThat(response.structuredContent()).isEqualTo("hello:world");
            });
        }

        @Test
        @DisplayName("Mono 返回值通过 toFuture().thenApply(response)")
        void monoReturn() {
            withRegistration("monoGreet", registration -> {
                McpToolResponse response = registration.handler()
                        .handle(Map.of("name", "world"), callContext())
                        .toCompletableFuture().join();
                assertThat(response.structuredContent()).isEqualTo("mono:world");
            });
        }

        @Test
        @DisplayName("裸值返回被包装为 McpToolResponse")
        void plainValueReturn() {
            withRegistration("plainGreet", registration -> {
                McpToolResponse response = registration.handler()
                        .handle(Map.of("name", "world"), callContext())
                        .toCompletableFuture().join();
                assertThat(response.structuredContent()).isEqualTo("plain:world");
            });
        }

        @Test
        @DisplayName("McpToolResponse 返回值直接透传")
        void toolResponseReturn() {
            withRegistration("responseGreet", registration -> {
                McpToolResponse response = registration.handler()
                        .handle(Map.of("name", "world"), callContext())
                        .toCompletableFuture().join();
                assertThat(response).isSameAs(SampleTool.RESPONSE);
            });
        }

        @Test
        @DisplayName("InvocationTargetException 解包为 failedFuture(cause)")
        void invocationTargetExceptionUnwrapped() {
            withRegistration("throwGreet", registration -> {
                assertThatThrownBy(() -> registration.handler()
                        .handle(Map.of("name", "world"), callContext())
                        .toCompletableFuture().join())
                        .hasCauseInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("boom");
            });
        }

        @Test
        @DisplayName("McpCallContext / McpCancellationToken / McpProgressReporter 透传到方法参数")
        void contextParametersPassedThrough() {
            withRegistration("contextAware", registration -> {
                McpCallContext ctx = callContext();
                McpToolResponse response = registration.handler()
                        .handle(Map.of("name", "world"), ctx)
                        .toCompletableFuture().join();
                assertThat(response.structuredContent())
                        .isEqualTo("CALL|" + ctx.protocolVersion() + "|NONE|NOOP");
            });
        }

        private void withRegistration(String name, java.util.function.Consumer<McpToolRegistration> consumer) {
            try (AnnotationConfigApplicationContext context = newContext(SampleToolConfig.class)) {
                McpServerProperties properties = new McpServerProperties();
                properties.getTools().setScanPackages(List.of("com.example.mcp"));
                McpAnnotatedToolRegistrar registrar = newRegistrar(context, properties);
                McpToolRegistration registration = registrar.registrations().stream()
                        .filter(r -> r.descriptor().name().equals(name))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("missing registration: " + name));
                consumer.accept(registration);
            }
        }
    }

    @Nested
    @DisplayName("Descriptor / Schema 增强")
    class DescriptorAndSchema {

        @Test
        @DisplayName("@McpTool 未指定 name 时回退到方法名")
        void defaultNameUsesMethodName() {
            withRegistration("unannotatedGreet", registration ->
                    assertThat(registration.descriptor().name()).isEqualTo("unannotatedGreet"));
        }

        @Test
        @DisplayName("@McpTool 未指定 title/description 时为 null")
        void defaultTitleDescriptionNull() {
            withRegistration("plainGreet", registration -> {
                assertThat(registration.descriptor().title()).isNull();
                assertThat(registration.descriptor().description()).isNull();
            });
        }

        @Test
        @DisplayName("@McpTool 全部字段被写入 annotations")
        void allAnnotationsApplied() {
            withRegistration("fullyAnnotatedGreet", registration -> {
                Map<String, Object> annotations = registration.descriptor().annotations();
                assertThat(annotations)
                        .containsEntry("idempotent", true)
                        .containsEntry("readOnly", true)
                        .containsEntry("destructive", true)
                        .containsEntry("openWorld", true)
                        .containsEntry("enabled", true)
                        .containsEntry("audit", true)
                        .containsEntry("group", "annotated")
                        .containsEntry("timeoutMs", 5000L)
                        .containsEntry("requiredScopes", List.of("read", "write"))
                        .containsEntry("sensitiveArguments", List.of("secret"));
            });
        }

        @Test
        @DisplayName("@McpHeader 在 schema 中写入 x-mcp-header")
        void headerAttributeInSchema() {
            withRegistration("headerGreet", registration -> {
                Map<String, Object> properties = (Map<String, Object>)
                        registration.descriptor().inputSchema().get("properties");
                assertThat(properties.get("name")).extracting("x-mcp-header").isEqualTo("X-Auth");
            });
        }

        @Test
        @DisplayName("@McpArgument 描述/format/example/enum/minLength/maxLength 全部写入 schema")
        void argumentEnrichments() {
            withRegistration("richArgGreet", registration -> {
                Map<String, Object> argSchema = (Map<String, Object>)
                        ((Map<String, Object>) registration.descriptor().inputSchema().get("properties")).get("name");
                assertThat(argSchema)
                        .containsEntry("description", "param desc")
                        .containsEntry("format", "uuid")
                        .containsEntry("minLength", 1)
                        .containsEntry("maxLength", 16)
                        .containsEntry("enum", List.of("a", "b", "c"));
                assertThat(argSchema.get("examples")).asList().containsExactly("sample");
            });
        }

        @Test
        @DisplayName("@McpArgument(minimum/maximum) 写出 number 类型")
        void numericArgumentEnrichments() {
            withRegistration("numericGreet", registration -> {
                Map<String, Object> argSchema = (Map<String, Object>)
                        ((Map<String, Object>) registration.descriptor().inputSchema().get("properties")).get("qty");
                assertThat(argSchema)
                        .containsEntry("minimum", new java.math.BigDecimal("1"))
                        .containsEntry("maximum", new java.math.BigDecimal("10"));
            });
        }

        @Test
        @DisplayName("@McpArgument(minimum/maximum) 格式非法抛 IllegalArgumentException")
        void invalidNumericRangeThrows() {
            try (AnnotationConfigApplicationContext context = newContext(InvalidNumericConfig.class)) {
                McpServerProperties properties = new McpServerProperties();
                properties.getTools().setScanPackages(List.of("com.example.mcp"));
                McpAnnotatedToolRegistrar registrar = newRegistrar(context, properties);
                assertThatThrownBy(registrar::registrations)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("minimum");
            }
        }

        @Test
        @DisplayName("@McpArgument(defaultValue) for integer 类型包装为 BigDecimal")
        void defaultValueIntegerRendersAsBigDecimal() {
            withRegistration("defaultIntegerGreet", registration -> {
                Map<String, Object> argSchema = (Map<String, Object>)
                        ((Map<String, Object>) registration.descriptor().inputSchema().get("properties")).get("qty");
                assertThat(argSchema.get("default"))
                        .isInstanceOf(java.math.BigDecimal.class)
                        .isEqualTo(new java.math.BigDecimal("42"));
            });
        }

        @Test
        @DisplayName("@McpArgument(defaultValue) for String 类型直接保留字符串")
        void defaultValueStringPassThrough() {
            withRegistration("defaultStringGreet", registration -> {
                Map<String, Object> argSchema = (Map<String, Object>)
                        ((Map<String, Object>) registration.descriptor().inputSchema().get("properties")).get("name");
                assertThat(argSchema.get("default")).isEqualTo("hello");
            });
        }

        @Test
        @DisplayName("@McpArgument(defaultValue) for boolean 类型接受 true/false（大小写不敏感）")
        void defaultValueBoolean() {
            withRegistration("defaultBooleanGreet", registration -> {
                Map<String, Object> argSchema = (Map<String, Object>)
                        ((Map<String, Object>) registration.descriptor().inputSchema().get("properties")).get("flag");
                assertThat(argSchema.get("default")).isEqualTo(Boolean.TRUE);
            });
        }

        @Test
        @DisplayName("@McpArgument(defaultValue) for boolean 类型非法值抛 IllegalArgumentException")
        void defaultValueBooleanInvalidThrows() {
            try (AnnotationConfigApplicationContext context = newContext(InvalidBooleanDefaultConfig.class)) {
                McpServerProperties properties = new McpServerProperties();
                properties.getTools().setScanPackages(List.of("com.example.mcp"));
                McpAnnotatedToolRegistrar registrar = newRegistrar(context, properties);
                assertThatThrownBy(registrar::registrations)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("defaultValue");
            }
        }

        @Test
        @DisplayName("required=false + defaultValue 存在时 primitive 不进入 required 列表")
        void primitiveWithDefaultIsOptional() {
            withRegistration("optionalPrimitiveGreet", registration -> {
                assertThat(registration.descriptor().inputSchema()).doesNotContainKey("required");
            });
        }

        @Test
        @DisplayName("参数没有 @McpArgument 注解时抛 IllegalArgumentException")
        void missingMcpargumentThrows() {
            try (AnnotationConfigApplicationContext context = newContext(MissingArgumentConfig.class)) {
                McpServerProperties properties = new McpServerProperties();
                properties.getTools().setScanPackages(List.of("com.example.mcp"));
                McpAnnotatedToolRegistrar registrar = newRegistrar(context, properties);
                assertThatThrownBy(registrar::registrations)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("@McpArgument");
            }
        }

        @Test
        @DisplayName("返回类型为 CompletionStage<T> 时输出 T 的 schema")
        void outputSchemaExtractedFromCompletionStage() {
            withRegistration("asyncGreet", registration ->
                    assertThat(registration.descriptor().outputSchema()).containsKey("type"));
        }

        @Test
        @DisplayName("返回类型为 void 时输出空 schema")
        void emptyOutputSchema() {
            withRegistration("voidGreet", registration ->
                    assertThat(registration.descriptor().outputSchema()).isEmpty());
        }

        @Test
        @DisplayName("primitive 参数无 default 时进入 required 列表")
        void primitiveWithoutDefaultIsRequired() {
            withRegistration("requiredPrimitiveGreet", registration ->
                    assertThat((List<String>) registration.descriptor().inputSchema().get("required"))
                            .containsExactly("qty"));
        }

        private void withRegistration(String name, java.util.function.Consumer<McpToolRegistration> consumer) {
            try (AnnotationConfigApplicationContext context = newContext(SampleToolConfig.class)) {
                McpServerProperties properties = new McpServerProperties();
                properties.getTools().setScanPackages(List.of("com.example.mcp"));
                McpAnnotatedToolRegistrar registrar = newRegistrar(context, properties);
                McpToolRegistration registration = registrar.registrations().stream()
                        .filter(r -> r.descriptor().name().equals(name))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("missing registration: " + name));
                consumer.accept(registration);
            }
        }
    }

    private static McpAnnotatedToolRegistrar newRegistrar(ApplicationContext context,
                                                          McpServerProperties properties) {
        return new McpAnnotatedToolRegistrar(
                context,
                new JacksonMcpArgumentBinder(realMapper()),
                new JacksonMcpTypeSchemaGenerator(),
                properties.getTools());
    }

    private static AnnotationConfigApplicationContext newContext(Class<?>... configClasses) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(configClasses);
        context.refresh();
        return context;
    }

    private static tools.jackson.databind.ObjectMapper realMapper() {
        return tools.jackson.databind.json.JsonMapper.builder().build();
    }

    private static McpCallContext callContext() {
        return new McpCallContext(
                "request-1",
                cn.richie696.component.mcp.protocol.McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                "subject-1",
                null,
                Map.of(),
                null,
                null);
    }

    @Configuration
    static class GreeterConfig {
        @Bean(name = "greetBean")
        public GreeterBean greeterBean() {
            return new GreeterBean();
        }
    }

    @Configuration
    static class InventoryConfig {
        @Bean(name = "inventoryTool")
        public InventoryToolFixture inventoryTool() {
            return new InventoryToolFixture();
        }
    }

    @Configuration
    static class SampleToolConfig {
        @Bean
        public SampleTool sampleTool() {
            return new SampleTool();
        }
    }

    @Configuration
    static class MissingArgumentConfig {
        @Bean
        public MissingArgumentTool missingArgumentTool() {
            return new MissingArgumentTool();
        }
    }

    @Configuration
    static class InvalidNumericConfig {
        @Bean
        public InvalidNumericTool invalidNumericTool() {
            return new InvalidNumericTool();
        }
    }

    @Configuration
    static class InvalidBooleanDefaultConfig {
        @Bean
        public InvalidBooleanDefaultTool invalidBooleanDefaultTool() {
            return new InvalidBooleanDefaultTool();
        }
    }

    @Configuration
    static class MetaBeansConfig {
        @Bean
        public McpToolRegistry metaRegistry() {
            return new McpToolRegistry();
        }

        @Bean
        public TestHandlerProvider metaProvider() {
            return new TestHandlerProvider();
        }
    }

    public static final class TestHandlerProvider implements McpToolHandlerProvider {
        @Override
        public String handlerRef() {
            return "test-handler";
        }

        @Override
        public McpToolHandler handler() {
            return (arguments, context) -> CompletableFuture.completedFuture(
                    new McpToolResponse(List.of(), "ok", false));
        }
    }
}
