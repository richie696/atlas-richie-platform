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
package com.richie.component.nats.support;

/**
 * 集成测试公共 POJO 载荷。
 *
 * <p>使用 record 而非裸 String 字符串的原因：{@code JacksonNatsMessageSerializer} 对 String 序列化时会
 * 输出带 JSON 引号的字节（{@code "hello"}），而测试断言如果写 {@code isEqualTo("hello")} 会失败。
 * 改用 POJO 载荷后，序列化为 {@code {"content":"hello"}}，无歧义且能验证完整的序列化/反序列化路径。</p>
 *
 * @author richie696
 */
public record TestEvent(String content) {
}
