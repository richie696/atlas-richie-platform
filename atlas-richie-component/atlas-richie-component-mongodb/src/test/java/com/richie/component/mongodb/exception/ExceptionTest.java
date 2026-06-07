package com.richie.component.mongodb.exception;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExceptionTest {

    @Test
    void mongodbException_shouldStoreMessage() {
        MongodbException ex = new MongodbException("test message");
        assertThat(ex.getMessage()).isEqualTo("test message");
    }

    @Test
    void mongodbException_shouldStoreCause() {
        Throwable cause = new RuntimeException("cause");
        MongodbException ex = new MongodbException("test", cause);
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    void duplicateKeyException_shouldExtendMongodbException() {
        assertThat(new DuplicateKeyException("msg")).isInstanceOf(MongodbException.class);
    }

    @Test
    void duplicateKeyException_canBeThrownAndCaught() {
        assertThatThrownBy(() -> { throw new DuplicateKeyException("test"); }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void connectionException_shouldExtendMongodbException() {
        assertThat(new ConnectionException("msg")).isInstanceOf(MongodbException.class);
    }

    @Test
    void transactionException_shouldExtendMongodbException() {
        assertThat(new TransactionException("msg")).isInstanceOf(MongodbException.class);
    }

    @Test
    void exceptions_shouldBeThrowable() {
        assertThatThrownBy(() -> { throw new MongodbException("test"); }).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> { throw new DuplicateKeyException("test"); }).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> { throw new ConnectionException("test"); }).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> { throw new TransactionException("test"); }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void duplicateKeyException_withStringArg_shouldStoreMessage() {
        DuplicateKeyException ex = new DuplicateKeyException("Duplicate key error");
        assertThat(ex.getMessage()).isEqualTo("Duplicate key error");
    }

    @Test
    void connectionException_withMessageAndCause_shouldStoreBoth() {
        Throwable cause = new RuntimeException("cause");
        ConnectionException ex = new ConnectionException("connection failed", cause);
        assertThat(ex.getMessage()).isEqualTo("connection failed");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    void transactionException_withMessageAndCause_shouldStoreBoth() {
        Throwable cause = new RuntimeException("cause");
        TransactionException ex = new TransactionException("transaction failed", cause);
        assertThat(ex.getMessage()).isEqualTo("transaction failed");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    void duplicateKeyException_withMessageAndCause_shouldStoreBoth() {
        Throwable cause = new RuntimeException("cause");
        DuplicateKeyException ex = new DuplicateKeyException("duplicate key", cause);
        assertThat(ex.getMessage()).isEqualTo("duplicate key");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    void duplicateKeyException_wrap_shouldCreateWithCorrectMessage() {
        com.mongodb.DuplicateKeyException mongoEx = new com.mongodb.DuplicateKeyException(
            new org.bson.BsonDocument(), new com.mongodb.ServerAddress("localhost"), null);
        DuplicateKeyException ex = DuplicateKeyException.wrap(mongoEx);
        assertThat(ex.getMessage()).startsWith("Duplicate key:");
        assertThat(ex.getCause()).isEqualTo(mongoEx);
    }
}
