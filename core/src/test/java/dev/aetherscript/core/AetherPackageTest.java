package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AetherPackageTest {
    @TempDir Path temp;
    private Path pack() throws Exception { return Files.createDirectory(temp.resolve("demo.aetherpack")); }
    private void write(Path pack, String file, String text) throws Exception { Files.writeString(pack.resolve(file), text); }
    private static final String MAIN = "import \"helper.aether\"\nfeature demo version \"1\"\ncommand demo permission \"test.demo\" sender any {\n send greeting()\n}\n";
    private static final String HELPER = "fn greeting(): text {\n return \"hello\"\n}\n";
    @Test void deterministicSnapshotAndDurableRecoveryAreIndependentOfEditableFiles() throws Exception {
        Path pack = pack(); write(pack, "main.aether", MAIN); write(pack, "helper.aether", HELPER);
        var source = AetherPackage.read(pack);
        assertArrayEquals(source.bytes(), AetherPackage.read(pack).bytes());
        assertEquals("aetherpack", source.format());
        assertEquals("hello", source.compile().program().evaluate(List.of()).getFirst().text());
        RevisionStore store = new RevisionStore(temp.resolve("storage"), 5);
        store.commit(store.prepare(Map.of("demo", source), Set.of()));
        Files.delete(pack.resolve("helper.aether")); Files.delete(pack.resolve("main.aether")); Files.delete(pack);
        var recovered = new RevisionStore(temp.resolve("storage"), 5).recover("demo");
        assertEquals(source.hash(), recovered.source().hash());
        assertEquals("hello", recovered.compiled().program().evaluate(List.of()).getFirst().text());
    }
    @Test void scriptStoreRejectsConflictsAndRemoteMutationOfPackages() throws Exception {
        Path pack = pack(); write(pack, "main.aether", MAIN); write(pack, "helper.aether", HELPER);
        ScriptStore store = new ScriptStore(temp);
        assertTrue(store.inspect("demo").get("demo").valid());
        assertThrows(java.io.IOException.class, () -> store.textSource("demo"));
        write(temp, "demo.aether", MAIN);
        assertFalse(store.inspect("demo").get("demo").valid());
    }
    @Test void reportsOriginalModuleFileAndLine() throws Exception {
        Path pack = pack(); write(pack, "main.aether", MAIN); write(pack, "helper.aether", "// comment\nfn greeting(): text {\n return missing\n}\n");
        ValidationException error = assertThrows(ValidationException.class, () -> AetherPackage.read(pack).compile());
        assertEquals("helper.aether", error.path()); assertEquals(3, error.line());
    }
    @Test void rejectsCyclesDuplicatesUnsafeImportsAndUnreachableFiles() throws Exception {
        Path pack = pack(); write(pack, "main.aether", MAIN); write(pack, "helper.aether", "import \"main.aether\"\n" + HELPER);
        assertThrows(ValidationException.class, () -> AetherPackage.read(pack).compile());
        write(pack, "helper.aether", HELPER);
        write(pack, "main.aether", "import \"helper.aether\"\n" + MAIN);
        assertThrows(ValidationException.class, () -> AetherPackage.read(pack).compile());
        write(pack, "main.aether", MAIN.replace("helper.aether", "../helper.aether"));
        assertThrows(ValidationException.class, () -> AetherPackage.read(pack).compile());
        write(pack, "main.aether", MAIN); write(pack, "unused.aether", HELPER);
        assertThrows(ValidationException.class, () -> AetherPackage.read(pack).compile());
    }
    @Test void rejectsModuleCommandsNestedDirectoriesAndOversize() throws Exception {
        Path pack = pack(); write(pack, "main.aether", MAIN); write(pack, "helper.aether", "command demo permission \"test.demo\" sender any {}\n");
        assertThrows(ValidationException.class, () -> AetherPackage.read(pack).compile());
        Files.createDirectory(pack.resolve("nested"));
        assertThrows(ValidationException.class, () -> AetherPackage.read(pack));
        Files.delete(pack.resolve("nested")); write(pack, "helper.aether", " ".repeat(65537));
        assertThrows(ValidationException.class, () -> AetherPackage.read(pack));
    }
    @Test void rejectsMoreThan32FilesAndDuplicateDeclarations() throws Exception {
        Path pack = pack(); write(pack, "main.aether", MAIN); write(pack, "helper.aether", HELPER);
        write(pack, "main.aether", MAIN.replace("feature demo version \"1\"\n", "feature demo version \"1\"\n" + HELPER));
        assertThrows(ValidationException.class, () -> AetherPackage.read(pack).compile());
        for (int i = 0; i < 31; i++) write(pack, "module-" + i + ".aether", HELPER);
        assertThrows(ValidationException.class, () -> AetherPackage.read(pack));
    }
    @Test void sharedDependenciesCompileOnceAndLateImportsAreRejected() throws Exception {
        Path pack = pack();
        write(pack, "main.aether", MAIN.replace("import \"helper.aether\"", "import \"left.aether\"\nimport \"right.aether\""));
        write(pack, "helper.aether", HELPER);
        write(pack, "left.aether", "import \"helper.aether\"\nfn left(): text {\n return greeting()\n}\n");
        write(pack, "right.aether", "import \"helper.aether\"\nfn right(): text {\n return greeting()\n}\n");
        assertNotNull(AetherPackage.read(pack).compile());
        write(pack, "right.aether", "fn right(): text {\n return \"right\"\n}\nimport \"helper.aether\"\n");
        var error = assertThrows(ValidationException.class, () -> AetherPackage.read(pack).compile());
        assertEquals("right.aether", error.path()); assertEquals(4, error.line());
    }

    @Test void rejectsSymbolicLinkMembersWhenPlatformSupportsLinks() throws Exception {
        Path pack = pack(); write(pack, "main.aether", MAIN);
        Path outside = Files.writeString(temp.resolve("outside.aether"), HELPER);
        try { Files.createSymbolicLink(pack.resolve("helper.aether"), outside); }
        catch (java.io.IOException | UnsupportedOperationException | SecurityException unavailable) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links unavailable: " + unavailable.getClass().getSimpleName());
        }
        assertThrows(ValidationException.class, () -> AetherPackage.read(pack));
    }

}
