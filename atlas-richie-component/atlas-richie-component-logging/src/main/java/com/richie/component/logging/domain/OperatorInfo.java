package com.richie.component.logging.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 操作人信息实体类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-04-27 11:34:12
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperatorInfo implements Serializable {

    /** 操作人 ID */
    private String id;

    /** 操作人姓名 */
    private String name;

}
