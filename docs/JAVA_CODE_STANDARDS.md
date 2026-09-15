# Java Code Standards

This document is the single source of truth for code-quality rules on this
project. Every rule here is **non-negotiable**: apply it to new code as it is
written, and treat any existing code that violates it as a defect to fix,
not as legacy to leave alone.

When a new standard is agreed with the project owner, add it here first,
then apply it across `src/main/java`. Do not let a rule live only in a chat
transcript or in a subagent's memory — if it isn't in this file, it isn't a
standing rule.

## 1. Package organization

Organize each capability package by **kind**, not as one flat package:

| Subpackage | Contents |
|---|---|
| *(package root)* | The capability's own Mongo/Panache document, when the package itself names the aggregate (e.g. `customerrecord`, `importjob`, `jobconfig`) |
| `.dto` | REST request/response records |
| `.model` | Domain data shapes that are **not** REST DTOs and **not** the package's own persistent entity (a CSV row record, a sealed result type, a recognized-field enum) |
| `.exception` | Domain exception classes |
| `.exception.mapper` | JAX-RS `ExceptionMapper` / `@Provider` classes — kept separate from `.exception` because they are HTTP-boundary adapters, not the exception type itself |
| `.validator` | Standalone validators |
| `.support` | Auxiliary technical infrastructure with no better home |

Apply this uniformly across every capability package.

## 2. Javadoc

1. **Every method needs Javadoc, regardless of visibility.** Public,
   protected, and private methods all require it — broader than the typical
   Java convention of documenting only the public API surface. This includes
   `@Test` methods.
   - **Current test suite (retrofit):** this project's existing tests were
     not built with Cucumber/Gherkin step-definition traceability from the
     start, so do not retrofit an artificial link to a Scenario or ADR onto
     them now. Add a short, plain descriptive sentence explaining what the
     test proves, in the same style as any other method's Javadoc.
   - **Future projects/suites:** when a project adopts a Cucumber-style BDD
     framework, each `.feature`'s scenarios must be wired to their executing
     step definitions/tests from the outset (not added after the fact), so
     traceability from Gherkin scenario to test is built into the suite's
     structure itself, not documented after the fact via Javadoc.
2. **Every Javadoc block must open with a description sentence** before any
   `@param`/`@return`/`@throws`/`@author` tag. A block that jumps straight
   into tags with no prose explaining what the method/constructor/field does
   or represents is a violation, exactly like a missing Javadoc is. A short,
   specific sentence is enough even for a simple getter or constructor —
   never a generic filler sentence.
3. **Javadoc is always 3+ lines**, never the compact single-line form.

   ```java
   // WRONG
   /** Monotonically increasing per {@link #recordId}, starting at 1. */

   // RIGHT
   /**
    * Monotonically increasing per {@link #recordId}, starting at 1.
    */
   ```

4. **A blank `*` line separates every section**: between the description and
   the first `@param`, and between `@param`/`@return`/`@throws` blocks when
   more than one tag type is present. Example of a fully-conformant block:

   ```java
   /**
    * Returns the header column names (in file order) as an ordered set, or
    * empty if the file has no header/is empty.
    *
    * @param filePath the source CSV file to read
    *
    * @return the header's column names, in file order; empty if the file
    *         has no header
    *
    * @throws UncheckedIOException if the file cannot be read
    */
   ```

## 3. Type and file organization

1. **One class per file.** No multiple top-level or sizable nested classes
   crammed into a single `.java` file.
2. **No inner/nested classes sharing a file with another class.** Every
   class — including a `private static` helper, a JAX-RS `@Provider`
   grouped "for convenience," or a `ThreadFactory` — must be extracted to
   its own file and referenced from there. This applies even to a nested
   `record` used as an internal accumulator or auxiliary value, unless it is
   part of the explicit exception below.
   - **Exception:** the `sealed interface` + `record` pattern used to model
     an algebraic data type (e.g. a sealed `Result`/`Outcome` type with a
     `Success`/`Failure` record pair) is *not* a violation — it is a single
     cohesive type, not unrelated classes sharing a file by convenience.
   - **Exception:** an `enum` with a per-constant method body (constant-specific
     class bodies, e.g. `STRING { ... }`, `INTEGER { ... }`) is idiomatic
     Java, not a nested class, and is not covered by this rule.
   - When in doubt whether a nested type has a genuine technical
     justification to stay co-located (vs. mere editorial convenience),
     stop and ask rather than deciding unilaterally — see the project's
     "never assume" rule.

