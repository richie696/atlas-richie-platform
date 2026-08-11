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
package cn.richie696.component.cache.redis.snowflake;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusPropertiesCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis-Plus 与雪花 ID 生成器的可选适配。
 *
 * <p>该配置必须与 {@link IdBuilderAutoConfiguration} 分离。否则不含
 * MyBatis-Plus 的 WebFlux/Gateway 应用在反射解析配置类方法时，会因为
 * {@code MybatisPlusPropertiesCustomizer} 不在运行时类路径而启动失败。</p>
 */
@AutoConfiguration(after = IdBuilderAutoConfiguration.class)
@ConditionalOnClass(MybatisPlusPropertiesCustomizer.class)
@ConditionalOnBean(IdBuilder.class)
public class MybatisPlusIdBuilderAutoConfiguration {

    /**
     * 设置雪花算法 ID 生成器为 MyBatis-Plus 全局主键生成器。
     *
     * @param idBuilder 雪花 ID 生成器
     * @return MyBatis-Plus 配置定制器
     */
    @Bean
    public MybatisPlusPropertiesCustomizer plusPropertiesCustomizer(IdBuilder idBuilder) {
        return plusProperties -> plusProperties.getGlobalConfig().setIdentifierGenerator(_ -> idBuilder.nextId());
    }
}
