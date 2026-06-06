package com.richie.component.mongodb.callback;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DataAfterConvertCallbackTest {

    private final DataAfterConvertCallback<AmountDoc> callback = new DataAfterConvertCallback<>();

    @Test
    void onAfterConvert_mapsCompositeIdToBigDecimal() {
        AmountDoc entity = new AmountDoc();
        Document id = new Document("amount", 12.5d);
        Document document = new Document("_id", id);

        AmountDoc result = callback.onAfterConvert(entity, document, "it_amount");

        assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(12.5d));
    }

    @Test
    void onAfterConvert_returnsEntityWhenIdNotDocument() {
        AmountDoc entity = new AmountDoc();
        Document document = new Document("_id", "plain");

        assertThat(callback.onAfterConvert(entity, document, "it_amount")).isSameAs(entity);
    }

    static class AmountDoc {
        private BigDecimal amount;

        BigDecimal getAmount() {
            return amount;
        }

        void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }
}
