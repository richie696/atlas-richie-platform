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

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * 校验标了 {@link RedisStreamConsumer} 的 Bean 必须继承 {@link AbstractStreamConsumer}。
 *
 * <p>该校验在 Bean 实例化后立即生效（{@code postProcessAfterInitialization}），
 * 失败时抛出 {@link IllegalStateException}，避免误用元注解注册普通 Bean。
 *
 * <p>实现细节：
 * <ul>
 *   <li>使用 {@link AopUtils#getTargetClass(Object)} 解析 AOP 代理后的原始类，
 *       保证 CGLIB / JDK 动态代理场景下也能正确识别继承关系与注解。</li>
 *   <li>使用 {@link AnnotatedElementUtils#findMergedAnnotation(Class, Class)} 处理
 *       元注解合并场景，与 Spring Boot 的注解处理逻辑保持一致。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
public class RedisStreamConsumerValidator implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopUtils.getTargetClass(bean);

        RedisStreamConsumer annotation = AnnotatedElementUtils.findMergedAnnotation(
                targetClass, RedisStreamConsumer.class);

        if (annotation != null && !AbstractStreamConsumer.class.isAssignableFrom(targetClass)) {
            throw new IllegalStateException(
                    "Bean '" + beanName + "' (" + targetClass.getName()
                            + ") is annotated with @RedisStreamConsumer(\"" + annotation.value()
                            + "\") but does not extend AbstractStreamConsumer. "
                            + "The @RedisStreamConsumer meta-annotation registers the class as a Spring bean "
                            + "AND requires it to be a stream consumer implementation.");
        }
        return bean;
    }
}