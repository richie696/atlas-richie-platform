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
package cn.richie696.component.redis.streammq.stream;

import cn.richie696.component.redis.streammq.support.ItStreamPayload;
import cn.richie696.contract.model.BaseStreamMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RedisStreamConsumerValidator} 单元测试。
 *
 * <p>覆盖场景：
 * <ol>
 *   <li>正确继承 {@link AbstractStreamConsumer} 的消费者类 —— 校验器应放行。</li>
 *   <li>错误标注（仅贴 {@link RedisStreamConsumer} 但不继承基类） —— 启动应快速失败。</li>
 * </ol>
 */
class RedisStreamConsumerValidatorTest {

    private final RedisStreamConsumerValidator validator = new RedisStreamConsumerValidator();

    /**
     * 正常路径：继承基类的消费者类应原样返回（校验器放行）。
     */
    @Test
    void postProcessAfterInitialization_consumerExtendingAbstractStreamConsumer_passesThrough() {
        ValidConsumer bean = new ValidConsumer();

        Object result = validator.postProcessAfterInitialization(bean, "validConsumer");

        assertThat(result).isSameAs(bean);
    }

    /**
     * 异常路径：仅贴 {@link RedisStreamConsumer} 但不继承 {@link AbstractStreamConsumer} 的类，
     * 启动时应快速失败并指出问题类名与配置名。
     */
    @Test
    void postProcessAfterInitialization_misannotatedClass_throwsIllegalStateException() {
        Misannotated bean = new Misannotated();

        assertThatThrownBy(() -> validator.postProcessAfterInitialization(bean, "misannotated"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Misannotated")
                .hasMessageContaining("does not extend AbstractStreamConsumer")
                .hasMessageContaining("@RedisStreamConsumer(\"bogus\")");
    }

    /**
     * 普通非消费者 Bean：即使被其他组件扫描命中，也不会触发校验（无对应注解）。
     */
    @Test
    void postProcessAfterInitialization_plainBeanWithoutAnnotation_passesThrough() {
        Object bean = new Object();

        assertThatCode(() -> validator.postProcessAfterInitialization(bean, "plain"))
                .doesNotThrowAnyException();
    }

    /**
     * 测试用：合法的消费者。
     */
    @RedisStreamConsumer("valid")
    static class ValidConsumer extends AbstractStreamConsumer<ItStreamPayload> {

        @Override
        protected void handle(ItStreamPayload payload, EventContext ctx) {
        }
    }

    /**
     * 测试用：误标注的非消费者类。
     */
    @RedisStreamConsumer("bogus")
    static class Misannotated {
    }
}