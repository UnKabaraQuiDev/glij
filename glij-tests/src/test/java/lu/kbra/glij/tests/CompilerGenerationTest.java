package lu.kbra.glij.tests;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.tools.ToolProvider;

import org.joml.Vector3i;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lu.kbra.glij.api.Semaphore;
import lu.kbra.glij.binding.BindingRegistry;
import lu.kbra.glij.compiler.GLiJCompiler;
import lu.kbra.glij.engine.EngineBindingProvider;
import lu.kbra.standalone.gameengine.cache.attrib.FloatAttribArray;
import lu.kbra.standalone.gameengine.generated.gl_wrapper.GL_W;
import lu.kbra.standalone.gameengine.geom.Mesh;

public class CompilerGenerationTest {
	@TempDir
	Path temp;

	private final BindingRegistry bindings = new BindingRegistry();

	public CompilerGenerationTest() {
		new EngineBindingProvider().register(this.bindings);
	}

	@Test
	void generatedComputeJavaCompilesAndDispatches() throws Exception {
		final Path input = this.temp.resolve("source/example/ParticleSystem.glij");
		Files.createDirectories(input.getParent());
		Files.writeString(input, """
				package example;
				import lu.kbra.glij.api.GlCompute;
				import lu.kbra.glij.api.LocalSize;
				import lu.kbra.glij.api.GroupSize;
				import lu.kbra.glij.api.Blocking;
				import lu.kbra.glij.api.Uniform;
				import lu.kbra.glij.api.Layout;
				import lu.kbra.glij.api.Access;
				import lu.kbra.glij.api.AccessMode;
				import lu.kbra.glij.api.Semaphore;
				import org.joml.Vector3i;
				import lu.kbra.standalone.gameengine.cache.CacheManager;
				import lu.kbra.standalone.gameengine.cache.attrib.FloatAttribArray;
				public class ParticleSystem {
				    private final CacheManager cacheManager = new CacheManager();
				    @GlCompute(version=460)
				    @LocalSize(x=128)
				    @lu.kbra.glij.api.CacheManager("cacheManager")
				    public Semaphore calculate(@Blocking boolean block, @GroupSize Vector3i groups,
				        @Layout("std430") @Access(AccessMode.READ_ONLY) FloatAttribArray input,
				        @Uniform int count,
				        @Layout("std430") @Access(AccessMode.WRITE_ONLY) FloatAttribArray output) {
				        void main() {
				            uint i = gl_GlobalInvocationID.x;
				            if (i >= uint(count)) return;
				            output[i] = float(i);
				        }
				    }
				}
				""");
		final var result = new GLiJCompiler(this.bindings)
				.compile(this.temp.resolve("source"), this.temp.resolve("java"), this.temp.resolve("resources"));
		Assertions.assertEquals(2, result.javaFiles().size());
		Assertions.assertEquals(1, result.shaderFiles().size());
		final String glsl = Files.readString(result.shaderFiles().getFirst());
		Assertions.assertTrue(glsl.contains("layout(local_size_x = 128"));
		Assertions.assertTrue(glsl.contains("readonly buffer InputBuffer"));
		Assertions.assertTrue(glsl.contains("writeonly buffer OutputBuffer"));
		Assertions.assertTrue(glsl.contains("uniform int count;"));
		try (URLClassLoader loader = this.compileJava(result.javaFiles())) {
			GL_W.reset();
			final var particle = loader.loadClass("example.ParticleSystem").getConstructor().newInstance();
			final var method = particle.getClass()
					.getMethod("calculate", boolean.class, Vector3i.class, FloatAttribArray.class, int.class, FloatAttribArray.class);
			final Semaphore semaphore = (Semaphore) method
					.invoke(particle, false, new Vector3i(4, 1, 1), new FloatAttribArray(), 40, new FloatAttribArray());
			Assertions.assertNotNull(semaphore);
			Assertions.assertTrue(semaphore.isDone());
			Assertions.assertEquals(1, GL_W.dispatches);
			Assertions.assertArrayEquals(new int[] { 4, 1, 1 }, GL_W.lastGroups);
			Assertions.assertEquals(2, GL_W.bufferBindings);
			semaphore.await();
			semaphore.close();
			semaphore.close();
			Assertions.assertEquals(1, GL_W.deletions);
		}
	}

