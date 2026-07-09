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
 * 集成测试环境变量 / 系统属性读取工具。
 */
public final class TestEnv {

    private TestEnv() {
    }

    public static boolean isTruthy(String envKey, String propertyKey) {
        String raw = firstNonBlank(System.getenv(envKey), System.getProperty(propertyKey));
        return raw != null && (raw.equalsIgnoreCase("true") || raw.equals("1") || raw.equalsIgnoreCase("yes"));
    }

    public static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    /** 按 env 变量名、系统属性名顺序解析第一个非空值，最后回退 defaults。 */
    public static String firstResolved(String[] envKeys, String[] propertyKeys, String... defaults) {
        for (String envKey : envKeys) {
            String value = System.getenv(envKey);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        for (String propertyKey : propertyKeys) {
            String value = System.getProperty(propertyKey);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return firstNonBlank(defaults);
    }
}
