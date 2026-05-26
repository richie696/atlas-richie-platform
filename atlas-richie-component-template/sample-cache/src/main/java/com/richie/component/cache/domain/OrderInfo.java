package com.richie.component.cache.domain;

import com.richie.contract.model.BaseStreamMessage;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderInfo implements Serializable, BaseStreamMessage {

    private Long orderId;

    private Long userId;

    private String productName;

    private String productCode;

    private BigDecimal amount;
}
