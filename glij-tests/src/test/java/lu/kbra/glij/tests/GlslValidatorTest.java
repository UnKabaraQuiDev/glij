package lu.kbra.glij.tests;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lu.kbra.glij.binding.BindingRegistry;
import lu.kbra.glij.compiler.GLiJCompiler;
import lu.kbra.glij.engine.EngineBindingProvider;

/** Validates with Khronos glslangValidator when the executable is installed. */
public class GlslValidatorTest {
	@TempDir
	Path temp;

	@Test
	void reservedJavaNamesGenerateValidGlsl() throws Exception {
		Process probe;
		try {
			probe = new ProcessBuilder("glslangValidator", "--version").start();
		} catch (final java.io.IOException missing) {
			Assumptions.assumeTrue(false, "glslangValidator not installed");
			return;
		}
		Assumptions.assumeTrue(probe.waitFor() == 0);
		final Path src = this.temp.resolve("src/Example.glij");
		Files.createDirectories(src.getParent());
		Files.writeString(src, """
				class Example {
				  @GlCompute(version=460)
				  @LocalSize(x=32)
				  @CacheManager("cacheManager")
				  void compute(@Layout("std430") FloatAttribArray input) {
				    void main() { input[0] = 1.0; }
				  }
				}
				""");
		final var registry = new BindingRegistry();
		new EngineBindingProvider().register(registry);
		final var generated = new GLiJCompiler(registry)
				.compile(this.temp.resolve("src"), this.temp.resolve("gen"), this.temp.resolve("res"));
		final String glsl = Files.readString(generated.shaderFiles().getFirst());
		Assertions.assertTrue(glsl.contains("poly_input"));
		final Process validator = new ProcessBuilder("glslangValidator", generated.shaderFiles().getFirst().toString())
				.redirectErrorStream(true)
				.start();
		final String output = new String(validator.getInputStream().readAllBytes());
		Assertions.assertEquals(0, validator.waitFor(), output);
	}
}
