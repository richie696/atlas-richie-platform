package com.richie.component.cache.commons;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 地理位置结果类
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-26 10:47:44
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeoPointResult {

    /**
     * 成员名称
     */
    private String member;
    /**
     * 经度
     */
    private double longitude;
    /**
     * 纬度
     */
    private double latitude;
    /**
     * 距离
     * <p>
     * 单位：米，可为null
     */
    private Double distance;

}
