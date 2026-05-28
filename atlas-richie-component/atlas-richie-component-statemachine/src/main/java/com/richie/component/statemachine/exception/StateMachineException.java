package com.richie.component.statemachine.exception;

import lombok.Getter;

/**
 * 状态机统一异常
 *
 * @author richie696
 * @since 1.0.0
 */
@Getter
public class StateMachineException extends RuntimeException {

    private final String code;

    public StateMachineException(String code, String message) {
        super(message);
        this.code = code;
    }

    public StateMachineException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

}

