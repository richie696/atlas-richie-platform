package cn.richie696.component.secret.provider.kmip;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Minimal KMIP 2.1 TTLV codec for Get-free Encrypt/Decrypt requests. */
final class KmipTtlv {
    static final int STRUCTURE = 0x01, INTEGER = 0x02, ENUMERATION = 0x05, TEXT = 0x07, BYTE_STRING = 0x08;
    record Element(int tag, int type, byte[] value) { }
    private KmipTtlv() { }
    static byte[] structure(int tag, byte[]... children) { ByteArrayOutputStream out = new ByteArrayOutputStream(); for (byte[] child : children) out.writeBytes(child); return element(tag, STRUCTURE, out.toByteArray()); }
    static byte[] integer(int tag, int value) { return element(tag, INTEGER, ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()); }
    static byte[] enumeration(int tag, int value) { return element(tag, ENUMERATION, ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()); }
    static byte[] text(int tag, String value) { return element(tag, TEXT, value.getBytes(StandardCharsets.UTF_8)); }
    static byte[] bytes(int tag, byte[] value) { return element(tag, BYTE_STRING, value.clone()); }
    static byte[] element(int tag, int type, byte[] value) { ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write((tag >>> 16) & 0xff); out.write((tag >>> 8) & 0xff); out.write(tag & 0xff); out.write(type); out.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value.length).array()); out.writeBytes(value); int padding = (8 - (value.length % 8)) % 8; out.writeBytes(new byte[padding]); return out.toByteArray(); }
    static List<Element> children(byte[] bytes) { return children(bytes, 0, bytes.length); }
    private static List<Element> children(byte[] bytes, int start, int end) { List<Element> result = new ArrayList<>(); int offset = start; while (offset + 8 <= end) { int tag = ((bytes[offset] & 255) << 16) | ((bytes[offset + 1] & 255) << 8) | (bytes[offset + 2] & 255); int type = bytes[offset + 3] & 255; int length = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.BIG_ENDIAN).getInt(); if (length < 0 || offset + 8 + length > end) throw new IllegalArgumentException("Invalid KMIP TTLV length"); byte[] value = Arrays.copyOfRange(bytes, offset + 8, offset + 8 + length); result.add(new Element(tag, type, value)); offset += 8 + length + ((8 - (length % 8)) % 8); } if (offset != end) throw new IllegalArgumentException("Invalid KMIP TTLV padding"); return result; }
    static byte[] first(List<Element> elements, int tag, int type) { for (Element element : elements) { if (element.tag() == tag && element.type() == type) return element.value(); if (element.type() == STRUCTURE) { byte[] nested = first(children(element.value()), tag, type); if (nested != null) return nested; } } return null; }
}
