package com.richie.component.cache.function;

import java.util.List;

/**
 * Lua脚本原子操作API管理器接口，封装了Redis中Lua脚本的执行能力。
 * <p>
 * 适用于复杂原子操作、分布式事务、批量处理等场景。
 * <ul>
 *   <li>evalLua：执行Lua脚本，支持传递key和参数，返回指定类型结果</li>
 * </ul>
 * <p>
 * 推荐用于高并发下的原子性保障、复杂业务逻辑下沉Redis等场景。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-25 17:49:54
 */
public interface LuaFunction extends CacheFunction {
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
    <T> T evalLua(String script, List<String> keys, List<String> args, Class<T> resultType);
}
