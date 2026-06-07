package com.richie.component.mongodb.exception;

/**
 * Exception thrown when a duplicate key violation occurs during insert.
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
     * Wrap a MongoDB DuplicateKeyException into this type.
     *
     * @param e the original MongoDB exception
     * @return a new DuplicateKeyException wrapping the original
     */
    public static DuplicateKeyException wrap(com.mongodb.DuplicateKeyException e) {
        return new DuplicateKeyException("Duplicate key: " + e.getMessage(), e);
    }
}
