package com.xyzw.webhelper.xyzw.ws;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BonCodec {
    BonCodec() {
    }

    byte[] encode(Object value) {
        DataWriter writer = new DataWriter();
        writeValue(writer, value);
        return writer.toByteArray();
    }

    Object decode(byte[] bytes) {
        DataReader reader = new DataReader(bytes);
        return readValue(reader);
    }

    private void writeValue(DataWriter writer, Object value) {
        if (value == null) {
            writer.writeInt8(0);
            return;
        }
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            writer.writeInt8(1);
            writer.writeInt32(((Number) value).intValue());
            return;
        }
        if (value instanceof Long) {
            writer.writeInt8(2);
            writer.writeInt64((Long) value);
            return;
        }
        if (value instanceof Float) {
            writer.writeInt8(3);
            writer.writeFloat32((Float) value);
            return;
        }
        if (value instanceof Double) {
            writer.writeInt8(4);
            writer.writeFloat64((Double) value);
            return;
        }
        if (value instanceof Boolean) {
            writer.writeInt8(6);
            writer.writeInt8((Boolean) value ? 1 : 0);
            return;
        }
        if (value instanceof String) {
            writer.writeInt8(5);
            writer.writeUtf((String) value);
            return;
        }
        if (value instanceof byte[]) {
            writer.writeInt8(7);
            byte[] bytes = (byte[]) value;
            writer.write7BitInt(bytes.length);
            writer.writeBytes(bytes);
            return;
        }
        if (value instanceof ByteBuffer) {
            ByteBuffer buffer = (ByteBuffer) value;
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            writeValue(writer, bytes);
            return;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            writer.writeInt8(9);
            writer.write7BitInt(list.size());
            for (Object item : list) {
                writeValue(writer, item);
            }
            return;
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) value;
            writer.writeInt8(8);
            writer.write7BitInt(map.size());
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                writeValue(writer, entry.getKey());
                writeValue(writer, entry.getValue());
            }
            return;
        }
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            long l = (long) d;
            if (l == d && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                writer.writeInt8(1);
                writer.writeInt32((int) l);
            } else if (l == d) {
                writer.writeInt8(2);
                writer.writeInt64(l);
            } else {
                writer.writeInt8(4);
                writer.writeFloat64(d);
            }
            return;
        }

        // Fallback: encode as string
        writer.writeInt8(5);
        writer.writeUtf(String.valueOf(value));
    }

    private Object readValue(DataReader reader) {
        int tag = reader.readUInt8();
        switch (tag) {
            case 0:
                return null;
            case 1:
                return reader.readInt32();
            case 2:
                return reader.readInt64();
            case 3:
                return reader.readFloat32();
            case 4:
                return reader.readFloat64();
            case 5:
                return reader.readUtf();
            case 6:
                return reader.readUInt8() == 1;
            case 7: {
                int len = reader.read7BitInt();
                return reader.readBytes(len);
            }
            case 8: {
                int count = reader.read7BitInt();
                Map<String, Object> map = new LinkedHashMap<String, Object>(count);
                for (int i = 0; i < count; i++) {
                    Object key = readValue(reader);
                    Object value = readValue(reader);
                    map.put(String.valueOf(key), value);
                }
                return map;
            }
            case 9: {
                int count = reader.read7BitInt();
                List<Object> list = new ArrayList<Object>(count);
                for (int i = 0; i < count; i++) {
                    list.add(readValue(reader));
                }
                return list;
            }
            case 10:
                return reader.readInt64();
            case 99:
                return reader.readStringRef();
            default:
                return null;
        }
    }

    private static final class DataReader {
        private final byte[] data;
        private int position;
        private final List<String> strings = new ArrayList<String>();

        private DataReader(byte[] data) {
            this.data = data == null ? new byte[0] : data;
        }

        private int readUInt8() {
            if (position >= data.length) {
                return 0;
            }
            return data[position++] & 0xff;
        }

        private short readInt16() {
            int b0 = readUInt8();
            int b1 = readUInt8();
            return (short) (b0 | (b1 << 8));
        }

        private int readInt32() {
            int b0 = readUInt8();
            int b1 = readUInt8();
            int b2 = readUInt8();
            int b3 = readUInt8();
            return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
        }

        private long readInt64() {
            long lo = readInt32();
            if (lo < 0) {
                lo += 0x100000000L;
            }
            long hi = readInt32();
            return lo + (hi * 0x100000000L);
        }

        private float readFloat32() {
            return Float.intBitsToFloat(readInt32());
        }

        private double readFloat64() {
            long low = readInt32() & 0xffffffffL;
            long high = readInt32() & 0xffffffffL;
            long value = low | (high << 32);
            return Double.longBitsToDouble(value);
        }

        private int read7BitInt() {
            int value = 0;
            int shift = 0;
            int b;
            int count = 0;
            do {
                if (count++ == 35) {
                    throw new IllegalStateException("Bad 7-bit int");
                }
                b = readUInt8();
                value |= (b & 0x7f) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            return value;
        }

        private String readUtf() {
            int len = read7BitInt();
            if (len <= 0) {
                strings.add("");
                return "";
            }
            byte[] bytes = readBytes(len);
            String value = new String(bytes, StandardCharsets.UTF_8);
            strings.add(value);
            return value;
        }

        private String readStringRef() {
            int idx = read7BitInt();
            if (idx < 0 || idx >= strings.size()) {
                return "";
            }
            return strings.get(idx);
        }

        private byte[] readBytes(int len) {
            if (len <= 0) {
                return new byte[0];
            }
            int end = Math.min(data.length, position + len);
            byte[] out = new byte[end - position];
            System.arraycopy(data, position, out, 0, out.length);
            position = end;
            return out;
        }
    }

    private static final class DataWriter {
        private ByteBuffer buffer = ByteBuffer.allocate(1024);

        private void ensure(int size) {
            if (buffer.remaining() >= size) {
                return;
            }
            int newSize = Math.max(buffer.capacity() * 2, buffer.capacity() + size);
            ByteBuffer next = ByteBuffer.allocate(newSize);
            buffer.flip();
            next.put(buffer);
            buffer = next;
        }

        private void writeInt8(int v) {
            ensure(1);
            buffer.put((byte) (v & 0xff));
        }

        private void writeInt16(int v) {
            ensure(2);
            buffer.put((byte) (v & 0xff));
            buffer.put((byte) ((v >> 8) & 0xff));
        }

        private void writeInt32(int v) {
            ensure(4);
            buffer.put((byte) (v & 0xff));
            buffer.put((byte) ((v >> 8) & 0xff));
            buffer.put((byte) ((v >> 16) & 0xff));
            buffer.put((byte) ((v >> 24) & 0xff));
        }

        private void writeInt64(long v) {
            writeInt32((int) v);
            if (v < 0) {
                writeInt32(~(int) Math.floor(-v / 0x100000000L));
            } else {
                writeInt32((int) Math.floor(v / 0x100000000L));
            }
        }

        private void writeFloat32(float v) {
            writeInt32(Float.floatToIntBits(v));
        }

        private void writeFloat64(double v) {
            long bits = Double.doubleToLongBits(v);
            writeInt32((int) (bits & 0xffffffffL));
            writeInt32((int) ((bits >>> 32) & 0xffffffffL));
        }

        private void write7BitInt(int v) {
            int n = v;
            while (n >= 0x80) {
                writeInt8((n & 0xff) | 0x80);
                n >>>= 7;
            }
            writeInt8(n & 0x7f);
        }

        private void writeUtf(String value) {
            if (value == null || value.isEmpty()) {
                write7BitInt(0);
                return;
            }
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            write7BitInt(bytes.length);
            writeBytes(bytes);
        }

        private void writeBytes(byte[] bytes) {
            if (bytes == null || bytes.length == 0) {
                return;
            }
            ensure(bytes.length);
            buffer.put(bytes);
        }

        private byte[] toByteArray() {
            buffer.flip();
            byte[] out = new byte[buffer.remaining()];
            buffer.get(out);
            return out;
        }
    }
}
