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
package cn.richie696.component.tenant.reactive;

import cn.richie696.contract.model.TenantPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TenantContextKeys 单元测试 — 验证常量值与 utility class 不可外部实例化。
 */
@DisplayName("TenantContextKeys — Reactor Context Key 常量")
class TenantContextKeysTest {

    @Test
    @DisplayName("TENANT_KEY 等于 TenantPrincipal.class")
    void tenantKeyEqualsTenantPrincipalClass() {
        assertThat(TenantContextKeys.TENANT_KEY).isEqualTo(TenantPrincipal.class);
    }

    @Test
    @DisplayName("private 构造器可通过反射实例化（覆盖 private 无参构造函数）")
    void privateConstructorAccessibleViaReflection() throws Exception {
        Constructor<TenantContextKeys> ctor = TenantContextKeys.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        // 调用反映构造器不会抛 — 只是覆盖 Utility class 不可外部直接 new 的事实
        assertThatCode(ctor::newInstance).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("private 构造器修饰符为 private")
    void privateConstructorModifier() throws Exception {
        Constructor<TenantContextKeys> ctor = TenantContextKeys.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(ctor.getModifiers())).isTrue();
    }
}
