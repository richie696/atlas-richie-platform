/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.bean.image;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ImageOptions {

    /**
     * 缩放比例
     */
    private Integer scale;

    /**
     * 剪裁比例
     */
    private String crop;

    /**
     * 旋转角度
     */
    private Integer rotate;

    /**
     * 图片格式
     */
    private ImageFormat format;

    /**
     * 图片质量（取值范围0~100，100为原画质，90表示90%画质）
     */
    private Integer quality;

    /**
     * 是否开启极致压缩
     */
    private boolean extremeCompression;


}
