# GLiJ

GLiJ (GLSL-inlined Java) is an experimental Java 21+ source-to-source compiler
for embedded GLSL compute and render methods.

## Modules

- `glij-api` — annotations and GPU-completion `Semaphore`.
- `glij-binding-spi` — extendible compile-time binding registry.
- `glij-compiler` — lexical extraction, JDK Java AST parsing, shader/resource and wrapper generation.
- `glij-engine-adapter` — build-time binding rules for the attached StandaloneGameEngine 0.2 APIs.
- `glij-maven-plugin` — `generate` goal bound to `generate-sources`.
- `glij-tests` — JUnit 5 regression tests and generation checks.
- `glij-test-bindings` — example third-party `BindingProvider` discovered with Java `ServiceLoader`.
- `glij-plugin-it` — actual Maven consumer: runs the plugin, compiles generated Java, packages GLSL, and tests execution against a small engine test double.
- `glij-eclipse-plugin/` — optional headless Tycho reactor containing the Eclipse plug-in,
  installable feature, and p2 repository ZIP (editor, builder, GLSL colors, and content assist).

## Build

Requires JDK 21+ and Maven 3.9+. Run `mvn clean verify` from the aggregator root.
This runs both the compiler unit tests and the real consumer module's Maven-plugin integration tests.
If `glslangValidator` is installed, the integration tests also validate the generated
`.comp`, `.vert` and `.frag` resources. Run `mvn install` to use the plugin in external projects.

The optional **installable Eclipse update-site ZIP** is generated directly by
Tycho after the core Maven artifacts are installed: `mvn clean install` then
`mvn -f glij-eclipse-plugin/pom.xml clean verify`. No installed Eclipse, standalone SDK download,
or external publishing script is needed. The ZIP is at
`glij-eclipse-plugin/lu.kbra.glij.eclipse.repository/target/glij-eclipse-update-site.zip`.
See [`glij-eclipse-plugin/README.md`](glij-eclipse-plugin/README.md) for build and installation instructions.

## Consuming Maven projects

Add `glij-api` as a compile dependency, your StandaloneGameEngine dependency
(`lu.kbra:gameengine:0.2-SNAPSHOT` in the supplied ZIP), and the plugin:

```xml
<plugin>
  <groupId>lu.kbra.glij</groupId>
  <artifactId>glij-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <executions><execution><goals><goal>generate</goal></goals></execution></executions>
</plugin>
```

Keep regular `.java` files and GLiJ sources **together** in `src/main/java`.
By default, only `.glij` files are transformed. Normal `.java` sources are left to `javac`.
Generated Java appears in `src/gen/java` and shaders in `src/gen/resources/shaders`;
Maven registers both output roots automatically. Never duplicate a GLiJ class
as a separate ordinary `.java` file.

All source paths and the GLiJ extension(s) are configurable in the Maven plug-in:

```xml
<plugin>
  <groupId>lu.kbra.glij</groupId>
  <artifactId>glij-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <configuration>
    <sourceDirectory>${project.basedir}/src/main/java</sourceDirectory>
    <fileExtensions>glij,gpujava</fileExtensions>
  </configuration>
  <executions><execution><goals><goal>generate</goal></goals></execution></executions>
</plugin>
```

For a working generation-only Maven consumer example, run `mvn generate-sources`
from `examples/` after `mvn install` in the reactor. Its generated shader files
can be checked with `glslangValidator examples/src/gen/resources/shaders/*.comp`
and similarly for `.vert` and `.frag` files.

### Notes and current constraints

This is an early compiler release, not yet a production shader language. GPU methods
must be on top-level classes, have a normal Java signature, and contain GLSL code
only inside their body. OpenGL rendering supports `vertex(...)` and
`fragment(...)` blocks. Include file paths are resolved under `src/main/java`.
The current binding rules support the supplied engine's known `AttribArray`,
`Texture`, and `Mesh` types and explicitly annotated uniforms.

Use `@lu.kbra.glij.api.CacheManager("cacheManager")` if the class imports
the game engine's `CacheManager` under the same simple name, or use the
equivalent `@ShaderCache("cacheManager")` annotation.

The type `lu.kbra.glij.api.Semaphore` is a GPU fence, **not** the
`java.util.concurrent.Semaphore`. Polling or waiting on it must happen with
the appropriate OpenGL context current; call `close()` to release the fence.

Shader files are not validated against a live OpenGL context at Maven build time.
Use a GLSL validator and run integration tests in an OpenGL-capable environment
before shipping shaders. OpenCL, automatic resource cleanup of failed GPU
operations, overloading/nested shader classes, and full GPU-type inference
are future work.

### Reusing GPU-only functions from Java sources

