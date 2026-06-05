package com.richie.component.cache.redis.manage;

import com.richie.component.cache.ops.ScriptOps;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.perf.RedisOperationCatalog;
import com.richie.component.cache.redis.perf.RedisPerfGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

/**
 * Lua脚本原子操作API管理器，封装了Redis中Lua脚本的执行能力。
 * <p>
 * 适用于复杂原子操作、分布式事务、批量处理等场景。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-25 17:49:54
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'=='REDIS'")
public class RedisScriptManager implements ScriptOps {

    /** Redis 模板（JSON 序列化） */
    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    /** Redis 性能守卫（可选启用） */
    private final RedisPerfGuard redisPerfGuard;

    /**
     * 执行Lua脚本。
     *
     * @param script     Lua脚本内容
     * @param keys       参与脚本的Redis键列表
     * @param args       脚本参数列表
     * @param resultType 返回结果类型
     * @param <T>        返回值泛型
     * @return 脚本执行结果
     */
    @Override
    public <T> T eval(String script, List<String> keys, List<String> args, Class<T> resultType) {
        return redisPerfGuard.<T>execute("RedisLuaManager", "evalLua", RedisOperationCatalog.LUA_EVAL, () -> {
            byte[][] keysAndArgs = Stream.concat(
                    keys.stream(),
                    args.stream()
            ).map(String::getBytes).toArray(byte[][]::new);
            return redisTemplate.execute((RedisCallback<T>) conn ->
                    conn.scriptingCommands().eval(script.getBytes(), ReturnType.fromJavaType(resultType), keys.size(), keysAndArgs)
            );
        });
    }

    /**
     * 执行Lua脚本。
     *
     * @param script     Lua脚本内容
     * @param key        参与脚本的Redis键
     * @param args       脚本参数列表
     * @param resultType 返回结果类型
     * @param <T>        返回值泛型
     * @return 脚本执行结果
     */
    @Override
    public <T> T eval(String script, String key, List<String> args, Class<T> resultType) {
        return eval(script, List.of(key), args, resultType);
    }
}
