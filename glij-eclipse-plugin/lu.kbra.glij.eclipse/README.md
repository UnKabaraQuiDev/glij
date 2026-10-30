# GLiJ Eclipse integration

Eclipse PDE/OSGi bundle for Java 21+ / Eclipse 2026-09.

Build an **installable update-site ZIP** from the top-level Maven reactor:

```sh
mvn -B -ntp clean install
mvn -B -ntp -f glij-eclipse-plugin/pom.xml clean verify
```

Tycho downloads the necessary Eclipse platform bundles from the Eclipse p2
repository and produces the complete ZIP at
`glij-eclipse-plugin/lu.kbra.glij.eclipse.repository/target/glij-eclipse-update-site.zip`.
No local Eclipse installation, SDK tarball, or shell script is needed.

## Usage

1. Import a Java 21+ project containing `src/main/java/**/*.glij`.
2. Right-click the project and select **Enable GLiJ**.
3. The GLiJ builder runs before JDT. It generates Java classes in
   `src/gen/java` and GLSL under `src/gen/resources/shaders`, and registers
   both as Java project source roots so the Java builder compiles/copies them.
4. Open a `.glij` file in **GLiJ**. The editor now **extends**
   JDT's `CompilationUnitEditor`, with the **Java editor's native scanner and
   presentation**. Import/package declarations, Java annotations, signatures,
   fields, and ordinary Java code keep the standard Eclipse Java palette.
   Only GLSL function ranges inside `@GlCompute`, `@GlRender`, and
   `@ShaderFunction` get GLiJ-specific colors. Render stages
   `vertex(...): { ... }` and `fragment(...): { ... }` are recognized.
5. **Window > Preferences > GLiJ** controls colors for GLSL tokens only:
   keywords, types, built-ins, uniforms, local variables, functions, comments,
   strings, numbers, and preprocessor lines. **Follow Java editor** maps GLSL
   categories to JDT's Java colors; other choices are Dark, Light and Custom.
   All Java portions continue using JDT's own theme/settings regardless of
   the GLSL palette setting.
6. Ctrl+Space uses the **unsaved Java working copy** outside GLSL, translating
   completion edits to .glij source offsets. GLSL completion and coloring are
   unchanged. The working copy is private and is never committed to disk.
7. Ctrl+Shift+O uses JDT's OrganizeImportsOperation on that private working copy.
   Only a Java edit that maps entirely to original source is applied to .glij.
8. Java errors and warnings are reconciled in the working copy after document
   changes and displayed as transient annotations with exact original offsets.
   Saved-build markers are also mirrored with exact offsets where possible.

## Why .glij is not a JDT Java-like extension

The raw `.glij` DSL contains invalid Java tokens (`vertex(...) : { ... }`,
`uint`, GLSL buffer statements). Declaring it to JDT as ordinary Java would
produce *false syntax errors*. This plug-in instead builds **real transformed
`.java` sources**, which JDT handles as standard Java (type checking, compile,
warnings, refactoring on the generated files). The original source keeps the
same physical line numbers because the compiler replaces GPU bodies with a
single-line delegate and line-preserving comments. After each Java build,
errors on generated `.java` originating from `.glij` are mirrored to `.glij`.
When the generated text matches the compiler's in-memory projection, unaffected
Java columns are mapped exactly; synthetic delegate errors are not forwarded.

### JDT bridge architecture and limits

- `GLiJCompiler.projectJava(...)` generates an **in-memory** Java projection
  using the same dispatcher generation as the disk compiler. It returns
  `JavaSourceProjection`, which maps Java and GLiJ UTF-16 source ranges.
- On document changes, `JavaWorkingCopyBridge` updates and reconciles a private
  `ICompilationUnit` working copy of the generated `.java` file. Reconciliation
  runs in a debounced background job; explicit completion synchronizes the
  latest buffer. The generated file must exist and be on the project's JDT
  classpath (enable GLiJ / build once first).
- JDT can find symbols in ordinary Java portions and compute import edits.
  Import edits never modify the generated `.java` file; only safely mapped edits
  are applied to the original `.glij` document.
- Java diagnostic squiggles from the live working copy are transient editor
  annotations; diagnostics from saved builds are persistent problem markers.
  Errors originating in synthesized Java delegates are not shown as spurious
  errors in GLSL. GLSL itself is not type checked by JDT.
