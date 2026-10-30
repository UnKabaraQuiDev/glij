package lu.kbra.glij.tests;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import lu.kbra.glij.binding.BindingContext;
import lu.kbra.glij.binding.BindingPlan;
import lu.kbra.glij.binding.BindingRegistry;
import lu.kbra.glij.binding.ParameterSpec;
import lu.kbra.glij.binding.ShaderTarget;
import lu.kbra.glij.engine.EngineBindingProvider;

public class BindingRegistryTest {
	private final BindingRegistry registry = new BindingRegistry();

	public BindingRegistryTest() {
		new EngineBindingProvider().register(this.registry);
	}

	@Test
	void usesSeparateBufferAndSamplerNamespaces() {
		final BindingContext context = new BindingContext();
		final ParameterSpec first = new ParameterSpec("input", "FloatAttribArray", Map.of("Layout", Map.of("value", "std430")));
		final ParameterSpec second = new ParameterSpec("output", "FloatAttribArray", Map.of("Access", Map.of("value", "WRITE_ONLY")));
		final ParameterSpec texture = new ParameterSpec("color", "SingleTexture", Map.of());
		Assertions.assertTrue(this.registry.plan(first, ShaderTarget.OPENGL_COMPUTE, context).shaderDeclaration().contains("binding = 0"));
		Assertions.assertTrue(this.registry.plan(second, ShaderTarget.OPENGL_COMPUTE, context).shaderDeclaration().contains("binding = 1"));
		Assertions.assertTrue(this.registry.plan(texture, ShaderTarget.OPENGL_RENDER, context).javaBinding().contains("bind(0)"));
	}

	@Test
	void respectsExplicitBindingAndRejectsDuplicates() {
		final BindingContext context = new BindingContext();
		final ParameterSpec first = new ParameterSpec("first", "FloatAttribArray", Map.of("Binding", Map.of("value", "3")));
		final ParameterSpec second = new ParameterSpec("second", "FloatAttribArray", Map.of("Binding", Map.of("value", "3")));
		this.registry.plan(first, ShaderTarget.OPENGL_COMPUTE, context);
		Assertions.assertThrows(IllegalArgumentException.class, () -> this.registry.plan(second, ShaderTarget.OPENGL_COMPUTE, context));
	}

	@Test
	void unknownTypeReportsError() {
		Assertions.assertThrows(IllegalArgumentException.class,
				() -> this.registry
						.plan(new ParameterSpec("value", "UnknownBuffer", Map.of()), ShaderTarget.OPENGL_COMPUTE, new BindingContext()));
	}

	@Test
	void externalLibraryCanRegisterBindingWithoutCompilerChanges() {
		this.registry.rule("ExternalParticleBuffer")
				.forPipeline(ShaderTarget.OPENGL_COMPUTE)
				.priority(90)
				.binding((parameter, target, context) -> new BindingPlan("buffer Particles { float " + parameter.name() + "[]; };\n",
						parameter.name() + ".bind();\n",
						"",
						null))
				.register();
		final var plan = this.registry.plan(new ParameterSpec("particles", "ExternalParticleBuffer", Map.of()),
				ShaderTarget.OPENGL_COMPUTE,
				new BindingContext());
		Assertions.assertTrue(plan.shaderDeclaration().contains("particles[]"));
		Assertions.assertTrue(plan.javaBinding().contains("particles.bind()"));
	}

	@Test
	void annotatedTexturesUseSamplerBindingRatherThanScalarUniform() {
		final var texture = new ParameterSpec("image",
				"SingleTexture",
				Map.of("Uniform", Map.of("name", "albedo"), "GpuType", Map.of("value", "sampler3D")));
		final var plan = this.registry.plan(texture, ShaderTarget.OPENGL_RENDER, new BindingContext());
		Assertions.assertEquals("uniform sampler3D albedo;\n", plan.shaderDeclaration());
		Assertions.assertTrue(plan.javaBinding().contains("image.bind(0)"));
		Assertions.assertTrue(plan.javaBinding().contains("setUniform(\"albedo\", 0)"));
	}
}
