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
package com.richie.testing.env;

/**
 * 跨组件集测策略：Docker 强制、外部服务 opt-in。
 */
public final class IntegrationTestPolicy {

    private IntegrationTestPolicy() {
    }

    /** CI 推荐：无 Docker 时失败，不静默跳过。 */
    public static boolean requireDocker(String... componentPrefixes) {
        if (TestEnv.isTruthy("IT_REQUIRE_DOCKER", "it.require.docker")) {
            return true;
        }
        for (String prefix : componentPrefixes) {
            if (TestEnv.isTruthy(prefix + "_IT_REQUIRE_DOCKER", toPropertyKey(prefix) + ".it.require.docker")) {
                return true;
            }
        }
        return false;
    }

    /** 本机已有中间件时跳过 Testcontainers（勿用于 CI）。 */
    public static boolean useExternal(String... componentPrefixes) {
        if (TestEnv.isTruthy("IT_USE_EXTERNAL", "it.use.external")) {
            return true;
        }
        for (String prefix : componentPrefixes) {
            if (TestEnv.isTruthy(prefix + "_IT_USE_EXTERNAL", toPropertyKey(prefix) + ".it.use.external")) {
                return true;
            }
        }
        return false;
    }

    private static String toPropertyKey(String prefix) {
        return prefix.toLowerCase().replace('_', '.');
    }
}
