package lu.kbra.glij.maven;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lu.kbra.glij.compiler.GLiJCompiler;

class GenerateMojoTest {
	@TempDir
	Path root;

	@Test
	void defaultMappingUsesCompilerConstants() throws Exception {
		final GenerateMojo mojo = new GenerateMojo();
		GenerateMojoTest.set(mojo, "basedir", this.root.toFile());
		final var layout = mojo.sourceLayout();
		Assertions.assertEquals(1, layout.directories().size());
		Assertions.assertEquals(this.root.resolve(GLiJCompiler.DEFAULT_SOURCE_DIRECTORY), layout.directories().getFirst().source());
		Assertions.assertEquals(this.root.resolve(GLiJCompiler.DEFAULT_JAVA_OUTPUT_DIRECTORY),
				layout.directories().getFirst().output());
		Assertions.assertEquals(Set.of(GLiJCompiler.DEFAULT_EXTENSION), layout.directories().getFirst().fileExtensions());
	}

	@Test
	void directoryOverrideTakesPrecedenceOverGlobalExtensions() throws Exception {
		final GenerateMojo mojo = new GenerateMojo();
		GenerateMojoTest.set(mojo, "basedir", this.root.toFile());
		GenerateMojoTest.set(mojo, "fileExtensions", "glij,other");
		final SourceDirectoryOption first = new SourceDirectoryOption();
		first.setSource(new File("src/main/java"));
		first.setOutput(new File("src/gen/java"));
		final SourceDirectoryOption second = new SourceDirectoryOption();
		second.setSource(new File("src/other"));
		second.setOutput(new File("src/gen/other"));
		second.setFileExtensions("gpujava");
		GenerateMojoTest.set(mojo, "directories", List.of(first, second));
		final var layout = mojo.sourceLayout();
		Assertions.assertEquals(Set.of("glij", "other"), layout.directories().getFirst().fileExtensions());
		Assertions.assertEquals(Set.of("gpujava"), layout.directories().get(1).fileExtensions());
		Assertions.assertEquals(this.root.resolve("src/gen/other"), layout.directories().get(1).output());
	}

	private static void set(final Object receiver, final String fieldName, final Object value) throws Exception {
		final Field field = receiver.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(receiver, value);
	}
}
