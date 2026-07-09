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
package com.richie.component.logging.handler;

import com.richie.component.cache.GlobalCache;
import com.richie.component.logging.domain.OperatorInfo;

/**
 * 操作人信息上下文
 *
 * @author richie696
 * @version 1.0
 * @since 2023-04-27 11:27:51
 */
public final class OperatorContextHolder {

    private static final String OPERATOR_KEY = "platform:operator:";

    /**
     * 工具类，禁止实例化。
     */
    private OperatorContextHolder() {
    }

    /**
     * 设置操作人信息的方法
     *
     * @param token HttpServletSession ID
     * @param operatorId 操作人ID
     * @param operatorName 操作人名字
     * @param expiredTime 信息过期时间（单位：毫秒）
     */
    public static void setOperator(String token, String operatorId, String operatorName, long expiredTime) {
        GlobalCache.struct().set(OPERATOR_KEY + token, new OperatorInfo(operatorId, operatorName), expiredTime);
    }

    /**
     * 检查操作人信息是否已存在
     *
     * @param token HttpServletSession ID
     * @return 返回检查结果（true：已存在，false：不存在）
     */
    public static boolean hasOperator(String token) {
        return GlobalCache.key().hasKey(OPERATOR_KEY + token);
    }

    /**
     * 获取操作人信息的方法
     *
     * @param token HttpServletSession ID
     * @return 返回操作人信息
     */
    public static OperatorInfo getOperator(String token) {
        return GlobalCache.struct().get(OPERATOR_KEY + token, OperatorInfo.class);
    }
}
