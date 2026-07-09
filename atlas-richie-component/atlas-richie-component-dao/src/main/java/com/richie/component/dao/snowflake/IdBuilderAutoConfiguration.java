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
package com.richie.component.dao.snowflake;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;

/**
 * 雪花 ID 生成器自动配置：通过 Redis Lua 脚本分配 workerId，并注册 IdBuilder Bean。
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-17
 */
@Slf4j
@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
//@ConditionalOnBean(MybatisPlusTenantAutoConfiguration.class)
public class IdBuilderAutoConfiguration {

    /** Redis 模板，用于执行 Lua 获取 workerId */
    private final StringRedisTemplate redisTemplate;

    private static final String WORK_ID_KEY = "snowflake:workId";

    private static final long MAX_MACHINE_CODE = 1024L;

    /**
     * 我们能够使用redis自增来进行机器码赋值处理，此外，我们还需要限制自增数量。故需要lua脚本保证原子性。
     * redis无法感知机器下线也无法回收下线机器id，只能在给定机器码范围内轮询发号
     * <p>
     * lua脚本：
     * -- 定义一个 Redis key，用于存储当前分发的机器码
     * local machineCodeKey = "snowflake:workId:service"
     * -- 定义最大的机器码数（假设为 1024）
     * local maxMachineCode = 1024
     * -- 尝试获取当前分发的机器码
     * local machineCode = tonumber(redis.call('GET', machineCodeKey))
     * -- 如果当前机器码不存在或者超过最大值，回到0重新分发
     * if not machineCode or machineCode >=  maxMachineCode
     * then machineCode = 0
     * end
     * -- 增加当前机器码，用于下次分发
     * redis.call("SET", machineCodeKey, machineCode + 1)
     * -- 返回当前分发的机器码
     * return machineCode
     *
     * @return IdBuilder 雪花 ID 生成器实例
     */
    @Bean
    public IdBuilder idBuilder() {
        String luaScript =
                " local machineCode = tonumber(redis.call('GET', KEYS[1])) " +
                " if not machineCode or machineCode >= "+ MAX_MACHINE_CODE +
                " then machineCode = 0 " +
                " end;  " +
                " redis.call('SET', KEYS[1], machineCode + 1); " +
                " return machineCode ";
        Long workerId = redisTemplate.execute(new DefaultRedisScript<>(luaScript, Long.class), Collections.singletonList(WORK_ID_KEY));
        if (null == workerId) {
            log.error("get workerId from redis error,random generate one");
            workerId = RandomUtils.nextLong(0, MAX_MACHINE_CODE - 1);
        }
        log.info("workerId:{}", workerId);
        return new IdBuilder(workerId);
    }

}
