/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

