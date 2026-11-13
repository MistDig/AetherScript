package dev.aetherscript.core;

import java.util.ArrayList;
import java.util.List;

/** Shared transport-independent argument limits. Values remain data, never source text. */
public final class InvocationArguments {
    public static final int MAX_COUNT = 16;
    public static final int MAX_TEXT = 2048;
    public static final int MAX_COMMAND_LENGTH = MAX_COUNT * (MAX_TEXT * 2 + 3);
    private InvocationArguments() {}
    /** Reconstructs Bukkit tokens, retaining quoted spaces and escaped quotes/backslashes. */
    public static List<String> fromCommandTokens(String[] tokens) {
        if (tokens == null || tokens.length > MAX_COMMAND_LENGTH)
            throw new ValidationException("$.arguments", "Command arguments exceed the input limit");
        long length = Math.max(0, tokens.length - 1);
        for (String token : tokens) {
            if (token == null) throw new ValidationException("$.arguments", "Command arguments must be text");
            length += token.length();
            if (length > MAX_COMMAND_LENGTH) throw new ValidationException("$.arguments", "Command arguments exceed the input limit");
        }
        String input = String.join(" ", tokens);
        List<String> result = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false, started = false;
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (current == '\\' && index + 1 < input.length() && (input.charAt(index + 1) == '"' || input.charAt(index + 1) == '\\')) {
                value.append(input.charAt(++index)); started = true;
            } else if (current == '"') {
                quoted = !quoted; started = true;
            } else if (current == ' ' && !quoted) {
                if (started) { result.add(value.toString()); value.setLength(0); started = false; }
            } else {
                value.append(current); started = true;
            }
            if (value.length() > MAX_TEXT || result.size() > MAX_COUNT)
                throw new ValidationException("$.arguments", "Expected at most 16 arguments of at most 2048 UTF-16 units");
        }
        if (quoted) throw new ValidationException("$.arguments", "Unclosed quoted argument. Add a closing double quote.");
        if (started) result.add(value.toString());
        return checked(result);
    }
    public static List<String> checked(List<String> arguments) {
        if (arguments == null || arguments.size() > MAX_COUNT)
            throw new ValidationException("$.arguments", "Expected at most 16 invocation arguments");
        for (int index = 0; index < arguments.size(); index++) {
            String value = arguments.get(index);
            if (value == null || value.length() > MAX_TEXT || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0xA7))
                throw new ValidationException("$.arguments[" + index + "]", "Arguments must be plain text of at most 2048 UTF-16 units");
        }
        return List.copyOf(arguments);
    }
}
