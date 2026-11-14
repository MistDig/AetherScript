import dev.aetherscript.core.SQLiteVariables;
import dev.aetherscript.core.PersistentVariables.*;
import java.nio.file.Path;
import java.util.List;

/** Invoked as a Java source launcher against each shipped shaded JAR. */
class StoragePackageCheck {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Key key = new Key("package-check", null, "count");
        try (var store = new SQLiteVariables(root)) {
            store.commit(List.of(new Mutation(key, 0, Value.integer(7))));
            try {
                store.commit(List.of(new Mutation(key, 0, Value.integer(8))));
                throw new AssertionError("Stale mutation accepted");
            } catch (ConflictException expected) { }
        }
        try (var reopened = new SQLiteVariables(root)) {
            if (!reopened.get(key).equals(new Entry(1, Value.integer(7))))
                throw new AssertionError("Packaged storage failed restart");
        }
        System.out.println("PACKAGED_SQLITE_OK");
    }
}
