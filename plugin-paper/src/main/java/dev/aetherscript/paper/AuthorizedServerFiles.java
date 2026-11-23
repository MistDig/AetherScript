package dev.aetherscript.paper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.*;

/** Filesystem boundary only. Mapping and approve/rollback methods are owner-console operations. */
final class AuthorizedServerFiles {
    enum Format { JSON, YAML }
    record View(String id, String hash, Map<String,Object> values) {}
    record Change(String token, String connection, String target, String kind, String beforeHash, String afterHash, Map<String,Object> details) {}
    record Applied(String target, String hash, String rollbackToken, boolean restartRequired) {}
    private record Target(Path path, Format format, Set<String> keys, String pluginName) {}
    private record Pending(Change change, byte[] before, byte[] after, Instant expires) {}
    private record Backup(String target, String relativePath, String expectedHash, String beforeHash) {}
    private record SavedTarget(String id, String relativePath, Format format, Set<String> keys, String pluginName) {}
    private record Saved(List<SavedTarget> targets, Map<String, Backup> backups) {}
    private static final int MAX_CONFIG=65_536, MAX_JAR=32*1024*1024, MAX_STATE=65536, MAX_VALUES=8192;
    private static final Pattern SENSITIVE=Pattern.compile("(?i).*(secret|password|passwd|token|credential|private.?key|api.?key|access.?key|signing.?key|webhook|bearer|authorization|session|connection|grant).*");
    private static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final Path root, staging, inbox, manifest;
    private final Map<String,Target> targets=new LinkedHashMap<>();
    private final Map<String,Pending> pending=new HashMap<>();
    private final Map<String,Backup> backups=new HashMap<>();
    AuthorizedServerFiles(Path pluginsRoot, Path privateStaging) throws IOException {
        root=pluginsRoot.toAbsolutePath().normalize(); staging=privateStaging.toAbsolutePath().normalize(); inbox=staging.resolve("inbox");
        manifest=staging.resolve("targets.json");
        safe(root); safe(inbox);
        Files.createDirectories(inbox); safe(inbox);
        if (Files.exists(manifest,LinkOption.NOFOLLOW_LINKS)) {
            Saved saved=JSON.readValue(read(manifest,MAX_STATE),Saved.class);
            if (saved.targets().size()>64 || saved.backups().size()>64) throw invalid("FILE_MANIFEST_LIMIT");
            for (SavedTarget entry:saved.targets()) {
                if (entry.format()==null) registerJar(entry.id(),entry.relativePath(),entry.pluginName());
                else registerConfig(entry.id(),entry.relativePath(),entry.format(),entry.keys());
            }
            for (var entry:saved.backups().entrySet()) {
                UUID.fromString(entry.getKey());
                Backup backup=entry.getValue(); Target target=target(backup.target());
                if (!relative(target).equals(backup.relativePath()) || !backup.expectedHash().matches("[0-9a-f]{64}")
                    || !backup.beforeHash().matches("[0-9a-f]{64}|ABSENT")) throw invalid("FILE_MANIFEST_INVALID");
                backups.put(entry.getKey(),backup);
            }
        }
    }
    synchronized void defineConfig(String id, String relative, Format format, Set<String> keys) throws IOException {
        registerConfig(id,relative,format,keys);
        try { persist(); } catch (IOException failure) { targets.remove(id); throw failure; }
    }
    private void registerConfig(String id,String relative,Format format,Set<String> keys) throws IOException {
        requireId(id); Objects.requireNonNull(format); Set<String> approved=Set.copyOf(keys);
        if(approved.isEmpty()||approved.size()>32||approved.stream().anyMatch(key->!key.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")||SENSITIVE.matcher(key).matches()))throw invalid("CONFIG_KEYS_DENIED");
        String suffix=format==Format.JSON?".json":".yml";
        if(!(relative.endsWith(suffix)||format==Format.YAML&&relative.endsWith(".yaml"))||SENSITIVE.matcher(relative).matches())throw invalid("CONFIG_PATH_DENIED");
        Path path=resolve(relative);
        if (path.getNameCount()-root.getNameCount()<2) throw invalid("CONFIG_MUST_BE_INSIDE_PLUGIN_DIRECTORY");
        define(id,new Target(path,format,approved,null));
    }
    synchronized void defineJar(String id,String relative,String pluginName) throws IOException {
        registerJar(id,relative,pluginName);
        try { persist(); } catch (IOException failure) { targets.remove(id); throw failure; }
    }
    private void registerJar(String id,String relative,String pluginName) throws IOException {
        requireId(id);
        if (pluginName.equalsIgnoreCase("AetherScript") || relative.toLowerCase(Locale.ROOT).startsWith("aetherscript")) throw invalid("AETHERSCRIPT_TARGET_DENIED");
        if(!relative.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.jar")||!pluginName.matches("[A-Za-z0-9_-]{1,64}"))throw invalid("JAR_TARGET_DENIED");
        define(id,new Target(resolve(relative),null,Set.of(),pluginName));
    }
    private void define(String id,Target target) {
        if(targets.containsKey(id)||targets.values().stream().anyMatch(value->value.path().equals(target.path())))throw invalid("TARGET_ALREADY_DEFINED");
        if(targets.size()>=64)throw invalid("TARGET_LIMIT"); targets.put(id,target);
    }
    synchronized void undefine(String id) throws IOException {
        if (backups.values().stream().anyMatch(backup->backup.target().equals(id))) throw invalid("TARGET_HAS_BACKUPS");
        Target target=target(id);
        targets.remove(id);
        try { persist(); } catch (IOException failure) { targets.put(id,target); throw failure; }
        pending.values().removeIf(value->value.change().target().equals(id));
    }
    private String relative(Target target) { return root.relativize(target.path()).toString().replace('\\','/'); }
    private void persist() throws IOException {
        List<SavedTarget> definitions=new ArrayList<>();
        targets.forEach((id,target)->definitions.add(new SavedTarget(id,relative(target),target.format(),target.keys(),target.pluginName())));
        byte[] state=JSON.writeValueAsBytes(new Saved(definitions,backups));
        if (state.length>MAX_STATE) throw new IOException("FILE_MANIFEST_LIMIT");
        atomic(manifest,state);
    }
    synchronized List<String> targetIds() { return List.copyOf(targets.keySet()); }
    synchronized View read(String id) throws IOException {
        Target target=config(id); byte[] bytes=read(target.path(),MAX_CONFIG); Map<String,Object> document=parse(bytes,target.format());
        Map<String,Object> fields=new LinkedHashMap<>();
        for(String key:target.keys())if(document.containsKey(key)&&scalar(document.get(key)))fields.put(key,document.get(key));
        if (JSON.writeValueAsBytes(fields).length>MAX_VALUES) throw invalid("CONFIG_VIEW_LIMIT");
        return new View(id,hash(bytes),Collections.unmodifiableMap(fields));
    }
    synchronized Change previewConfig(String connection,String id,String expectedHash,Map<String,Object> replacements) throws IOException {
        Target target=config(id); byte[] before=read(target.path(),MAX_CONFIG); cas(expectedHash,hash(before));
        if(replacements.isEmpty()||!target.keys().containsAll(replacements.keySet())||replacements.values().stream().anyMatch(value->!scalar(value)))throw invalid("CONFIG_PATCH_DENIED");
        Map<String,Object> document=parse(before,target.format());
        Map<String,Object> diff=new LinkedHashMap<>();
        for(var entry:replacements.entrySet()){
            if(document.containsKey(entry.getKey())&&!scalar(document.get(entry.getKey())))throw invalid("CONFIG_PATCH_DENIED");
            diff.put(entry.getKey(),Map.of("before",Objects.toString(document.get(entry.getKey()),"null"),"after",Objects.toString(entry.getValue(),"null")));
            document.put(entry.getKey(),entry.getValue());
        }
        byte[] after=target.format()==Format.JSON?JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(document):yaml().dump(document).getBytes(StandardCharsets.UTF_8);
        if(after.length>MAX_CONFIG || JSON.writeValueAsBytes(diff).length>MAX_VALUES)throw invalid("CONFIG_SIZE_LIMIT");
        return plan(connection,id,"config",before,after,diff);
    }
    synchronized Change stageJarInbox(String connection,String id,String filename,String sha256) throws IOException {
        if(!filename.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.jar"))throw invalid("INBOX_FILENAME_DENIED");
        return stageJar(connection,id,read(inbox.resolve(filename),MAX_JAR),sha256);
    }
    private Change stageJar(String connection,String id,byte[] bytes,String sha256) throws IOException {
        Target target=target(id);if(target.pluginName()==null)throw invalid("NOT_JAR_TARGET");
        if(bytes.length<1||bytes.length>MAX_JAR||!sha256.matches("[a-f0-9]{64}")||!MessageDigest.isEqual(sha256.getBytes(StandardCharsets.US_ASCII),hash(bytes).getBytes(StandardCharsets.US_ASCII)))throw invalid("JAR_HASH_OR_SIZE_INVALID");
        Map<String,Object> metadata=jarMetadata(bytes,target.pluginName());
        byte[] before=Files.exists(target.path(),LinkOption.NOFOLLOW_LINKS)?read(target.path(),MAX_JAR):null;
        if(before!=null)jarMetadata(before,target.pluginName());
        return plan(connection,id,"jar",before,bytes,metadata);
    }
    private Change plan(String connection,String id,String kind,byte[] before,byte[] after,Map<String,Object> details) {
        Objects.requireNonNull(connection);if(connection.isBlank()||connection.length()>128)throw invalid("CONNECTION_INVALID");
        pending.values().removeIf(value->value.expires().isBefore(Instant.now()));
        if(pending.size()>=32||pending.values().stream().mapToLong(value->(long)value.after().length+(value.before()==null?0:value.before().length)).sum()+after.length+(before==null?0:before.length)>64L*1024*1024)throw invalid("PENDING_LIMIT");
        String token=UUID.randomUUID().toString();
        Change change=new Change(token,connection,id,kind,before==null?"ABSENT":hash(before),hash(after),Map.copyOf(details));
        pending.put(token,new Pending(change,before,after,Instant.now().plusSeconds(900)));return change;
    }
    /** Owner console must display Change and explicitly choose this exact single-use token. */
    synchronized Applied approve(String token, String connection, java.util.function.Predicate<String> active) throws IOException {
        Pending value=pending.get(token);
        if(value==null||!value.change().connection().equals(connection)||!value.expires().isAfter(Instant.now()))throw invalid("APPROVAL_INVALID");
        pending.remove(token); // Approval attempts cannot be replayed, even after CAS failure.
        Target target=target(value.change().target());safe(target.path());
        String current=currentHash(target.path(),target.pluginName()==null?MAX_CONFIG:MAX_JAR);cas(value.change().beforeHash(),current);
        if(backups.size()>=64)throw invalid("BACKUP_LIMIT");
        String rollback=UUID.randomUUID().toString();Path backup=staging.resolve(rollback+".bak");
        if(value.before()!=null)atomic(backup,value.before());
        backups.put(rollback,new Backup(value.change().target(),relative(target),value.change().afterHash(),value.change().beforeHash()));
        try {
            persist();
            cas(value.change().beforeHash(),currentHash(target.path(),target.pluginName()==null?MAX_CONFIG:MAX_JAR));
            if (!active.test(connection)) throw invalid("CONNECTION_REVOKED");
            atomic(target.path(),value.after());
        } catch (IOException | RuntimeException failure) {
            backups.remove(rollback);
            try { persist(); Files.deleteIfExists(backup); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        return new Applied(value.change().target(),value.change().afterHash(),rollback,target.pluginName()!=null);
    }
    synchronized Applied rollback(String token) throws IOException {
        Backup backup=backups.get(token);if(backup==null)throw invalid("ROLLBACK_INVALID");
        Target target=target(backup.target());safe(target.path());cas(backup.expectedHash(),currentHash(target.path(),target.pluginName()==null?MAX_CONFIG:MAX_JAR));
        Path stored=staging.resolve(token+".bak");
        byte[] bytes=backup.beforeHash().equals("ABSENT")?null:read(stored,target.pluginName()==null?MAX_CONFIG:MAX_JAR);
        if (bytes!=null) cas(backup.beforeHash(),hash(bytes));
        try {
            if (bytes==null) Files.delete(target.path());
            else atomic(target.path(),bytes);
        } catch (FileSystemException blocked) {
            if (target.pluginName()!=null)
                throw invalid("JAR_ROLLBACK_REQUIRES_STOP: stop Paper before replacing a loaded JAR. The target and rollback receipt are unchanged.");
            throw blocked;
        }
        backups.remove(token);
        try { persist(); } catch (IOException failure) { backups.put(token,backup); throw failure; }
        Files.deleteIfExists(stored);
        return new Applied(backup.target(),bytes==null?"ABSENT":hash(bytes),"",target.pluginName()!=null);
    }
    synchronized void cancelConnection(String connection) { pending.values().removeIf(value->value.change().connection().equals(connection)); }
    synchronized void cancel(String token) { pending.remove(token); }
    synchronized List<Change> pending() { pending.values().removeIf(value->!value.expires().isAfter(Instant.now()));return pending.values().stream().map(Pending::change).toList(); }
    private Target target(String id) { Target target=targets.get(id);if(target==null)throw invalid("TARGET_NOT_APPROVED");return target; }
    private Target config(String id) { Target target=target(id);if(target.format()==null)throw invalid("NOT_CONFIG_TARGET");return target; }
    private Path resolve(String relative) throws IOException {
        Path relativePath=Path.of(relative);
        if (relativePath.isAbsolute() || relativePath.getNameCount()==0 || relativePath.getName(0).toString().equalsIgnoreCase("AetherScript")) throw invalid("TARGET_PATH_DENIED");
        for (Path segment:relativePath) if (segment.toString().equals("..") || segment.toString().equals(".")) throw invalid("TARGET_PATH_DENIED");
        Path candidate=root.resolve(relativePath).normalize();
        if(Path.of(relative).isAbsolute()||!candidate.startsWith(root)||candidate.equals(root)||candidate.startsWith(staging)||relative.contains(":"))throw invalid("TARGET_PATH_DENIED");safe(candidate);return candidate;
    }
    private static void safe(Path path) throws IOException {
        for(Path current=path.toAbsolutePath();current!=null;current=current.getParent())if(Files.isSymbolicLink(current)||Files.exists(current,LinkOption.NOFOLLOW_LINKS)&&Files.readAttributes(current,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).isOther())throw invalid("SYMLINK_OR_SPECIAL_PATH_DENIED");
    }
    private static byte[] read(Path path,int limit) throws IOException {
        safe(path);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>limit)throw invalid("FILE_SIZE_OR_TYPE_DENIED");
        try(InputStream input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){byte[] bytes=input.readNBytes(limit+1);if(bytes.length>limit)throw invalid("FILE_SIZE_LIMIT");return bytes;}
    }
    private static String currentHash(Path path,int limit) throws IOException { return Files.exists(path,LinkOption.NOFOLLOW_LINKS)?hash(read(path,limit)):"ABSENT"; }
    private static void atomic(Path path,byte[] bytes) throws IOException {
        safe(path);if(!Files.isDirectory(path.getParent()))throw invalid("TARGET_PARENT_MISSING");
        Path temporary=Files.createTempFile(path.getParent(),".aether-",".tmp");
        try {Files.write(temporary,bytes);safe(path);Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temporary);}
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> parse(byte[] bytes,Format format) throws IOException {
        Object value;
        try{value=format==Format.JSON?JSON.readValue(bytes,Object.class):yaml().load(new String(bytes,StandardCharsets.UTF_8));}catch(RuntimeException invalid){throw invalid("CONFIG_PARSE_INVALID");}
        if(!(value instanceof Map<?,?> map)||map.keySet().stream().anyMatch(key->!(key instanceof String)))throw invalid("CONFIG_ROOT_INVALID");
        return new LinkedHashMap<>((Map<String,Object>)value);
    }
    private static Yaml yaml() { LoaderOptions options=new LoaderOptions();options.setAllowDuplicateKeys(false);options.setMaxAliasesForCollections(0);options.setCodePointLimit(MAX_CONFIG);options.setNestingDepthLimit(16);return new Yaml(new SafeConstructor(options)); }
    private static boolean scalar(Object value) {return value==null||value instanceof String text&&text.length()<=2048||value instanceof Boolean||value instanceof Number number&&Double.isFinite(number.doubleValue());}
    private static Map<String,Object> jarMetadata(byte[] bytes,String expectedName) throws IOException {
        byte[] metadata=null;String descriptor=null;Set<String> names=new HashSet<>();int total=0,count=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){
            if(++count>4096||entry.getName().startsWith("/")||entry.getName().contains("..")||entry.getName().contains("\\")||!names.add(entry.getName()))throw invalid("JAR_CONTENT_DENIED");
            byte[] content=zip.readNBytes(MAX_JAR+1);total+=content.length;if(content.length>MAX_JAR||total>64*1024*1024)throw invalid("JAR_EXPANSION_LIMIT");
            if(entry.getName().equals("plugin.yml")||entry.getName().equals("paper-plugin.yml")){if(metadata!=null||content.length>MAX_CONFIG)throw invalid("JAR_METADATA_INVALID");metadata=content;descriptor=entry.getName();}
        }}
        if(metadata==null)throw invalid("JAR_PLUGIN_METADATA_MISSING");
        Map<String,Object> data=parse(metadata,Format.YAML);
        String name=Objects.toString(data.get("name"),""),version=Objects.toString(data.get("version"),""),main=Objects.toString(data.get("main"),"");
        if(!expectedName.equals(name)||!version.matches("[A-Za-z0-9][A-Za-z0-9._+ -]{0,63}")||!main.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")||!names.contains(main.replace('.','/')+".class"))throw invalid("JAR_PLUGIN_IDENTITY_MISMATCH");
        Object rawDependencies=data.get("depend");
        if (rawDependencies==null && data.get("dependencies") instanceof Map<?,?> dependencies
            && dependencies.get("server") instanceof Map<?,?> server) rawDependencies=server.keySet();
        List<String> dependencies=new ArrayList<>();
        if (rawDependencies instanceof Collection<?> namesToCheck) for (Object dependency:namesToCheck) {
            if (!(dependency instanceof String text) || !text.matches("[A-Za-z0-9 _-]{1,64}") || dependencies.size()>=32)
                throw invalid("JAR_DEPENDENCIES_INVALID");
            dependencies.add(text);
        }
        return Map.of("name",name,"version",version,"main",main,"descriptor",descriptor,"dependencies",dependencies,"restartRequired",true);
    }
    private static String hash(byte[] bytes) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    private static void cas(String expected,String actual) {if(!Objects.equals(expected,actual))throw invalid("FILE_HASH_CONFLICT");}
    private static void requireId(String id) {if(!id.matches("[a-z][a-z0-9-]{0,63}"))throw invalid("TARGET_ID_INVALID");}
    private static IllegalArgumentException invalid(String code) {return new IllegalArgumentException(code);}
}
