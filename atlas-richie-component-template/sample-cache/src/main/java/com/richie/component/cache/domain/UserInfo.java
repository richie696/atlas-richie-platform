package com.richie.component.cache.domain;

import com.richie.contract.model.BaseStreamMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo implements Serializable, BaseStreamMessage {

    private Integer id;

    private String name;

    private Integer age;

    private String slogan;
}
