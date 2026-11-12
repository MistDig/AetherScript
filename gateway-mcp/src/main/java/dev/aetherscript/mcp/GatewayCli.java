package dev.aetherscript.mcp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Pairing is an explicit owner process operation, never an MCP tool. */
final class GatewayCli {
    static int run(String[] args, InputStream input, OutputStream output, PrintStream errors) {
        try {
            if (args.length == 0) { new Main().serve(input, output); return 0; }
            if (args.length == 1 && args[0].equals("--help")) { usage(new PrintStream(output)); return 0; }
            String operation = args[0];
            Map<String, String> options = options(args);
            if (operation.equals("pair") && options.keySet().equals(java.util.Set.of("--endpoint", "--profile"))) {
                URI endpoint = BridgeClient.validateEndpoint(URI.create(options.get("--endpoint")));
                String code = readCode(input, errors);
                PairingProfile profile = BridgeClient.pair(endpoint, code);
                ProfileStore.save(Path.of(options.get("--profile")), profile);
                output.write("Paired read-only server status. Profile saved.\n".getBytes(StandardCharsets.UTF_8)); output.flush(); return 0;
            }
            if (operation.equals("serve") && options.keySet().equals(java.util.Set.of("--profile"))) {
                PairingProfile profile = ProfileStore.load(Path.of(options.get("--profile")));
                new Main(new BridgeClient(profile)).serve(input, output); return 0;
            }
            if (operation.equals("forget") && options.keySet().equals(java.util.Set.of("--profile"))) {
                ProfileStore.forget(Path.of(options.get("--profile")));
                output.write("Local pairing profile removed. Use /ais unpair to revoke the server session.\n".getBytes(StandardCharsets.UTF_8)); output.flush(); return 0;
            }
            usage(errors); return 2;
        } catch (IOException | IllegalArgumentException | IllegalStateException error) {
            errors.println("Pairing or connection setup failed. Check the endpoint, fresh console code, and owner-only profile permissions.");
            return 2;
        }
    }
    private static Map<String, String> options(String[] args) {
        Map<String, String> result = new HashMap<>();
        if (args.length % 2 != 1) throw new IllegalArgumentException();
        for (int index = 1; index < args.length; index += 2)
            if (!java.util.Set.of("--endpoint", "--profile").contains(args[index]) || args[index + 1].isBlank()
                || result.putIfAbsent(args[index], args[index + 1]) != null) throw new IllegalArgumentException();
        return result;
    }
    private static String readCode(InputStream input, PrintStream errors) throws IOException {
        if (System.console() != null && input == System.in) {
            char[] code = System.console().readPassword("Pairing code: ");
            if (code == null) throw new IOException("Pairing code required");
            try { return new String(code).strip(); } finally { Arrays.fill(code, '\0'); }
        }
        errors.print("Pairing code: "); errors.flush();
        ByteArrayOutputStream code = new ByteArrayOutputStream(); int next;
        while ((next = input.read()) != -1 && next != '\n') {
            if (code.size() >= 128) throw new IOException("Pairing code size limit");
            code.write(next);
        }
        return code.toString(StandardCharsets.UTF_8).strip();
    }
    private static void usage(PrintStream output) {
        output.println("AetherScript read-only MCP gateway");
        output.println("  No arguments: offline source validation over stdio");
        output.println("  pair --endpoint http://127.0.0.1:PORT --profile PATH");
        output.println("    Reads the code from a hidden terminal prompt or one stdin line.");
        output.println("  serve --profile PATH");
        output.println("    Starts stdio MCP with owner-paired local server status.");
        output.println("  forget --profile PATH");
        output.println("    Removes the local credential. Server /ais unpair revokes access.");
    }
}
