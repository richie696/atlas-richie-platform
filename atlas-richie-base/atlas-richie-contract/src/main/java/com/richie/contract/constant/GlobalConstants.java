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
package com.richie.contract.constant;

/**
 * 全局常量
 *
 * @author richie696
 * @version 1.0
 * @since 2025-05-16 15:51:42
 */
public interface GlobalConstants {

    /**
     * 框架版本号
     */
    int FRAMEWORK_VERSION = 100;

    /**
     * 时间格式化模式
     */
    String X_TIME_FORMAT_PATTERN = "x-rd-request-time-format-pattern";

    /**
     * 货币格式化模式
     */
    String X_CURRENCY_FORMAT_PATTERN = "x-rd-request-currency-format-pattern";

    /**
     * 请求头中的原始uri
     */
    String X_REQUEST_ORIGIN_URI= "x-rd-request-origin-uri";

    /**
     * 请求头中的token
     */
    String X_ACCESS_TOKEN = "x-rd-request-apitoken";

    /**
     * 请求头中的 MFA 临时令牌（登出或 MFA 验证时传递）
     */
    String X_MFA_TOKEN = "x-rd-request-mfa-token";

    /**
     * 租户 ID header
     */
    String X_TENANT_ID = "x-rd-request-tenantid";

    /**
     * 店铺header
     */
    String X_RD_REQUEST_SHOP_CODE = "x-rd-request-shopcode";

    /**
     * 语言
     */
    String X_RD_REQUEST_LANGUAGE = "x-rd-request-language";

    /**
     * 时区
     */
    String X_RD_REQUEST_TIMEZONE = "x-rd-request-timezone";

    /**
     * 请求头中的额外信息
     */
    String X_RD_REQUEST_EXTRA = "x-rd-request-extra";

    /**
     * IP地址
     */
    String X_RD_REQUEST_FLAG = "x-rd-request-flag";

    /**
     * 请求头中的SSO令牌
     */
    String X_RD_REQUEST_SSO = "x-rd-request-sso";

    /**
     * 国际化缓存标识
     */
    String I18N_CACHE_KEY = "platform:i18n:";

    /**
     * 灰度环境标识
     */
    String X_CANARY_ENV = "x-canary-env";

    /**
     * 灰度环境ID表示
     */
    String X_CANARY_ID = "x-canary-id";

    /**
     * 灰度环境版本表示
     */
    String X_CANARY_VERSION = "x-canary-version";

    /**
     * 灰度环境类型
     */
    String X_CANARY_CATEGORY = "x-canary-category";

    /**
     * 简单模式的金丝雀（灰度）版本开启标识元数据KEY
     */
    String SERVER_CANARY_ENV = "canary";

    /**
     * 高级模式的金丝雀（灰度）版本的环境标识
     */
    String SERVER_CANARY_CATEGORY = "canary-category";

    /**
     * 金丝雀（灰度）版本的版本号元数据KEY
     */
    String SERVER_CANARY_VERSION = "canary-version";

    /**
     * 用户信息缓存KEY
     */
    String JWT_USER_KEY = "uk";

    /**
     * 是否是移动端令牌
     */
    String IS_MOBILE_TOKEN = "mobile";

    /**
     * ECC客户端公钥
     */
    String X_CLIENT_PUBLIC_KEY = "X-Client-Public-Key";

    /**
     * ECC客户端ID
     */
    String X_CLIENT_ID = "X-Client-Id";

    /**
     * ECC加密数据
     */
    String X_ENCRYPTED_DATA = "X-Encrypted-Data";

    /**
     * ECC响应加密标识
     */
    String X_RESPONSE_ENCRYPTED =  "X-Response-Encrypted";
}