## 4. No stacked/nested object instantiation

Avoid nesting one object construction inside another's constructor/method
call arguments — this must be avoided at all costs. Extract every
intermediate value into its own local variable with a meaningful name, even
when that value is used exactly once and only within the current scope. A
descriptive local variable name documents what the intermediate value *is*,
which a nested expression cannot.

```java
// WRONG — nested/stacked construction
Document groupStage =
        new Document("$group",
                     new Document("_id", "$id")
                             .append("doc",
                                     new Document("$first", "$$ROOT")));

// RIGHT — each level named
Document firstDocumentInGroup = new Document("$first", "$$ROOT");

Document groupStage =
        new Document("$group",
                     Map.ofEntries(entry("_id", "$id"),
                                   entry("doc", firstDocumentInGroup)));
```

**What this does *not* forbid:** a builder-style chain on an already-named
variable (e.g. `Document doc = new Document("id", 1); doc.append("version", 1);`,
or even `.append(...)` chained directly onto a single `new` call assigned to
one variable) is fine — chaining calls onto one already-clear object is not
the problem. The problem is specifically a fresh `new X(...)` passed inline
as an argument to another constructor/method call: that is what makes an
expression hard to read, especially once it happens more than once in the
same statement. When in doubt, the test is "how many `new` keywords appear
in this one statement, and are any of them buried inside another call's
argument list?" — more than one nested that way is the violation, not the
presence of `.append`/chained calls in general.

**Caveat:** when the value being built needs a guaranteed iteration order
(e.g. a MongoDB compound index's field order, which changes which query
patterns the index can serve), do not reach for an order-losing `Map`
factory (`Map.of`, `Map.ofEntries`) to "flatten" the construction — those do
not guarantee iteration order. Use a `LinkedHashMap` (or another
order-preserving structure) explicitly instead, even if that means a few
more lines than a single-expression `Map.of` call.

## 5. Modern Java idioms

Prefer Streams, Lambdas, `Optional`, and default methods over older
imperative loops and manual null-checks — the project targets Java 25.

- Pure transformations (map/filter/collect over an already-materialized
  collection) should be expressed as Streams.
- A ternary or `if/else` doing nothing but a null-check/default should
  become `Optional.ofNullable(...).orElse(...)`/`.map(...)`.
- **Do not force modernization where it would reduce clarity or risk
  changing behavior**, in particular:
  - Sequential I/O with early-return/break semantics (e.g. reading a file
    line-by-line) is fine to leave imperative.
  - Loops mutating an external builder/object with positional/index
    semantics (e.g. writing POI `Sheet`/`Row`/`Cell` objects) are fine to
    leave imperative.
  - Code under a lock with precise short-circuit semantics (e.g. a
    concurrency gate) must only be converted after careful validation that
    behavior — including exception paths — stays identical; when unsure,
    ask before changing it.

## 6. Configuration

1. **Configuration property keys are named constants**, never string
   literals scattered across the codebase (e.g. avoid repeating
   `@ConfigProperty(name = "quarkus.mongodb.database")` inline more than
   once).
2. **Prefer YAML over `.properties`** for any project-owned configuration
   file. Exception: a file an external tool requires in a specific format
   for its own tooling (e.g. a Keycloak realm-export JSON) is exempt — that
   is the external tool's required format, not a project config choice.

## 7. Formatting

Match the file's existing indentation, brace placement, and wrapping style
when adding new methods or classes. Do not run a different formatter or
introduce a new formatting convention over new code — new code should blend
in with what is already there.

## 8. Static analysis

Code must be clean against standard SonarQube/SonarLint rules (code smells,
bugs, vulnerabilities, maintainability issues) — write code that would pass
a SonarLint scan, even though no SonarQube server/CI gate is configured for
this repository.

## Process note

Real ambiguities (e.g. "does this nested record count as a violation?",
"is this loop safe to modernize?") are never resolved unilaterally by
whoever is applying these rules — surface them for a decision instead of
guessing. Verify compliance with objective checks (grep/scripts, not only
visual inspection) before reporting a sweep as complete, since prior manual
"looks fine" reviews have missed real violations. Full test suite
(`./gradlew clean test --no-daemon`) must stay green after any sweep.
