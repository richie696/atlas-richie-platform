/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.cache.ops;

import java.util.List;

/**
 * 脚本原子操作API管理器接口，封装了脚本的执行能力。
 * <p>
 * 适用于复杂原子操作、分布式事务、批量处理等场景。
 * <ul>
 *   <li>script：执行脚本，支持传递key和参数，返回指定类型结果</li>
 * </ul>
 * <p>
 * 推荐用于高并发下的原子性保障、复杂业务逻辑下沉缓存中间件等场景（Redis -> Lua、其它：待更新）
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-25 17:49:54
 */
public interface ScriptOps {

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
    <T> T eval(String script, List<String> keys, List<String> args, Class<T> resultType);

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
    <T> T eval(String script, String key, List<String> args, Class<T> resultType);
}
