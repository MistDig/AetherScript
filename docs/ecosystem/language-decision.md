# Initial textual-language decision

Decision date is 11 November 2026. This document records the first M1 slice agreed with the frontend implementation. Its six matrix rows are verified by the 0.0.4 pure-language and lifecycle tests, with packaged offline authoring coverage. The final binary passed 28 real Paper checks across five boots, including 100 distinct textual reloads.

## Frontend and execution boundary

A `.aether` source uses `TextFrontend.parse(byte[])` to produce the existing `CompiledFeature`. Its selected pure expressions evaluate during preparation, and its output becomes the same immutable `Feature.MessageAction` sequence used by YAML/JSON. File discovery, validation, history, restart recovery and offline MCP should enter through the shared parser path.

A local value in this slice exists during preparation. It cannot observe an invoking player or change between invocations. The current runtime executes precomputed messages. Invocation variables, persistent state, functions, loops, server references and continuations are later M1/M2 capabilities with their own IR and lifetime rules.

## First grammar

```text
feature hello-ai version "1.0.0"
command helloai permission "aetherscript.example.hello" sender any {
  let hearts = 10
  let ready = hearts > 0
  if ready {
    send "Ready with " + text(hearts) + " hearts."
  } else {
    send "Not ready."
  }
}
```

The header carries feature ID/version and command name/permission/sender. IDs retain the existing filename and logical-ID checks. `runtime.message.send` is inferred. The first header exposes the existing one-command surface.

```ebnf
program     = "feature", id, "version", text, newline,
              "command", id, "permission", text, "sender", ("any" | "player"), block ;
block       = "{", { newline | statement }, "}" ;
statement   = "let", name, "=", expression, terminator
            | "send", expression, terminator
            | "if", expression, block, ["else", block] ;
terminator  = newline | closing-brace-boundary ;
expression  = literal | name | "text", "(", expression, ")"
            | "(", expression, ")" | unary-expression | binary-expression ;
```

Statements use newlines. A closing brace can end the final statement in its block. `//` starts a line comment. The implementation's precedence rules refine the expression production.

## Values, names and expressions

- Values are text, boolean and signed 64-bit integers. Integer arithmetic is checked for overflow.
- Arithmetic operators are `+`, `-`, `*`, `/` and `%`. Comparison/equality operators are `<`, `<=`, `>`, `>=`, `==` and `!=`. Boolean operators are `!`, `&&` and `||`.
- `text(expression)` performs an explicit text conversion. Operand combinations are type-checked before activation.
- Locals are immutable and lexically scoped. An inner block can shadow an outer name. A block rejects duplicate declarations. Initializers resolve earlier bindings, so self/forward references fail.
- Conditions require booleans. Both branch bodies and both sides of boolean operators are statically checked. Preparation evaluates the selected branch and short-circuits `&&`/`||`.
- Integer division truncates toward zero. Evaluation rejects a zero divisor and the `MIN_VALUE / -1` overflow case. Remainder has the dividend's sign.
- Source is UTF-8. A leading BOM is accepted without shifting reported columns. Malformed UTF-8 is rejected. Text supports escaped quote, backslash, newline, carriage return and tab. Unsupported escapes fail. Emitted messages still satisfy the existing plain-text/control-character policy.

## Bounds and diagnostics

| Resource | Initial bound |
| --- | --- |
| Source | 64 KiB |
| Lexer tokens, including newlines/end | 8,192 |
| Expression nodes | 2,048 |
| Statements | 256 |
| Lexical/expression nesting | 32 |
| Preparation evaluation steps | 8,192 |
| Selected message actions | 1–32 |
| Message text | Existing 2,048 UTF-16-unit plain-text bound |

Syntax, undefined names, duplicate declarations, incompatible types, arithmetic failures and exceeded bounds should report useful line/column locations through the existing validation diagnostic type. The source hash refers to original source bytes, while the IR hash refers to the resulting immutable feature. Equal behavior across frontends can therefore have equal IR hashes with distinct source hashes.

## Extension path

Retain source spans and type information as the language grows into an invocation-time IR. A future typed callable registry must declare stable IDs, argument/result types, platform versions, execution context, permissions/capabilities, ownership, error behavior and examples. That registry should supply both language calls and generated SDK/editor metadata. It must preserve one semantic implementation where structured definitions and textual syntax overlap.

The initial grammar deliberately supplies a bounded subset that fits today's message model. Full M1 acceptance requires runtime scopes/control flow/functions and initial SDK contracts. M2 then supplies real commands, events, scheduling, typed Minecraft references and gameplay persistence.

## Executed checkpoint

The combined 0.0.4 suite passes 113 tests with zero failures, errors or skips. TextFrontendTest covers values, expressions, scope, inactive-branch checking, integer edge cases, strict source encoding, limits and diagnostics. TextIntegrationTest covers shared IR/source hashes, normal reload/permission/retirement, invalid-edit restart recovery, held rollback, duplicate formats and structured-to-text migration. Seven packaged offline authoring checks also pass. The final binary passed 28 real Paper checks across five boots, including 100 distinct textual reloads. [0.0.4 evidence](../evidence/0.0.4/)

The completed 0.0.4 server record is [Paper 26.3 build 245 evidence](../evidence/0.0.4/paper-26.3-245.json).