	@Test
	void generatedRenderingJavaCompilesAndDraws() throws Exception {
		final Path input = this.temp.resolve("source/example/MyRenderer.glij");
		Files.createDirectories(input.getParent());
		Files.writeString(input, """
				package example;
				import lu.kbra.glij.api.GlRender;
				import lu.kbra.glij.api.Uniform;
				import lu.kbra.standalone.gameengine.cache.CacheManager;
				import lu.kbra.standalone.gameengine.geom.Mesh;
				public class MyRenderer {
				    private final CacheManager cacheManager = new CacheManager();
				    @GlRender(version = 460)
				    @lu.kbra.glij.api.CacheManager("cacheManager")
				    public void render(Mesh mesh, @Uniform float brightness) {
				        vertex(@AttributeLocation(0) in vec3 position, out vec3 outputColor): {
				            void main() {
				                gl_Position = vec4(position, 1.0);
				                outputColor = vec3(brightness);
				            }
				        }
				        fragment(in vec3 outputColor, @AttributeLocation(0) out vec4 fragColor): {
				            void main() { fragColor = vec4(outputColor, 1.0); }
				        }
				    }
				}
				""");
		final var result = new GLiJCompiler(this.bindings)
				.compile(this.temp.resolve("source"), this.temp.resolve("java"), this.temp.resolve("resources"));
		Assertions.assertEquals(2, result.shaderFiles().size());
		Assertions.assertTrue(Files.readString(result.shaderFiles().getFirst()).contains("layout(location = 0) in vec3 position;"));
		Assertions.assertTrue(Files.readString(result.shaderFiles().get(1)).contains("out vec4 fragColor;"));
		try (URLClassLoader loader = this.compileJava(result.javaFiles())) {
			GL_W.reset();
			final var renderer = loader.loadClass("example.MyRenderer").getConstructor().newInstance();
			renderer.getClass().getMethod("render", Mesh.class, float.class).invoke(renderer, new Mesh(), 0.5f);
			Assertions.assertEquals(1, GL_W.drawCalls);
		}
	}

	@Test
	void resolvesIncludesAndRejectsTraversal() throws Exception {
		final Path root = this.temp.resolve("shaders");
		Files.createDirectories(root);
		Files.writeString(root.resolve("helper.glsl"), "float square(float x) { return x*x; }\n");
		final var resolver = new lu.kbra.glij.compiler.shader.IncludeResolver(root);
		Assertions.assertTrue(resolver.resolve("#include \"helper.glsl\"\nvoid main(){}").contains("float square"));
		Assertions.assertThrows(java.io.IOException.class, () -> resolver.resolve("#include \"../secret.glsl\""));
	}

	@Test
	void multipleAnnotationIncludesAreInsertedAfterVersion() throws Exception {
		final Path root = this.temp.resolve("src");
		Files.createDirectories(root);
		Files.writeString(root.resolve("first.glsl"), "float helperA() { return 1.0; }\n");
		Files.writeString(root.resolve("second.glsl"), "float helperB() { return 2.0; }\n");
		Files.writeString(root.resolve("Includes.glij"), """
				class Includes {
				    @GlCompute(version=460)
				    @CacheManager("cacheManager")
				    @Include(path="first.glsl")
				    @Include(path="second.glsl")
				    public void execute() { void main() { float a = helperA() + helperB(); } }
				}
				""");
		final var result = new GLiJCompiler(this.bindings)
				.compile(root, this.temp.resolve("generated"), this.temp.resolve("resources"));
		final String glsl = Files.readString(result.shaderFiles().getFirst());
		Assertions.assertTrue(glsl.startsWith("#version 460 core"));
		Assertions.assertTrue(glsl.contains("float helperA()"));
		Assertions.assertTrue(glsl.contains("float helperB()"));
	}

