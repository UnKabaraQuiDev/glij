package lu.kbra.glij.tests;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lu.kbra.glij.binding.BindingRegistry;
import lu.kbra.glij.compiler.GLiJCompiler;

class SourceOptionsTest {
	@TempDir
	Path directory;

	@Test
	void extensionsNormalizeAndRejectJava() {
		Assertions.assertEquals(java.util.Set.of("glij", "gpujava"), GLiJCompiler.normalizeExtensions(".GLIJ, gpujava"));
		Assertions.assertThrows(IllegalArgumentException.class, () -> GLiJCompiler.normalizeExtensions(".java"));
		Assertions.assertThrows(IllegalArgumentException.class, () -> GLiJCompiler.normalizeExtensions("foo/bar"));
		Assertions.assertEquals(java.util.Set.of("glij"), GLiJCompiler.normalizeExtensions(""));
	}

	@Test
	void mixedSourceTreeOnlyProcessesConfiguredExtensions() throws Exception {
		final Path src = this.directory.resolve("src/main/java/demo");
		Files.createDirectories(src);
		Files.writeString(src.resolve("Normal.java"), "package demo; public class Normal { }\n");
		Files.writeString(src.resolve("Custom.gpujava"), "package demo; public class Custom { }\n");
		Files.writeString(src.resolve("Default.glij"), "package demo; public class Default { }\n");
		final var compiler = new GLiJCompiler(new BindingRegistry());
		final var custom = compiler.compile(this.directory.resolve("src/main/java"),
				this.directory.resolve("src/gen/java"),
				this.directory.resolve("src/gen/resources"),
				".gpujava");
		Assertions.assertEquals(1, custom.javaFiles().size());
		Assertions.assertEquals("Custom.java", custom.javaFiles().getFirst().getFileName().toString());
		Assertions.assertFalse(Files.exists(this.directory.resolve("src/gen/java/demo/Normal.java")));
		Assertions.assertFalse(Files.exists(this.directory.resolve("src/gen/java/demo/Default.java")));
		final var both = compiler.compile(this.directory.resolve("src/main/java"),
				this.directory.resolve("src/gen/java"),
				this.directory.resolve("src/gen/resources"),
				".gpujava,glij");
		Assertions.assertEquals(2, both.javaFiles().size());
	}
}
