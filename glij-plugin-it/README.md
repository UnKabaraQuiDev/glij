# Maven consumer integration test

This module uses `glij-maven-plugin:generate` during Maven's normal
`generate-sources` phase. Its `.glij` source files become compiled Java classes
in `src/gen/java`, and shader files become resources under
`src/gen/resources/shaders` and `target/classes/shaders`.

The executable JUnit tests verify:

- Calling a generated compute shader dispatches work, binds SSBOs and returns a GPU fence.
- Calling a generated render shader binds a `Mesh` and triggers the draw call.
- The plugin discovers an **external** rule provider from `glij-test-bindings`
  through the plugin dependency and invokes `CustomResource.bind()` and
  `ImageResource.bindUniform(...)`.
- Shader resources are copied to the classpath and pass `glslangValidator`
  (when installed).

Small Java classes under `src/main/java/lu/kbra/standalone` and `org/joml`
are **test doubles**, copied from the compiler test fixtures, so tests run on
a headless CI runner without a real OpenGL context or the proprietary engine JAR.
Replace them with your actual engine/JOML dependencies for GPU runtime testing.

Run from the root: `mvn clean verify`.

The input tree is `src/main/java`, with `.glij`, `.gpujava`, and ordinary
`.java` files side-by-side. `pom.xml` configures both GLiJ extensions and
the output roots. `Extra.gpujava` and `NormalJava.java` prove that the Maven
plugin generates `Extra.java` while leaving ordinary Java to the normal compiler.


The Maven consumer additionally compiles a `.gpujava` class from
`src/secondary/java` into `src/gen/secondary/java`; the JUnit test directly
invokes this class. This checks two independent input/output directory pairs.