	@Test
	void shaderFunctionsFromOtherSourceAreIncludedAndJavaRemainsCompilable() throws Exception {
		final Path root = this.temp.resolve("gpu");
		Files.createDirectories(root.resolve("example"));
		Files.writeString(root.resolve("example/MathGpu.glij"), """
				package example;
				import lu.kbra.glij.api.ShaderFunction;
				import lu.kbra.glij.api.Include;
				public class MathGpu {
				  @ShaderFunction public static float twice(float number) { return number * 2.0; }
				  @ShaderFunction public static float quadruple(float number) { return twice(twice(number)); }
				}
				""");
		Files.writeString(root.resolve("example/Kernel.glij"), """
				package example;
				import lu.kbra.glij.api.*;
				import lu.kbra.standalone.gameengine.cache.CacheManager;
				public class Kernel {
				  private final CacheManager cacheManager = new CacheManager();
				  @GlCompute @LocalSize(x=16) @ShaderCache("cacheManager")
				  @Include(type=MathGpu.class, methodName="quadruple")
				  @Include(type=MathGpu.class, methodName="twice")
				  public void execute() { void main() { float x = quadruple(1.0); } }
				}
				""");
		// The transitive function dependency is resolved before the function using it.
		String source = Files.readString(root.resolve("example/MathGpu.glij"));
		source = source.replace("@ShaderFunction public static float quadruple", """
				@Include(type=MathGpu.class, methodName="twice")
				  @ShaderFunction public static float quadruple""");
		Files.writeString(root.resolve("example/MathGpu.glij"), source);
		final var result = new GLiJCompiler(this.bindings).compile(root, this.temp.resolve("java"), this.temp.resolve("resources"));
		final String glsl = Files.readString(result.shaderFiles().getFirst());
		Assertions.assertEquals(1, glsl.split("float twice\\(").length - 1);
		Assertions.assertTrue(glsl.indexOf("float twice(") < glsl.indexOf("float quadruple("));
		Assertions.assertTrue(glsl.contains("float quadruple(float number)"));
		try (URLClassLoader ignored = this.compileJava(result.javaFiles())) {
			/* compile all rewritten classes */ }
	}

	@Test
	void invalidShaderFunctionReferencesFailWithHelpfulError() throws Exception {
		final Path root = this.temp.resolve("source");
		Files.createDirectories(root);
		Files.writeString(root.resolve("Bad.glij"), """
				class Bad {
				  @GlCompute @CacheManager("cacheManager")
				  @Include(type=Bad.class, methodName="missing")
				  void kernel() { void main() { } }
				}
				""");
		final Exception error = Assertions.assertThrows(IllegalArgumentException.class,
				() -> new GLiJCompiler(this.bindings).compile(root, this.temp.resolve("gen"), this.temp.resolve("res")));
		Assertions.assertTrue(error.getMessage().contains("@Include"), error.getMessage());
	}

	@Test
	void compileFileProcessesOnlyOneEntryButCanResolveSiblingFunctions() throws Exception {
		final Path sourceRoot = this.temp.resolve("source");
		Files.createDirectories(sourceRoot);
		final Path helper = sourceRoot.resolve("Helpers.glij");
		final Path kernel = sourceRoot.resolve("ComputeOne.glij");
		Files.writeString(helper, """
				class Helpers { @ShaderFunction static float triple(float x) { return x * 3.0; } }
				""");
		Files.writeString(kernel, """
				class ComputeOne {
				  @GlCompute @CacheManager("cacheManager") @Include(type=Helpers.class,methodName="triple")
				  void run() { void main() { float result = triple(2.0); } }
				}
				""");
		final var result = new GLiJCompiler(this.bindings)
				.compileFile(kernel, sourceRoot, this.temp.resolve("java"), this.temp.resolve("resources"));
		Assertions.assertEquals(2, result.javaFiles().size());
		Assertions.assertTrue(Files.readString(result.shaderFiles().getFirst()).contains("float triple(float x)"));
		Assertions.assertFalse(Files.exists(this.temp.resolve("java/Helpers.java")));
	}

