package dev.aetherscript.core;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded, pure preparation-time language frontend. Values never contain host objects. */
public final class TextFrontend {
    public static final int MAX_NODES = 2048;
    public static final int MAX_STATEMENTS = 256;
    public static final int MAX_DEPTH = 32;
    public static final int MAX_STEPS = 8192;

    public CompiledFeature parse(byte[] source) {
        if (source.length > FeatureParser.MAX_BYTES) throw new ValidationException("$", "Source exceeds 65536 bytes", 1, 1);
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(source)).toString();
        } catch (CharacterCodingException error) {
            throw new ValidationException("$", "Invalid UTF-8 encoding. Save the source as UTF-8.", 1, 1);
        }
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        return new Parser(new Lexer(text).scan()).compile(source);
    }

    private enum Kind { NAME, NUMBER, STRING, SYMBOL, NEWLINE, END }
    private enum Type { INTEGER, TEXT, BOOLEAN }
    private record Token(Kind kind, String text, int line, int column) {
        ValidationException error(String reason) { return new ValidationException("$", reason, line, column); }
    }
    private record Value(Type type, Object value) {
        long integer() { return (Long) value; }
        boolean bool() { return (Boolean) value; }
        String text() { return (String) value; }
    }
    private record Binding(String name, Type type, Token declaration) {}

    private static final class Lexer {
        private final String input;
        private final List<Token> tokens = new ArrayList<>();
        private int index, line = 1, column = 1;
        Lexer(String input) { this.input = input; }
        List<Token> scan() {
            while (index < input.length()) {
                char c = input.charAt(index);
                int startLine = line, startColumn = column;
                if (c == ' ' || c == '\t' || c == '\r') { advance(); continue; }
                if (c == '\n') { advance(); add(Kind.NEWLINE, "\n", startLine, startColumn); continue; }
                if (c == '/' && peek(1) == '/') {
                    while (index < input.length() && input.charAt(index) != '\n') advance();
                    continue;
                }
                if (Character.isLetter(c) && c < 128 || c == '_') {
                    int start = index;
                    do { advance(); } while (index < input.length() && isNamePart(input.charAt(index)));
                    add(Kind.NAME, input.substring(start, index), startLine, startColumn);
                } else if (c >= '0' && c <= '9') {
                    int start = index;
                    do { advance(); } while (index < input.length() && Character.isDigit(input.charAt(index)));
                    add(Kind.NUMBER, input.substring(start, index), startLine, startColumn);
                } else if (c == '"') {
                    advance(); StringBuilder value = new StringBuilder(); boolean closed = false;
                    while (index < input.length()) {
                        char next = input.charAt(index); advance();
                        if (next == '"') { closed = true; break; }
                        if (next == '\n' || next == '\r') throw error(startLine, startColumn, "Text cannot cross a source line. Use a supported escape.");
                        if (next == '\\') {
                            if (index == input.length()) throw error(startLine, startColumn, "Unclosed text literal");
                            char escape = input.charAt(index); advance();
                            next = switch (escape) {
                                case '"' -> '"'; case '\\' -> '\\'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                                default -> throw error(line, column - 1, "Unsupported text escape. Use escaped quotes, backslash, n, r or t; write Unicode characters directly.");
                            };
                        }
                        value.append(next);
                        if (value.length() > FeatureParser.MAX_TEXT) throw error(startLine, startColumn, "Text exceeds 2048 UTF-16 units");
                    }
                    if (!closed) throw error(startLine, startColumn, "Unclosed text literal. Add a closing double quote.");
                    add(Kind.STRING, value.toString(), startLine, startColumn);
                } else {
                    String operator = Character.toString(c);
                    if (index + 1 < input.length() && List.of("==", "!=", "<=", ">=", "&&", "||").contains(input.substring(index, index + 2))) {
                        operator = input.substring(index, index + 2); advance();
                    } else if ("{}()+-*/%<>=!".indexOf(c) < 0) {
                        throw error(startLine, startColumn, "Unexpected character. Use the documented operators and double-quoted text.");
                    }
                    advance(); add(Kind.SYMBOL, operator, startLine, startColumn);
                }
            }
            add(Kind.END, "", line, column);
            return List.copyOf(tokens);
        }
        private boolean isNamePart(char c) { return c < 128 && (Character.isLetterOrDigit(c) || c == '_'); }
        private char peek(int ahead) { return index + ahead < input.length() ? input.charAt(index + ahead) : '\0'; }
        private void advance() {
            char c = input.charAt(index++);
            if (c == '\n') { line++; column = 1; } else column++;
        }
        private void add(Kind kind, String text, int line, int column) {
            if (tokens.size() >= 8192) throw error(line, column, "Token limit exceeded (8192)");
            tokens.add(new Token(kind, text, line, column));
        }
        private ValidationException error(int line, int column, String reason) { return new ValidationException("$", reason, line, column); }
    }

    private interface Expression {
        Type type();
        int depth();
        Value evaluate(Execution execution);
    }
    private record Literal(Value value) implements Expression {
        public Type type() { return value.type(); }
        public int depth() { return 1; }
        public Value evaluate(Execution execution) { execution.step(); return value; }
    }
    private record Name(Binding binding, Token token) implements Expression {
        public Type type() { return binding.type(); }
        public int depth() { return 1; }
        public Value evaluate(Execution execution) { execution.step(); return execution.values.get(binding); }
    }
    private record Unary(Token operator, Expression operand, Type type) implements Expression {
        public int depth() { return operand.depth() + 1; }
        public Value evaluate(Execution execution) {
            execution.step(); Value value = operand.evaluate(execution);
            if (operator.text().equals("!")) return new Value(Type.BOOLEAN, !value.bool());
            try { return new Value(Type.INTEGER, Math.negateExact(value.integer())); }
            catch (ArithmeticException error) { throw operator.error("Integer overflow. Keep arithmetic within signed 64-bit bounds."); }
        }
    }
    private record Binary(Expression left, Token operator, Expression right, Type type) implements Expression {
        public int depth() { return Math.max(left.depth(), right.depth()) + 1; }
        public Value evaluate(Execution execution) {
            execution.step(); Value a = left.evaluate(execution);
            if (operator.text().equals("&&") && !a.bool()) return new Value(Type.BOOLEAN, false);
            if (operator.text().equals("||") && a.bool()) return new Value(Type.BOOLEAN, true);
            Value b = right.evaluate(execution);
            try {
                return switch (operator.text()) {
                    case "+" -> type == Type.TEXT ? textValue(a.text() + b.text(), operator) : new Value(type, Math.addExact(a.integer(), b.integer()));
                    case "-" -> new Value(type, Math.subtractExact(a.integer(), b.integer()));
                    case "*" -> new Value(type, Math.multiplyExact(a.integer(), b.integer()));
                    case "/" -> {
                        if (b.integer() == 0) throw operator.error("Division by zero. Use a nonzero divisor.");
                        if (a.integer() == Long.MIN_VALUE && b.integer() == -1) throw new ArithmeticException();
                        yield new Value(type, a.integer() / b.integer());
                    }
                    case "%" -> {
                        if (b.integer() == 0) throw operator.error("Remainder by zero. Use a nonzero divisor.");
                        yield new Value(type, a.integer() % b.integer());
                    }
                    case "<" -> new Value(type, a.integer() < b.integer());
                    case "<=" -> new Value(type, a.integer() <= b.integer());
                    case ">" -> new Value(type, a.integer() > b.integer());
                    case ">=" -> new Value(type, a.integer() >= b.integer());
                    case "==" -> new Value(type, a.value().equals(b.value()));
                    case "!=" -> new Value(type, !a.value().equals(b.value()));
                    case "&&" -> new Value(type, a.bool() && b.bool());
                    case "||" -> new Value(type, a.bool() || b.bool());
                    default -> throw new AssertionError(operator.text());
                };
            } catch (ArithmeticException error) { throw operator.error("Integer overflow. Keep arithmetic within signed 64-bit bounds."); }
        }
    }
    private record AsText(Expression operand, Token token) implements Expression {
        public Type type() { return Type.TEXT; }
        public int depth() { return operand.depth() + 1; }
        public Value evaluate(Execution execution) { execution.step(); return textValue(operand.evaluate(execution).value().toString(), token); }
    }
    private static Value textValue(String text, Token token) {
        if (text.length() > FeatureParser.MAX_TEXT) throw token.error("Text exceeds 2048 UTF-16 units");
        return new Value(Type.TEXT, text);
    }

    private interface Statement { void prepare(Execution execution); }
    private record Let(Binding binding, Expression initializer) implements Statement {
        public void prepare(Execution execution) { execution.step(); execution.values.put(binding, initializer.evaluate(execution)); }
    }
    private record Send(Expression expression, Token token) implements Statement {
        public void prepare(Execution execution) {
            execution.step(); String text = expression.evaluate(execution).text();
            if (text.isEmpty() || text.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0xA7))
                throw token.error("send requires nonempty plain text without controls or legacy formatting");
            if (execution.messages.size() >= FeatureParser.MAX_ACTIONS) throw token.error("A feature may emit at most 32 messages");
            execution.messages.add(new Feature.MessageAction(text));
        }
    }
    private record Condition(Expression condition, List<Statement> yes, List<Statement> no) implements Statement {
        public void prepare(Execution execution) {
            execution.step();
            for (Statement statement : condition.evaluate(execution).bool() ? yes : no) statement.prepare(execution);
        }
    }
    private static final class Execution {
        final Map<Binding, Value> values = new HashMap<>();
        final List<Feature.MessageAction> messages = new ArrayList<>();
        private int steps;
        void step() {
            if (++steps > MAX_STEPS) throw new ValidationException("$", "Preparation budget exceeded (8192 steps)", 1, 1);
        }
    }

    private static final class Parser {
        private final List<Token> tokens;
        private final Deque<Map<String, Binding>> scopes = new ArrayDeque<>();
        private int index, nodes, statements, blockDepth, expressionDepth;
        Parser(List<Token> tokens) { this.tokens = tokens; }
        CompiledFeature compile(byte[] original) {
            newlines(); expect("feature"); String id = logicalId();
            expect("version"); Token versionToken = require(Kind.STRING, "Feature version must be double-quoted text");
            bounded(versionToken, 64, "Feature version"); lineEnd();
            expect("command"); Token commandToken = peek(); String name = logicalId();
            expect("permission"); Token permission = require(Kind.STRING, "Permission must be explicit double-quoted text");
            if (permission.text().length() > 128 || !permission.text().matches("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+"))
                throw permission.error("Permission must be an explicit dotted node without wildcards, at most 128 characters");
            expect("sender"); Token sender = require(Kind.NAME, "Expected sender any or player");
            if (!List.of("any", "player").contains(sender.text())) throw sender.error("Sender must be any or player");
            List<Statement> body = block(); newlines();
            if (peek().kind() != Kind.END) throw peek().error("Expected end of file. This slice supports one command per feature.");
            Execution execution = new Execution();
            for (Statement statement : body) statement.prepare(execution);
            if (execution.messages.isEmpty()) throw commandToken.error("The selected branch must send at least one message");
            Feature feature = new Feature(1, id, versionToken.text(), List.of("runtime.message.send"),
                new Feature.Command(name, permission.text(), sender.text(), execution.messages));
            try { return new CompiledFeature(feature, FeatureParser.hash(original), FeatureParser.hash(FeatureParser.JSON.writeValueAsBytes(feature))); }
            catch (java.io.IOException error) { throw new AssertionError(error); }
        }
        private List<Statement> block() {
            expect("{");
            if (++blockDepth > MAX_DEPTH) throw previous().error("Block nesting exceeds 32");
            scopes.push(new HashMap<>()); newlines(); List<Statement> result = new ArrayList<>();
            while (!matches("}")) {
                if (peek().kind() == Kind.END) throw peek().error("Unclosed block. Add a closing brace.");
                if (++statements > MAX_STATEMENTS) throw peek().error("Statement limit exceeded (256)");
                result.add(statement()); newlines();
            }
            take(); scopes.pop(); blockDepth--; return List.copyOf(result);
        }
        private Statement statement() {
            Token start = take();
            if (start.text().equals("let")) {
                Token name = require(Kind.NAME, "Expected a local variable name after let");
                if (List.of("true", "false", "text", "let", "send", "if", "else").contains(name.text())) throw name.error("This keyword cannot be a variable name");
                if (scopes.peek().containsKey(name.text())) throw name.error("Variable is already declared in this block. Use a different name.");
                expect("="); Expression initializer = expression(); lineEnd();
                Binding binding = new Binding(name.text(), initializer.type(), name); scopes.peek().put(name.text(), binding);
                return new Let(binding, initializer);
            }
            if (start.text().equals("send")) {
                Expression value = expression(); requireType(value, Type.TEXT, start, "send expects text; use text(value) for an integer or boolean"); lineEnd();
                return new Send(value, start);
            }
            if (start.text().equals("if")) {
                Expression condition = expression(); requireType(condition, Type.BOOLEAN, start, "if expects a boolean condition");
                List<Statement> yes = block(); newlines(); List<Statement> no = List.of();
                if (matches("else")) { take(); no = block(); }
                return new Condition(condition, yes, no);
            }
            throw start.error("Expected let, send or if. Assignment and host calls are unavailable in this preparation-time slice.");
        }
        private Expression expression() {
            if (++expressionDepth > MAX_DEPTH) throw peek().error("Expression nesting exceeds 32");
            Expression result = binary(1); expressionDepth--; return result;
        }
        private Expression binary(int minimum) {
            Expression left = unary();
            while (precedence(peek().text()) >= minimum) {
                Token operator = take(); int priority = precedence(operator.text());
                Expression right = binary(priority + 1); Type type;
                switch (operator.text()) {
                    case "&&", "||" -> {
                        requireType(left, Type.BOOLEAN, operator, "Boolean operators require booleans");
                        requireType(right, Type.BOOLEAN, operator, "Boolean operators require booleans"); type = Type.BOOLEAN;
                    }
                    case "==", "!=" -> {
                        if (left.type() != right.type()) throw operator.error("Equality requires values of the same type"); type = Type.BOOLEAN;
                    }
                    case "+" -> {
                        if (left.type() != right.type() || left.type() == Type.BOOLEAN) throw operator.error("+ requires two integers or two text values. Use text(value) explicitly.");
                        type = left.type();
                    }
                    default -> {
                        requireType(left, Type.INTEGER, operator, "This operator requires integers");
                        requireType(right, Type.INTEGER, operator, "This operator requires integers");
                        type = List.of("<", "<=", ">", ">=").contains(operator.text()) ? Type.BOOLEAN : Type.INTEGER;
                    }
                }
                left = node(new Binary(left, operator, right, type), operator);
            }
            return left;
        }
        private Expression unary() {
            if (matches("!") || matches("-")) {
                Token operator = take();
                if (operator.text().equals("-") && peek().kind() == Kind.NUMBER) {
                    Token number = take();
                    try { return node(new Literal(new Value(Type.INTEGER, Long.parseLong("-" + number.text()))), number); }
                    catch (NumberFormatException error) { throw number.error("Integer literal exceeds signed 64-bit bounds"); }
                }
                if (++expressionDepth > MAX_DEPTH) throw operator.error("Expression nesting exceeds 32");
                Expression operand = unary(); expressionDepth--;
                Type type = operator.text().equals("!") ? Type.BOOLEAN : Type.INTEGER;
                requireType(operand, type, operator, "Unary operator has the wrong operand type");
                return node(new Unary(operator, operand, type), operator);
            }
            Token token = take();
            if (token.kind() == Kind.STRING) return node(new Literal(new Value(Type.TEXT, token.text())), token);
            if (token.kind() == Kind.NUMBER) {
                try { return node(new Literal(new Value(Type.INTEGER, Long.parseLong(token.text()))), token); }
                catch (NumberFormatException error) { throw token.error("Integer literal exceeds signed 64-bit bounds"); }
            }
            if (token.text().equals("true") || token.text().equals("false")) return node(new Literal(new Value(Type.BOOLEAN, Boolean.parseBoolean(token.text()))), token);
            if (token.text().equals("(")) { Expression result = expression(); expect(")"); return result; }
            if (token.text().equals("text")) {
                expect("("); Expression value = expression(); expect(")"); return node(new AsText(value, token), token);
            }
            if (token.kind() == Kind.NAME) {
                for (Map<String, Binding> scope : scopes) {
                    Binding binding = scope.get(token.text());
                    if (binding != null) return node(new Name(binding, token), token);
                }
                throw token.error("Unknown variable '" + token.text() + "'. Declare it with let before use.");
            }
            throw token.error("Expected an integer, boolean, quoted text, variable or parenthesized expression");
        }
        private Expression node(Expression value, Token token) {
            if (++nodes > MAX_NODES) throw token.error("Expression node limit exceeded (2048)");
            if (value.depth() > MAX_DEPTH) throw token.error("Expression depth exceeds 32. Split the expression into local bindings.");
            return value;
        }
        private int precedence(String operator) {
            return switch (operator) {
                case "||" -> 1; case "&&" -> 2; case "==", "!=" -> 3;
                case "<", "<=", ">", ">=" -> 4; case "+", "-" -> 5; case "*", "/", "%" -> 6; default -> 0;
            };
        }
        private String logicalId() {
            Token start = require(Kind.NAME, "Expected a lowercase logical ID"); StringBuilder id = new StringBuilder(start.text());
            while (matches("-")) {
                take(); Token part = take();
                if (part.kind() != Kind.NAME && part.kind() != Kind.NUMBER) throw part.error("Expected an ID segment after hyphen");
                id.append('-').append(part.text());
                // A logical ID permits digit-leading segments, while expression identifiers cannot start with a digit.
                if (part.kind() == Kind.NUMBER && peek().kind() == Kind.NAME && peek().line() == part.line()
                    && peek().column() == part.column() + part.text().length()) id.append(take().text());
            }
            try { FeatureParser.requireId(id.toString()); }
            catch (ValidationException error) { throw start.error(error.reason()); }
            return id.toString();
        }
        private void bounded(Token token, int max, String field) {
            if (token.text().isEmpty() || token.text().length() > max) throw token.error(field + " must contain 1.." + max + " characters");
        }
        private void requireType(Expression value, Type type, Token token, String reason) { if (value.type() != type) throw token.error(reason); }
        private void lineEnd() {
            if (peek().kind() == Kind.NEWLINE) { newlines(); return; }
            if (matches("}") || peek().kind() == Kind.END) return;
            throw peek().error("Expected the end of this statement. Put the next statement on a new line.");
        }
        private void newlines() { while (peek().kind() == Kind.NEWLINE) take(); }
        private void expect(String text) { Token token = take(); if (!token.text().equals(text)) throw token.error("Expected '" + text + "'"); }
        private Token require(Kind kind, String reason) { Token token = take(); if (token.kind() != kind) throw token.error(reason); return token; }
        private boolean matches(String text) { return peek().text().equals(text); }
        private Token previous() { return tokens.get(Math.max(0, index - 1)); }
        private Token peek() { return tokens.get(Math.min(index, tokens.size() - 1)); }
        private Token take() { Token token = peek(); if (index < tokens.size() - 1) index++; return token; }
    }
}
