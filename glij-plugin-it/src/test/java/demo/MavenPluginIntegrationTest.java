package demo;

import java.nio.file.Files;
import java.nio.file.Path;

import org.joml.Vector3i;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import lu.kbra.glij.api.Semaphore;
import lu.kbra.standalone.gameengine.cache.attrib.FloatAttribArray;
import lu.kbra.standalone.gameengine.generated.gl_wrapper.GL_W;
import lu.kbra.standalone.gameengine.geom.Mesh;

/** Maven itself generates the classes tested here during generate-sources. */
class MavenPluginIntegrationTest {
	@org.junit.jupiter.api.Test
	void secondSourceDirectoryIsCompiled() {
		org.junit.jupiter.api.Assertions.assertEquals("secondary", Secondary.location());
	}

	@Test
	void compilesAndCallsGeneratedComputeShader() {
		GL_W.reset();
		final Semaphore fence = new ParticleJob()
				.compute(false, new Vector3i(2, 3, 1), new FloatAttribArray(), new FloatAttribArray(), 128);
		Assertions.assertNotNull(fence);
		Assertions.assertEquals(1, GL_W.dispatches);
		Assertions.assertArrayEquals(new int[] { 2, 3, 1 }, GL_W.lastGroups);
		Assertions.assertEquals(2, GL_W.bufferBindings);
		fence.await();
		fence.close();
		Assertions.assertEquals(1, GL_W.deletions);
	}

	@Test
	void compilesAndCallsGeneratedRenderShader() {
		GL_W.reset();
		final CustomResource resource = new CustomResource();
		final ImageResource image = new ImageResource();
		new SceneJob().draw(new Mesh(), resource, image, 0.7f);
		Assertions.assertEquals(1, GL_W.drawCalls);
		Assertions.assertEquals(1, resource.binds(), "custom ServiceLoader provider should handle the resource");
		Assertions.assertEquals("image", image.boundName());
		Assertions.assertEquals(0, image.boundSlot());
	}

	@Test
	void copiesGeneratedResourcesIntoJarInputs() throws Exception {
		final Path shaders = Path.of("target/classes/shaders");
		Assertions.assertTrue(Files.exists(shaders.resolve("ParticleJob_compute_ComputeShader.comp")));
		Assertions.assertTrue(Files.exists(shaders.resolve("SceneJob_draw_RenderShader.vert")));
		Assertions.assertTrue(Files.exists(shaders.resolve("SceneJob_draw_RenderShader.frag")));
		final String source = Files.readString(shaders.resolve("ParticleJob_compute_ComputeShader.comp"));
		Assertions.assertTrue(source.contains("float square(float value)"));
		Assertions.assertTrue(source.contains("output[id] = square(poly_input[id])"));
	}

	@Test
	void generatedShaderFilesPassGlslValidationWhenAvailable() throws Exception {
		Process probe;
		try {
			probe = new ProcessBuilder("glslangValidator", "--version").start();
		} catch (final java.io.IOException notInstalled) {
			Assumptions.assumeTrue(false, "glslangValidator unavailable");
			return;
		}
		Assumptions.assumeTrue(probe.waitFor() == 0);
		try (var shaders = Files.list(Path.of("target/classes/shaders"))) {
			for (final Path shader : shaders.toList()) {
				final Process check = new ProcessBuilder("glslangValidator", shader.toString()).redirectErrorStream(true).start();
				final String output = new String(check.getInputStream().readAllBytes());
				Assertions.assertEquals(0, check.waitFor(), shader + ": " + output);
			}
		}
	}

	@org.junit.jupiter.api.Test
	void customExtensionAndNormalJavaCoexist() throws Exception {
		org.junit.jupiter.api.Assertions.assertEquals(42, NormalJava.answer());
		org.junit.jupiter.api.Assertions.assertEquals(42, Extra.answer());
		org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("src/gen/java/demo/Extra.java")));
	}

}
