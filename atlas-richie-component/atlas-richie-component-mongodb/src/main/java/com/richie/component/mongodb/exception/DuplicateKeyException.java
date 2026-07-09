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
package com.richie.component.mongodb.exception;

/**
 * 插入时发生重复键冲突时抛出的异常。
 *
 * @author richie696
 * @since 1.x
 */
public class DuplicateKeyException extends MongodbException {

    public DuplicateKeyException(String msg) {
        super(msg);
    }

    public DuplicateKeyException(String msg, Throwable cause) {
        super(msg, cause);
    }

    /**
     * 将 MongoDB DuplicateKeyException 包装为当前类型。
     *
     * @param e 原始 MongoDB 异常
     * @return 包装了原始异常的新 DuplicateKeyException
     */
    public static DuplicateKeyException wrap(com.mongodb.DuplicateKeyException e) {
        return new DuplicateKeyException("Duplicate key: " + e.getMessage(), e);
    }
}
