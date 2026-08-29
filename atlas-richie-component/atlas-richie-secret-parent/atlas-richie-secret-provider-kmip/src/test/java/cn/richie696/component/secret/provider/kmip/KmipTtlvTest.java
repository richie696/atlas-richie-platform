package cn.richie696.component.secret.provider.kmip;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmipTtlvTest {
    @Test
    void encodesAndParsesNestedTtlvWithoutPlaintextTransformation() {
        byte[] message = KmipTtlv.structure(0x420078,
                KmipTtlv.structure(0x420079,
                        KmipTtlv.text(0x420094, "key-1"),
                        KmipTtlv.bytes(0x420022, "payload".getBytes(StandardCharsets.UTF_8))));
        List<KmipTtlv.Element> elements = KmipTtlv.children(message);
        assertEquals(1, elements.size());
        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), KmipTtlv.first(elements, 0x420022, KmipTtlv.BYTE_STRING));
    }

    @Test
    void parsesOnlySuccessfulMatchingEncryptBatchData() {
        byte[] response = response(31, 0, "ciphertext".getBytes(StandardCharsets.UTF_8));

        assertArrayEquals("ciphertext".getBytes(StandardCharsets.UTF_8),
                KmipSecretClient.parseResponse(31, response));
    }

    @Test
    void rejectsFailedMismatchedAndAmbiguousResponses() {
        assertThatThrownBy(() -> KmipSecretClient.parseResponse(
                31, response(31, 1, "untrusted".getBytes(StandardCharsets.UTF_8))))
                .hasMessageContaining("result status 1");
        assertThatThrownBy(() -> KmipSecretClient.parseResponse(
                31, response(32, 0, "plaintext".getBytes(StandardCharsets.UTF_8))))
                .hasMessageContaining("does not match");

        byte[] first = batch(31, 0, "first".getBytes(StandardCharsets.UTF_8));
        byte[] second = batch(31, 0, "second".getBytes(StandardCharsets.UTF_8));
        byte[] ambiguous = KmipTtlv.structure(0x42007b, first, second);
        assertThatThrownBy(() -> KmipSecretClient.parseResponse(31, ambiguous))
                .hasMessageContaining("multiple Batch Item");
    }

    private byte[] response(int operation, int status, byte[] data) {
        return KmipTtlv.structure(0x42007b, batch(operation, status, data));
    }

    private byte[] batch(int operation, int status, byte[] data) {
        return KmipTtlv.structure(0x42000f,
                KmipTtlv.enumeration(0x42005c, operation),
                KmipTtlv.enumeration(0x42007f, status),
                KmipTtlv.structure(0x42007c,
                        KmipTtlv.bytes(0x4200c2, data)));
    }
}
