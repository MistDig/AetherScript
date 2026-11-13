package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InvocationArgumentsTest {
    @Test void quotedTextAndTypedValuesKeepTheirPositionalBoundaries() {
        assertEquals(List.of("Alex Smith", "3", "false"), InvocationArguments.fromCommandTokens(new String[]{"\"Alex", "Smith\"", "3", "false"}));
        assertEquals(List.of("Alex", "3"), InvocationArguments.fromCommandTokens(new String[]{"Alex", "3"}));
        assertEquals(List.of("", "two  spaces"), InvocationArguments.fromCommandTokens(new String[]{"\"\"", "\"two", "", "spaces\""}));
        assertEquals(List.of(), InvocationArguments.fromCommandTokens(new String[0]));
    }
    @Test void escapedQuotesAndBackslashesRemainText() {
        assertEquals(List.of("Alex \"Cloud\" Smith", "C:\\temp"), InvocationArguments.fromCommandTokens(new String[]{"\"Alex", "\\\"Cloud\\\"", "Smith\"", "C:\\\\temp"}));
        assertEquals(List.of("literal\\n", "tail\\"), InvocationArguments.fromCommandTokens(new String[]{"literal\\n", "tail\\"}));
    }
    @Test void argumentCountIsCheckedAfterQuotedWordsAreGrouped() {
        String[] words = new String[20]; Arrays.fill(words, "word"); words[0] = "\"word"; words[19] = "word\"";
        assertEquals(List.of(String.join(" ", java.util.Collections.nCopies(20, "word"))), InvocationArguments.fromCommandTokens(words));
        String[] many = new String[17]; Arrays.fill(many, "one");
        assertThrows(ValidationException.class, () -> InvocationArguments.fromCommandTokens(many));
    }
    @Test void unclosedQuotesAndExcessiveInputFailBeforeInvocation() {
        assertTrue(assertThrows(ValidationException.class, () -> InvocationArguments.fromCommandTokens(new String[]{"\"Alex", "Smith"})).reason().contains("Unclosed"));
        assertThrows(ValidationException.class, () -> InvocationArguments.fromCommandTokens(new String[]{"x".repeat(2049)}));
        assertThrows(ValidationException.class, () -> InvocationArguments.fromCommandTokens(new String[]{"x".repeat(InvocationArguments.MAX_COMMAND_LENGTH + 1)}));
        assertThrows(ValidationException.class, () -> InvocationArguments.fromCommandTokens(new String[]{"unsafe\ntext"}));
    }
}
