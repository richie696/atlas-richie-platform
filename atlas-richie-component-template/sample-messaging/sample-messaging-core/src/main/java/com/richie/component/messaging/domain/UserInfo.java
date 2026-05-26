package com.richie.component.messaging.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class UserInfo implements Serializable {

    private Integer id;

    private String name;

    private Integer age;

    private String param1;

    private String param2;

    private String param3;

    private String param4;

    private String param5;

    private String param6;

}
