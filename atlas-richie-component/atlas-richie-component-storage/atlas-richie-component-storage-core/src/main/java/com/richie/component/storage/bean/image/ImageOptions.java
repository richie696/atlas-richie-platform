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
