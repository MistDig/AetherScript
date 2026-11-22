package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PackageAuthoringTest {
    private String entry(String message) { return "feature example version \"1\"\ncommand example permission \"example.use\" sender any {\nsend \"" + message + "\"\n}\n"; }
    private String packed(String message) throws Exception { return FeatureParser.JSON.writeValueAsString(Map.of("main.aether", entry(message))); }
    @Test void regularPackageFileSupportsValidationCasAndImmutableFormat(@TempDir Path root) throws Exception {
        var scripts = new ScriptStore(root);
        var one = scripts.compareAndSwapSource("example", "ABSENT", "aetherpack", packed("one"));
        assertTrue(Files.isRegularFile(root.resolve("example.aetherpack")));
        assertTrue(scripts.inspect("example").get("example").valid());
        assertEquals(one.hash(), scripts.editableSource("example", "aetherpack").hash());
        assertEquals("TEXT_SOURCE_REQUIRED", assertThrows(java.io.IOException.class, () -> scripts.compareAndSwapText("example", one.hash(), entry("two"))).getMessage());
        assertThrows(java.io.IOException.class, () -> scripts.compareAndSwapSource("example", "stale", "aetherpack", packed("two")));
        var two = scripts.compareAndSwapSource("example", one.hash(), "aetherpack", packed("two"));
        assertNotEquals(one.hash(), two.hash());
    }
    @Test void directoryPackageRemainsReadableButCannotBeMutated(@TempDir Path root) throws Exception {
        Path pack=Files.createDirectory(root.resolve("example.aetherpack")); Files.writeString(pack.resolve("main.aether"),entry("local"));
        var scripts=new ScriptStore(root); assertTrue(scripts.inspect("example").get("example").valid());
        assertEquals("PACKAGE_DIRECTORY_READ_ONLY",assertThrows(java.io.IOException.class,()->scripts.editableSource("example","aetherpack")).getMessage());
        assertThrows(java.io.IOException.class,()->scripts.beginRemoteIntent("example","ABSENT","aetherpack",packed("remote"),0));
        assertEquals(entry("local"),Files.readString(pack.resolve("main.aether")));
    }
    @Test void packageJournalRecoversOnlyCommittedMatchingFormatAndPreservesOwnerChanges(@TempDir Path root) throws Exception {
        Path directory=Files.createDirectory(root.resolve("scripts")); var scripts=new ScriptStore(directory);
        var store=new RevisionStore(root.resolve("storage"),5); var manager=new FeatureManager(scripts,store); var empty=new Registry().snapshot();
        String text=packed("committed"); var change=manager.prepareSource("example","aetherpack",text,empty);
        scripts.beginRemoteIntent("example","ABSENT","aetherpack",text,0);store.commit(change.durable());scripts.recoverRemoteIntents(store);
        assertEquals("aetherpack",scripts.editableSource("example","aetherpack").format());
        var current=scripts.editableSource("example","aetherpack");String next=packed("next");
        scripts.beginRemoteIntent("example",current.hash(),"aetherpack",next,store.state().sequence());
        store.commit(manager.prepareSource("example","aetherpack",next,empty).durable());
        String owner=packed("owner");Files.writeString(directory.resolve("example.aetherpack"),owner);scripts.recoverRemoteIntents(store);
        assertEquals(owner,Files.readString(directory.resolve("example.aetherpack")));
    }
    @Test void canonicalMapOrderingAndModuleDiagnosticsRemainStable(@TempDir Path root) throws Exception {
        String main="import \"helper.aether\"\n"+entry("ok");String helper="// helper\n";
        String packed=FeatureParser.JSON.writeValueAsString(new LinkedHashMap<>(Map.of("main.aether",main,"helper.aether",helper)));
        var source=SourceFile.authored("example","aetherpack",packed,new FeatureParser());
        assertTrue(source.text().startsWith("{\"helper.aether\":"));
        String invalid=FeatureParser.JSON.writeValueAsString(Map.of("main.aether","import \"bad.aether\"\n"+entry("ok"),"bad.aether","fn broken( {\n"));
        assertEquals("bad.aether",assertThrows(ValidationException.class,()->SourceFile.authored("example","aetherpack",invalid,new FeatureParser())).path());
        assertThrows(ValidationException.class,()->SourceFile.authored("example","aetherpack","{\"../bad.aether\":\"x\"}",new FeatureParser()));
    }
}
