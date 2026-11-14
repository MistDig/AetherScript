package dev.aetherscript.core;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static dev.aetherscript.core.PersistentVariables.*;

/** Blocking SQLite state. Hosts must dispatch every call onto an owner-managed worker. */
public final class SQLiteVariables implements AutoCloseable {
    private static final String CREATE = "CREATE TABLE variables (feature TEXT NOT NULL, player TEXT NOT NULL, name TEXT NOT NULL, revision INTEGER NOT NULL CHECK(revision>0), kind TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(feature,player,name)) STRICT";
    private final Connection connection;
    private boolean closed;
    public SQLiteVariables(Path root) throws IOException {
        Path directory = root.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        if (!directory.toRealPath().equals(directory)) throw new IOException("Variable directory traverses a link");
        Path file = directory.resolve("variables.sqlite");
        for (String suffix : List.of("", "-journal", "-wal", "-shm")) safeFile(Path.of(file + suffix));
        if (Files.exists(file) && Files.size(file) > MAX_FILE_BYTES) throw new IOException("Variable database size limit");
        Connection opened = null;
        try {
            opened = new org.sqlite.JDBC().connect("jdbc:sqlite:" + file, new Properties());
            if (opened == null) throw new SQLException("SQLite provider rejected database URL");
            connection = opened;
            sql("PRAGMA busy_timeout=5000");
            long initialVersion = scalar("PRAGMA user_version");
            if (initialVersion != 0 && initialVersion != 1) throw new IOException("Unsupported variable schema version: " + initialVersion);
            if (initialVersion == 0 && scalar("SELECT count(*) FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'") != 0) throw new IOException("Unsupported unversioned variable database");
            sql("PRAGMA journal_mode=DELETE");
            sql("PRAGMA synchronous=FULL");
            if (scalar("PRAGMA page_size") != 4096 || scalar("PRAGMA page_count") > 6144) throw new IOException("Variable database size/page limit");
            sql("PRAGMA max_page_count=6144");
            sql("BEGIN IMMEDIATE");
            try {
                long version = scalar("PRAGMA user_version");
                if (version == 0) {
                    if (scalar("SELECT count(*) FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'") != 0)
                        throw new IOException("Unsupported unversioned variable database");
                    sql(CREATE);
                    sql("PRAGMA user_version=1");
                } else if (version != 1) throw new IOException("Unsupported variable schema version: " + version);
                try (PreparedStatement statement = connection.prepareStatement("SELECT sql FROM sqlite_master WHERE type='table' AND name='variables'"); ResultSet schema = statement.executeQuery()) {
                    if (!schema.next() || !CREATE.equals(schema.getString(1)) || scalar("SELECT count(*) FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'") != 1) throw new IOException("Unsupported variable schema layout");
                }
                readSnapshot();
                sql("COMMIT");
            } catch (IOException | SQLException | RuntimeException e) { rollback(); throw e; }
        } catch (SQLException | IOException | RuntimeException e) {
            if (opened != null) try { opened.close(); } catch (SQLException ignored) { }
            throw new IOException("SQLite variable storage unavailable or corrupt", e);
        }
    }
    public synchronized Entry get(Key key) throws IOException { return snapshot().get(Objects.requireNonNull(key)); }
    public synchronized Map<Key, Entry> snapshot() throws IOException {
        ensureOpen();
        try { sql("BEGIN"); var result = readSnapshot(); sql("COMMIT"); return result; }
        catch (SQLException | IOException | RuntimeException e) { rollback(); throw failure(e); }
    }
    /** Zero means absent. Distinct keys and matching revisions are required for the entire batch. */
    public synchronized Map<Key, Entry> commit(List<Mutation> mutations) throws IOException {
        ensureOpen(); mutations = List.copyOf(mutations);
        if (mutations.isEmpty() || mutations.size() > MAX_BATCH) throw new IllegalArgumentException("Batch must contain 1..128 mutations");
        Set<Key> seen = new HashSet<>();
        for (Mutation mutation : mutations) {
            if (!seen.add(mutation.key())) throw new IllegalArgumentException("Duplicate batch key");
            validateText(mutation.value());
        }
        try {
            sql("BEGIN IMMEDIATE");
            Map<Key, Entry> next = new HashMap<>(readSnapshot());
            for (Mutation mutation : mutations) {
                Entry old = next.get(mutation.key()); long revision = old == null ? 0 : old.revision();
                if (revision != mutation.expectedRevision()) throw new ConflictException(mutation.key());
                next.put(mutation.key(), new Entry(Math.incrementExact(revision), mutation.value()));
            }
            if (next.size() > MAX_KEYS) throw new IllegalArgumentException("Variable key limit");
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO variables(feature,player,name,revision,kind,value) VALUES(?,?,?,?,?,?) ON CONFLICT(feature,player,name) DO UPDATE SET revision=excluded.revision,kind=excluded.kind,value=excluded.value")) {
                for (Mutation mutation : mutations) {
                    Key key = mutation.key(); Entry entry = next.get(key); Value value = entry.value();
                    statement.setString(1, key.feature()); statement.setString(2, key.player() == null ? "" : key.player().toString()); statement.setString(3, key.name());
                    statement.setLong(4, entry.revision()); statement.setString(5, value.type().name());
                    statement.setString(6, switch (value.type()) { case TEXT -> value.text(); case INTEGER -> Long.toString(value.integer()); case BOOLEAN -> value.bool() ? "true" : "false"; });
                    statement.executeUpdate();
                }
            }
            sql("COMMIT"); return Map.copyOf(next);
        } catch (SQLException | IOException | RuntimeException e) { rollback(); throw failure(e); }
    }
    private Map<Key, Entry> readSnapshot() throws SQLException, IOException {
        Map<Key, Entry> values = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT feature,player,name,revision,kind,value,typeof(feature),typeof(player),typeof(name),typeof(revision),typeof(kind),typeof(value) FROM variables LIMIT 4097"); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                for (int column = 7; column <= 12; column++) if (!(column == 10 ? "integer" : "text").equals(rows.getString(column))) throw new IOException("Invalid SQLite variable storage type");
                if (values.size() == MAX_KEYS) throw new IOException("Variable key limit");
                String playerText = rows.getString(2);
                byte[] raw = rows.getBytes(6);
                if (raw == null || raw.length > MAX_TEXT_BYTES) throw new IOException("Variable value size limit");
                String encoded = java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(raw)).toString();
                UUID player = playerText.isEmpty() ? null : UUID.fromString(playerText);
                if (player != null && !player.toString().equals(playerText)) throw new IOException("Noncanonical UUID");
                Type type = Type.valueOf(rows.getString(5));
                Value value = switch (type) {
                    case TEXT -> Value.text(encoded);
                    case INTEGER -> { long number = Long.parseLong(encoded); if (!Long.toString(number).equals(encoded)) throw new IOException("Noncanonical integer"); yield Value.integer(number); }
                    case BOOLEAN -> { if (!encoded.equals("true") && !encoded.equals("false")) throw new IOException("Invalid boolean"); yield Value.bool(Boolean.parseBoolean(encoded)); }
                };
                validateText(value);
                Key key = new Key(rows.getString(1), player, rows.getString(3));
                if (values.put(key, new Entry(rows.getLong(4), value)) != null) throw new IOException("Duplicate variable key");
            }
        }
        return Map.copyOf(values);
    }
    private static void validateText(Value value) {
        if (value.type() != Type.TEXT) return;
        try { java.nio.charset.StandardCharsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value.text())); }
        catch (java.nio.charset.CharacterCodingException e) { throw new IllegalArgumentException("Invalid UTF-16 text", e); }
    }
    private static void safeFile(Path file) throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe variable database file");
        if (file.getFileSystem().supportedFileAttributeViews().contains("unix") && ((Number) Files.getAttribute(file, "unix:nlink", LinkOption.NOFOLLOW_LINKS)).longValue() != 1)
            throw new IOException("Hard-linked variable database file");
    }
    private void sql(String sql) throws SQLException { try (PreparedStatement statement = connection.prepareStatement(sql)) { statement.execute(); } }
    private long scalar(String sql) throws SQLException { try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet rows = statement.executeQuery()) { if (!rows.next()) throw new SQLException("Missing scalar"); return rows.getLong(1); } }
    private void rollback() { try { sql("ROLLBACK"); } catch (SQLException ignored) { } }
    private static IOException failure(Exception e) throws IOException { if (e instanceof RuntimeException runtime) throw runtime; return e instanceof IOException io ? io : new IOException("SQLite variable operation failed", e); }
    private void ensureOpen() { if (closed) throw new IllegalStateException("Variable storage closed"); }
    @Override public synchronized void close() throws IOException { if (!closed) { closed = true; try { connection.close(); } catch (SQLException e) { throw new IOException(e); } } }
}