	@Test
	void rejectsCyclicShaderFunctions() throws Exception {
		final Path root = this.temp.resolve("src");
		Files.createDirectories(root);
		Files.writeString(root.resolve("Cyclic.glij"), """
				class Cyclic {
				  @ShaderFunction @Include(type=Cyclic.class,methodName="b")
				  static float a(float x) { return b(x); }
				  @ShaderFunction @Include(type=Cyclic.class,methodName="a")
				  static float b(float x) { return a(x); }
				  @GlCompute @CacheManager("cacheManager") @Include(type=Cyclic.class,methodName="a")
				  void compute() { void main() { float result = a(1.0); } }
				}
				""");
		final var error = Assertions.assertThrows(IllegalArgumentException.class,
				() -> new GLiJCompiler(this.bindings).compile(root, this.temp.resolve("gen"), this.temp.resolve("res")));
		Assertions.assertTrue(error.getMessage().contains("Cyclic @Include"), error.getMessage());
	}

	@Test
	void javacErrorsAfterGpuBlockReportOriginalGlijLine() throws Exception {
		final Path root = this.temp.resolve("source");
		Files.createDirectories(root);
		final String content = """
				import lu.kbra.glij.api.*;
				import lu.kbra.standalone.gameengine.cache.CacheManager;
				class WrongJava {
				  CacheManager cacheManager = new CacheManager();
				  @GlCompute @ShaderCache("cacheManager")
				  void gpu() {
				    void main() {
				      uint id = gl_GlobalInvocationID.x;
				    }
				  }
				  int broken = "wrong type";
				}
				""";
		Files.writeString(root.resolve("WrongJava.glij"), content);
		final var result = new GLiJCompiler(this.bindings).compile(root, this.temp.resolve("java"), this.temp.resolve("resources"));
		final String rewritten = Files.readString(this.temp.resolve("java/WrongJava.java"));
		Assertions.assertEquals(content.lines().count(), rewritten.lines().count());

		final var diagnostics = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
		final var javac = ToolProvider.getSystemJavaCompiler();
		try (var manager = javac.getStandardFileManager(diagnostics, null, null)) {
			final var fileObjects = manager.getJavaFileObjectsFromPaths(result.javaFiles());
			final var compiled = javac.getTask(null,
					manager,
					diagnostics,
					List.of("--release",
							"21",
							"-classpath",
							System.getProperty("java.class.path"),
							"-d",
							this.temp.resolve("compiled").toString()),
					null,
					fileObjects);
			Files.createDirectories(this.temp.resolve("compiled"));
			Assertions.assertFalse(compiled.call(), "the deliberate Java type error should fail");
		}
		final long expectedLine = content.substring(0, content.indexOf("int broken")).lines().count();
		Assertions.assertTrue(
				diagnostics.getDiagnostics()
						.stream()
						.anyMatch(d -> d.getLineNumber() == expectedLine && d.getMessage(null).contains("incompatible types")),
				diagnostics.getDiagnostics().toString());
	}

	private URLClassLoader compileJava(final List<Path> files) throws Exception {
		final Path classes = this.temp.resolve("compiled");
		Files.createDirectories(classes);
		final List<String> args = new ArrayList<>(
				List.of("--release", "21", "-classpath", System.getProperty("java.class.path"), "-d", classes.toString()));
		for (final Path file : files) {
			args.add(file.toString());
		}
		final int status = ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new));
		Assertions.assertEquals(0, status, "generated Java must compile");
		return new URLClassLoader(new URL[] { classes.toUri().toURL() }, this.getClass().getClassLoader());
	}
}
