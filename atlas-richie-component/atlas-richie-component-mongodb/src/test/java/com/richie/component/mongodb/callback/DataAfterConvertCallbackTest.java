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
