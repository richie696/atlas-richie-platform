package cn.richie696.component.secret.provider.kmip;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

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
}
