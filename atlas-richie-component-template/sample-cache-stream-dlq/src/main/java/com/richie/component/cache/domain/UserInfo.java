package com.richie.component.cache.domain;

import com.richie.contract.model.BaseStreamMessage;
import lombok.Data;

import java.io.Serializable;

@Data
public class UserInfo implements Serializable, BaseStreamMessage {

    private String name;

    private Integer age;

    private String slogan;
}