Shader helpers can live in the same `.glij` file or in a different file under
`src/main/java`. Use `@ShaderFunction` on a Java-style method signature,
then include the method from a GPU entry point:

```java
// GpuMath.glij
public class GpuMath {
    @ShaderFunction
    public static float square(float x) { return x * x; }
}

// In a compute shader method's annotations:
@Include(type = GpuMath.class, methodName = "square")
```

The compiler emits `float square(float x) { ... }` into GLSL and rewrites the
original Java helper into a throwing placeholder. The helper is GPU-only; Java
code must not call it. Transitive includes and cycle detection are supported.
For parameters/return types that need a nonstandard GLSL type, use
`@GpuType("vec3")` on the parameter or method. Path-based `@Include` and
`@StaticInclude` continue to work. Class/method includes currently resolve
only against `.glij` files within the current source tree.

### Extending parameter binding in the Maven plugin

Implement `lu.kbra.glij.binding.BindingProvider` in a separate JAR,
register it under `META-INF/services/lu.kbra.glij.binding.BindingProvider`,
and add that JAR as a **dependency of the Maven plugin** (not just the project's
regular dependencies). The plugin automatically discovers the provider.

For an end-to-end example see `glij-test-bindings` and
`glij-plugin-it/pom.xml`. The example supplies custom `.bind()` and
`.bindUniform(this, name, slot)` handlers; its tests verify both operations.
The built-in engine adapter also supports `@Uniform` texture arguments and
`@GpuType("sampler3D")` to override the default `sampler2D` declaration.

`glij-plugin-it` intentionally contains minimal test doubles matching the
given StandaloneGameEngine API; it does not ship or replace the actual engine.
For real integration, depend on your `lu.kbra:gameengine` artifact instead.

## GitHub Actions and Eclipse update site

- `.github/workflows/tests.yml`: Maven tests on pushes and pull requests,
  including a headless Tycho plug-in, feature and repository ZIP build.
- `.github/workflows/build-release.yml`: on `v*` tags or manual dispatch,
  run the same Maven reactor, upload the **installable** p2 ZIP and publish
  the unpacked repository to the `pages` branch.
- `.github/workflows/deploy-pages.yml`: optional SSH mirror of the p2 site;
  configure `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_PATH`, optionally
  `DEPLOY_PORT` (repository variables), and `DEPLOY_SSH_KEY` plus
  `DEPLOY_KNOWN_HOSTS` (repository secrets).

Build the Eclipse ZIP locally using **only JDK 21, Maven 3.9+, and network
access** (the Tycho build downloads Eclipse dependencies automatically):

```bash
mvn -B -ntp clean install
mvn -B -ntp -f glij-eclipse-plugin/pom.xml clean verify
```

Then use
`glij-eclipse-plugin/lu.kbra.glij.eclipse.repository/target/glij-eclipse-update-site.zip`
through **Eclipse → Help → Install New Software → Add → Archive**.
No Eclipse executable, SDK tarball, or standalone publishing script is needed.
See [`glij-eclipse-plugin/README.md`](glij-eclipse-plugin/README.md).

The Eclipse editor extends JDT's `CompilationUnitEditor`. It has a private
working copy of the generated Java counterpart, updated from unsaved `.glij`
text. Java completion and Organize Imports use that copy and only map safe
Java edits back to the original source; live Java diagnostics become error and
warning annotations. GLSL remains under the existing custom editor color and
completion path. Full multi-file Java refactoring is still unsupported.

## Maven source/output mapping

The Maven goal `glij:generate` reads GLiJ files alongside ordinary
`.java` files. Its defaults come from `GLiJCompiler`:

- `fileExtensions`: `glij` (comma/space-separated; `.java` is not allowed).
- A single source mapping: `src/main/java` → `src/gen/java`.
- `generatedResourcesDirectory`: `src/gen/resources` (shared shader resource root).

Configure any number of directory pairs with the optional `fileExtensions`
override; directories without an override inherit the top-level list:

```xml
<configuration>
    <fileExtensions>glij,other</fileExtensions>
    <directories>
        <directory>
            <source>${project.basedir}/src/main/java</source>
            <output>${project.basedir}/src/gen/java</output>
        </directory>
        <directory>
            <source>${project.basedir}/src/secondary/java</source>
            <output>${project.basedir}/src/gen/secondary/java</output>
            <fileExtensions>gpujava</fileExtensions>
        </directory>
    </directories>
    <generatedResourcesDirectory>${project.basedir}/src/gen/resources</generatedResourcesDirectory>
</configuration>
```

All configured Java outputs are registered as Maven compile source roots.
The compiler rejects overlapping source roots and duplicate output roots to
avoid ambiguous mappings. Resources from all mappings are written to the
shared resource folder. The Eclipse builder, completion bridge, and Java
diagnostic mirror use the same mappings when configured in the project's POM.
