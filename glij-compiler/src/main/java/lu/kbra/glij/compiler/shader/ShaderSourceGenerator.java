package lu.kbra.glij.compiler.shader;

import java.util.List;

import lu.kbra.glij.binding.BindingPlan;
import lu.kbra.glij.binding.GpuNames;
import lu.kbra.glij.binding.ParameterSpec;
import lu.kbra.glij.compiler.ast.MethodInfo;
import lu.kbra.glij.compiler.source.MethodRegionFinder;

public final class ShaderSourceGenerator {
	private ShaderSourceGenerator() {
	}

	public static String compute(final MethodInfo method, final List<BindingPlan> bindings) {
		final StringBuilder shader = ShaderSourceGenerator.header(method);
		final int x = Integer.parseInt(method.annotationValue("LocalSize", "x", "1"));
		final int y = Integer.parseInt(method.annotationValue("LocalSize", "y", "1"));
		final int z = Integer.parseInt(method.annotationValue("LocalSize", "z", "1"));
		if (x <= 0 || y <= 0 || z <= 0) {
			throw new IllegalArgumentException("Local sizes must be positive");
		}
		shader.append("layout(local_size_x = ")
				.append(x)
				.append(", local_size_y = ")
				.append(y)
				.append(", local_size_z = ")
				.append(z)
				.append(") in;\n");
		for (final BindingPlan plan : bindings) {
			shader.append(plan.shaderDeclaration());
		}
		shader.append('\n')
				.append(ShaderSourceGenerator.rewriteIdentifiers(method.region().shaderBody().trim(), method.parameters()))
				.append('\n');
		if (!method.region().shaderBody().contains("void main")) {
			throw new IllegalArgumentException("Compute shader must contain void main()");
		}
		return shader.toString();
	}

	public static List<String> render(final MethodInfo method, final List<BindingPlan> bindings) {
		final var stages = ShaderBodyParser.renderingStages(method.region().shaderBody());
		for (final var output : stages.get(0).parameters()) {
			if (!"out".equals(output.qualifier())) {
				continue;
			}
			final boolean linked = stages.get(1)
					.parameters()
					.stream()
					.anyMatch(input -> "in".equals(input.qualifier()) && input.name().equals(output.name())
							&& input.type().equals(output.type()));
			if (!linked) {
				throw new IllegalArgumentException("Unmatched vertex output: " + output.name());
			}
		}
		return stages.stream().map(stage -> {
			final StringBuilder shader = ShaderSourceGenerator.header(method);
			for (final var arg : stage.parameters()) {
				shader.append(arg.declaration());
			}
			for (final BindingPlan plan : bindings) {
				shader.append(plan.shaderDeclaration());
			}
			shader.append('\n').append(ShaderSourceGenerator.rewriteIdentifiers(stage.body().trim(), method.parameters())).append('\n');
			if (!stage.body().contains("void main")) {
				throw new IllegalArgumentException(stage.name() + " shader must contain void main()");
			}
			return shader.toString();
		}).toList();
	}

	private static StringBuilder header(final MethodInfo method) {
		final String kind = method.has("GlCompute") ? "GlCompute" : "GlRender";
		final String version = method.annotationValue(kind, "version", "460");
		final String profile = method.annotationValue(kind, "profile", "core");
		return new StringBuilder("#version ").append(version).append(' ').append(profile).append("\n\n");
	}

	private static String rewriteIdentifiers(String body, final List<ParameterSpec> parameters) {
		for (final ParameterSpec p : parameters) {
			String declared = p.has("Uniform") ? p.property("Uniform", "name", "") : "";
			if (declared.isBlank()) {
				declared = p.name();
			}
			final String replacement = GpuNames.shaderName(declared);
			if (replacement.equals(p.name())) {
				continue;
			}
			final String mask = MethodRegionFinder.maskNonCode(body);
			final var matcher = java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(p.name()) + "\\b").matcher(mask);
			final var found = new java.util.ArrayList<Integer>();
			while (matcher.find()) {
				found.add(matcher.start());
			}
			final StringBuilder rewritten = new StringBuilder(body);
			for (int i = found.size() - 1; i >= 0; i--) {
				final int start = found.get(i);
				rewritten.replace(start, start + p.name().length(), replacement);
			}
			body = rewritten.toString();
		}
		return body;
	}
}
