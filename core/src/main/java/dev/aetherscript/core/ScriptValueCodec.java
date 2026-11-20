package dev.aetherscript.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Versioned typed values for TEXT storage rows. Encoding and decoding are bounded to one row. */
public final class ScriptValueCodec {
    private static final String PREFIX = "ASV1:";
    private static final int MAX_BINARY = ((PersistentVariables.MAX_TEXT_BYTES - PREFIX.length()) / 4) * 3;
    private ScriptValueCodec() {}

    public static String encode(ScriptValue value) {
        Objects.requireNonNull(value, "value");
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(new FilterOutputStream(bytes) {
                private int count;
                @Override public void write(int b) throws IOException {
                    if (++count > MAX_BINARY) throw new IOException("Typed value exceeds storage row limit");
                    out.write(b);
                }
                @Override public void write(byte[] b, int off, int len) throws IOException {
                    if (len > MAX_BINARY - count) throw new IOException("Typed value exceeds storage row limit");
                    count += len; out.write(b, off, len);
                }
            });
            writeType(out, value.type());
            writeValue(out, value, new int[] {4096});
            return PREFIX + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) { throw new IllegalArgumentException("Typed value exceeds storage row limit", e); }
    }

    public static ScriptValue decode(String text) {
        if (text == null || text.length() > PersistentVariables.MAX_TEXT_BYTES || !text.startsWith(PREFIX))
            throw new IllegalArgumentException("Invalid typed storage value");
        try {
            String payload = text.substring(PREFIX.length());
            byte[] bytes = Base64.getDecoder().decode(payload);
            if (bytes.length > MAX_BINARY || !Base64.getEncoder().encodeToString(bytes).equals(payload)) throw new IOException("Noncanonical encoding");
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            ScriptValue value = readValue(in, readType(in, 0), new int[] {4096});
            if (in.read() != -1) throw new IOException("Trailing typed value data");
            return value;
        } catch (IOException | IllegalArgumentException e) { throw new IllegalArgumentException("Invalid typed storage value", e); }
    }

    private static void writeType(DataOutputStream out, ScriptType type) throws IOException {
        // Stable ASV1 wire tags; these must never follow enum declaration order.
        out.writeByte(switch (type.kind()) {
            case INTEGER -> 0; case TEXT -> 1; case BOOLEAN -> 2; case LIST -> 3;
            case UUID -> 4; case LOCATION -> 5; case UNIT -> 6; case OPTIONAL -> 7;
            case MAP -> 8; case SET -> 9;
        });
        if (type.isMap()) writeType(out, type.key());
        if (type.element() != null) writeType(out, type.element());
    }
    private static ScriptType readType(DataInputStream in, int depth) throws IOException {
        if (depth > 8) throw new IOException("Type nesting limit");
        int tag = in.readUnsignedByte();
        return switch (tag) {
            case 0 -> ScriptType.INTEGER;
            case 1 -> ScriptType.TEXT;
            case 2 -> ScriptType.BOOLEAN;
            case 4 -> ScriptType.UUID;
            case 5 -> ScriptType.LOCATION;
            case 6 -> ScriptType.UNIT;
            case 3 -> ScriptType.list(readType(in, depth + 1));
            case 7 -> ScriptType.optional(readType(in, depth + 1));
            case 9 -> ScriptType.set(readType(in, depth + 1));
            case 8 -> ScriptType.map(readType(in, depth + 1), readType(in, depth + 1));
            default -> throw new IOException("Unknown type");
        };
    }
    private static void writeValue(DataOutputStream out, ScriptValue value, int[] budget) throws IOException {
        if (--budget[0] < 0) throw new IOException("Typed value node limit");
        switch (value.type().kind()) {
            case INTEGER -> out.writeLong(value.integer());
            case TEXT -> {
                if (value.text().length() > MAX_BINARY) throw new IOException("Text length limit");
                byte[] bytes;
                try {
                    ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value.text()));
                    bytes = new byte[encoded.remaining()]; encoded.get(bytes);
                } catch (CharacterCodingException e) { throw new IllegalArgumentException("Invalid Unicode text", e); }
                out.writeInt(bytes.length); out.write(bytes);
            }
            case BOOLEAN -> out.writeByte(value.bool() ? 1 : 0);
            case UUID -> writeUuid(out, value.uuid());
            case LOCATION -> {
                var l = value.location(); writeUuid(out, l.worldId());
                out.writeDouble(l.x()); out.writeDouble(l.y()); out.writeDouble(l.z()); out.writeFloat(l.yaw()); out.writeFloat(l.pitch());
            }
            case UNIT -> { }
            case OPTIONAL -> { out.writeByte(value.optional().isPresent() ? 1 : 0); if (value.optional().isPresent()) writeValue(out, value.optional().orElseThrow(), budget); }
            case LIST -> { out.writeShort(value.list().size()); for (var item : value.list()) writeValue(out, item, budget); }
            case SET -> { out.writeShort(value.set().size()); for (var item : value.set()) writeValue(out, item, budget); }
            case MAP -> { out.writeShort(value.map().size()); for (var item : value.map().entrySet()) { writeValue(out, item.getKey(), budget); writeValue(out, item.getValue(), budget); } }
        }
    }
    private static ScriptValue readValue(DataInputStream in, ScriptType type, int[] budget) throws IOException {
        if (--budget[0] < 0) throw new IOException("Typed value node limit");
        Object value = switch (type.kind()) {
            case INTEGER -> in.readLong();
            case TEXT -> {
                int length = in.readInt();
                if (length < 0 || length > MAX_BINARY || length > in.available()) throw new IOException("Text length limit");
                yield StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(in.readNBytes(length))).toString();
            }
            case BOOLEAN -> readFlag(in);
            case UUID -> readUuid(in);
            case LOCATION -> new ScriptValue.LocationSnapshot(readUuid(in), in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(), in.readFloat());
            case UNIT -> ScriptValue.Unit.INSTANCE;
            case OPTIONAL -> readFlag(in) ? Optional.of(readValue(in, type.element(), budget)) : Optional.empty();
            case LIST -> { int size = readSize(in); var list = new ArrayList<ScriptValue>(size); for (int i = 0; i < size; i++) list.add(readValue(in, type.element(), budget)); yield list; }
            case SET -> {
                int size = readSize(in); var set = new LinkedHashSet<ScriptValue>();
                for (int i = 0; i < size; i++) if (!set.add(readValue(in, type.element(), budget))) throw new IOException("Duplicate set element");
                yield set;
            }
            case MAP -> {
                int size = readSize(in); var map = new LinkedHashMap<ScriptValue, ScriptValue>();
                for (int i = 0; i < size; i++) if (map.put(readValue(in, type.key(), budget), readValue(in, type.element(), budget)) != null) throw new IOException("Duplicate map key");
                yield map;
            }
        };
        return new ScriptValue(type, value);
    }
    private static boolean readFlag(DataInputStream in) throws IOException { int flag = in.readUnsignedByte(); if (flag > 1) throw new IOException("Invalid boolean"); return flag == 1; }
    private static int readSize(DataInputStream in) throws IOException { int size = in.readUnsignedShort(); if (size > 256) throw new IOException("Collection size limit"); return size; }
    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException { out.writeLong(uuid.getMostSignificantBits()); out.writeLong(uuid.getLeastSignificantBits()); }
    private static UUID readUuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
}
