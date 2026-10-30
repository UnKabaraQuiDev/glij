package demo.bindings;

import lu.kbra.glij.binding.BindingPlan;
import lu.kbra.glij.binding.BindingProvider;
import lu.kbra.glij.binding.BindingRegistry;
import lu.kbra.glij.binding.GpuNames;
import lu.kbra.glij.binding.ShaderTarget;

/** An independent plugin JAR can add handling for an application-specific resource. */
public final class CustomBindingProvider implements BindingProvider {
	@Override
	public void register(final BindingRegistry registry) {
		registry.rule("CustomResource")
				.forPipeline(ShaderTarget.OPENGL_RENDER)
				.priority(85)
				.binding((param, target, context) -> new BindingPlan("", param.name() + ".bind();\n", "", null))
				.register();
		registry.rule("ImageResource").priority(130).binding((param, target, context) -> {
			final String annotated = param.property("Uniform", "name", "");
			final String name = GpuNames.shaderName(annotated.isBlank() ? param.name() : annotated);
			final int slot = context.textureBinding(param);
			return new BindingPlan("uniform sampler2D " + name + ";\n",
					param.name() + ".bindUniform(this, \"" + name + "\", " + slot + ");\n",
					"",
					name);
		}).register();
	}
}
