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
package com.richie.component.desensitize.core.strategy;

import com.richie.component.desensitize.core.model.MaskType;
import org.springframework.stereotype.Component;

/**
 * 地址脱敏：保留前 6 个字符。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
@Component
public class AddressMaskingStrategy extends AbstractKeepEdgeMaskingStrategy {

    @Override
    /**
     * 判断是否支持地址类型脱敏。
     *
     * @param type 脱敏类型
     * @return 是否支持
     */
    public boolean supports(MaskType type) {
        return type == MaskType.ADDRESS;
    }

    /**
     * supportedType。
     * @return 处理结果
     */
    @Override
    protected MaskType supportedType() {
        return MaskType.ADDRESS;
    }
}