- Proposals that require additional JDT edits (e.g. automatic insertion of an
  import as a side effect of selecting completion) currently insert only their
  primary edit. Run **Organize Imports** afterward to add imports. Advanced
  multi-file refactorings and semantic navigation are not yet bridged; applying
  such changes to generated files would risk corrupting source.
- While a shader declaration is partially typed and cannot be parsed, the
  bridge retains the previous valid working copy and retries on subsequent edits.
- This editor intentionally subclasses JDT's *internal* CompilationUnitEditor,
  and Tycho/Eclipse integration must be retested after JDT upgrades.
- A full Tycho/Eclipse runtime validation has not been performed in this
  environment; regression tests for offset translation, tolerant parsing and
  the existing GLSL lexer can be run headlessly.

## Configurable file associations

Open **Window → Preferences → GLiJ** and set **Editor extensions**
(e.g. `glij,gpujava`). The plug-in associates matching *workspace files* with
its editor, including files added after Eclipse starts. It does not globally
redefine all plain-text file types. Existing GLiJ associations are cleared
when removed from the setting. Eclipse's declared default is `.glij`.

The Eclipse builder reads the `glij-maven-plugin` configuration in the
project's `pom.xml` using the shared `MavenSourceLayout` parser. It supports
multiple `<directories>` entries, per-directory file extensions, and different
Java output folders. The working-copy bridge and diagnostic mirroring use the same
source/output mappings. There is no line/column guessing for editable Java ranges. The generated folders are registered as Eclipse JDT
source roots. If no plugin configuration exists, it uses the compiler defaults.

**Limit:** The headless POM reader supports direct plugin configuration and
project-local Maven properties. It cannot resolve externally inherited Maven
settings or profiles. Projects with those settings should declare the effective
GLiJ directory mappings locally in their `pom.xml`. Eclipse-generated Java
source roots must be inside the Eclipse project directory (or linked manually).

**JDT editor inheritance:** `GLiJEditor extends CompilationUnitEditor` and
its action contributor is JDT's `CompilationUnitEditorActionContributor`. It
preserves JDT Java scanner behavior by adding a **text presentation listener**
that recolors only shader ranges. A small `JavaSourceViewerConfiguration`
subclass only appends shader completion; it does **not** replace the JDT Java
scanner. Both classes are JDT implementation details / explicitly unsupported
extension points, so an Eclipse/JDT upgrade requires integration testing.

**Source-of-truth guarantee:** All generated Java working-copy edits are in-memory.
Only text edits with a proven inverse source-map range are applied to `.glij`.
Full JDT Java refactorings across generated shader code remain unsupported.

## Scope and tokenizer regression test

There is a dependency-free smoke test in `../tests/GpuHighlightingSmokeTest.java`:

```sh
mkdir -p /tmp/glij-editor-test
javac -d /tmp/glij-editor-test \
  glij-compiler/src/main/java/lu/kbra/glij/compiler/source/{MethodRegion,MethodRegionFinder}.java \
  glij-eclipse-plugin/lu.kbra.glij.eclipse/src/main/java/lu/kbra/glij/eclipse/editor/{GpuHighlightRegions,GpuLexicalHighlighter}.java \
  glij-eclipse-plugin/tests/GpuHighlightingSmokeTest.java
java -cp /tmp/glij-editor-test GpuHighlightingSmokeTest
```

It checks Java/GLSL boundaries, Java annotations, render-stage blocks,
`@ShaderFunction`, partial functions, full identifier matching (including
`main`, `Layout`, `in` and `out`), and token categories.

## Update-site ZIP

See [`../README.md`](../README.md) for headless Maven build instructions,
GitHub Actions deployment, and instructions for installing from Eclipse's
**Help → Install New Software…** dialog.

## Working-copy bridge smoke checks

The dependency-free source projection tests can run with Java 21:

```sh
javac -d /tmp/glij-bridge-test \
  glij-compiler/src/main/java/lu/kbra/glij/compiler/JavaSourceProjection.java \
  glij-eclipse-plugin/tests/JavaSourceProjectionSmokeTest.java
java -cp /tmp/glij-bridge-test JavaSourceProjectionSmokeTest
```

`JavaProjectionParserSmokeTest.java` also exercises tolerant AST discovery on
`glij-plugin-it/src/main/java/demo/ParticleJob.glij` and intentionally
invalid surrounding Java; it needs `JavaAstAnalyzer`, `ParameterSpec`, and the
`MethodRegion` classes on the `javac` command line.
