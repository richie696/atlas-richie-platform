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
package com.richie.component.desensitize.core.model;

/**
 * 脱敏遮罩类型
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public enum MaskType {
    /**
     * 电话号码
     */
    PHONE,
    /**
     * 身份证号
     */
    ID_CARD,
    /**
     * 电子邮件
     */
    EMAIL,
    /**
     * 银行卡号
     */
    BANK_CARD,
    /**
     * 姓名
     */
    NAME,
    /**
     * 地址
     */
    ADDRESS,
    /**
     * 密码
     */
    PASSWORD,
    /**
     * 自定义
     */
    CUSTOM
}
