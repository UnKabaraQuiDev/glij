package lu.kbra.glij.compiler.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lu.kbra.glij.binding.ParameterSpec;
import lu.kbra.glij.compiler.ast.MethodInfo;

/** Resolves references to GPU-only methods from the same or another .glij source. */
public final class ShaderFunctionResolver {
	private final Map<String, List<MethodInfo>> functions;
	private final IncludeResolver files;

	public ShaderFunctionResolver(final Map<String, List<MethodInfo>> functions, final IncludeResolver files) {
		this.functions = Map.copyOf(functions);
		this.files = files;
	}

	public String apply(final String shader, final MethodInfo caller) throws IOException {
		final StringBuilder declarations = new StringBuilder();
		final Set<String> emitted = new LinkedHashSet<>();
		for (final var annotation : caller.annotations().entrySet()) {
			if (!ShaderFunctionResolver.isInclude(annotation.getKey())) {
				continue;
			}
			this.append(annotation.getValue(), caller, declarations, emitted, new HashSet<>());
		}
		final int firstLine = shader.indexOf('\n');
		if (firstLine == -1) {
			throw new IllegalArgumentException("Shader is missing a #version header");
		}
		return this.files.resolve(shader.substring(0, firstLine + 1) + declarations + shader.substring(firstLine + 1));
	}

	private void append(
			final Map<String, String> annotation,
			final MethodInfo caller,
			final StringBuilder output,
			final Set<String> emitted,
			final Set<String> active)
			throws IOException {
		final String path = annotation.getOrDefault("path", "");
		final String type = annotation.getOrDefault("type", annotation.getOrDefault("value", ""));
		final String name = annotation.getOrDefault("methodName", "");
		if (!path.isBlank()) {
			if (!type.isBlank() || !name.isBlank()) {
				throw new IllegalArgumentException("@Include must specify either path or type/methodName");
			}
			if (emitted.add("file:" + path)) {
				output.append(this.files.resolveFile(path)).append('\n');
			}
			return;
		}
		if (name.isBlank() || type.isBlank() || "Void.class".equals(type)) {
			throw new IllegalArgumentException("@Include requires path or type=... and methodName=...");
		}
		final String owner = this.resolveOwner(type.replaceFirst("\\.class$", ""), caller);
		final List<MethodInfo> matches = this.functions.getOrDefault(owner, List.of())
				.stream()
				.filter(f -> f.name().equals(name) && f.has("ShaderFunction"))
				.toList();
		if (matches.size() != 1) {
			throw new IllegalArgumentException("Expected one @ShaderFunction " + owner + "." + name + "; found " + matches.size());
		}
		final String identifier = owner + "#" + name;
		if (emitted.contains(identifier)) {
			return;
		}
		if (!active.add(identifier)) {
			throw new IllegalArgumentException("Cyclic @Include: " + identifier);
		}
		final MethodInfo function = matches.getFirst();
		try {
			for (final var dependency : function.annotations().entrySet()) {
				if (ShaderFunctionResolver.isInclude(dependency.getKey())) {
					this.append(dependency.getValue(), function, output, emitted, active);
				}
			}
			output.append(ShaderFunctionResolver.functionType(function.returnType(), function.annotationValue("GpuType", "value", "")))
					.append(' ')
					.append(function.name())
					.append('(');
			final List<String> args = new ArrayList<>();
			for (final ParameterSpec parameter : function.parameters()) {
				args.add(ShaderFunctionResolver.functionType(parameter.javaType(), parameter.property("GpuType", "value", "")) + " "
						+ parameter.name());
			}
			output.append(String.join(", ", args)).append(") {\n").append(function.region().shaderBody().strip()).append("\n}\n");
			emitted.add(identifier);
		} finally {
			active.remove(identifier);
		}
	}

	private String resolveOwner(final String requested, final MethodInfo caller) {
		if (requested.contains(".")) {
			if (this.functions.containsKey(requested)) {
				return requested;
			}
			throw new IllegalArgumentException("Unknown @Include class: " + requested);
		}
		for (final String imported : caller.imports()) {
			final String statement = imported.replace("import", "").replace("static", "").replace(";", "").trim();
			if (statement.endsWith("." + requested) && this.functions.containsKey(statement)) {
				return statement;
			}
		}
		final String samePackage = caller.packageName().isBlank() ? requested : caller.packageName() + "." + requested;
		if (this.functions.containsKey(samePackage)) {
			return samePackage;
		}
		final List<String> candidates = this.functions.keySet()
				.stream()
				.filter(key -> key.endsWith("." + requested) || key.equals(requested))
				.toList();
		if (candidates.size() == 1) {
			return candidates.getFirst();
		}
		throw new IllegalArgumentException("Unknown or ambiguous @Include class: " + requested);
	}

	private static String functionType(final String javaType, final String override) {
		if (!override.isBlank()) {
			return override;
		}
		return switch (javaType.substring(javaType.lastIndexOf('.') + 1)) {
		case "void", "boolean", "int", "float", "double" -> javaType;
		case "long" -> "int64_t";
		case "Vector2f", "Vector2fc" -> "vec2";
		case "Vector3f", "Vector3fc" -> "vec3";
		case "Vector4f", "Vector4fc" -> "vec4";
		case "Vector2i", "Vector2ic" -> "ivec2";
		case "Vector3i", "Vector3ic" -> "ivec3";
		case "Matrix4f", "Matrix4fc" -> "mat4";
		default -> throw new IllegalArgumentException("@ShaderFunction needs @GpuType for " + javaType);
		};
	}

	private static boolean isInclude(final String name) {
		return "Include".equals(name) || name.startsWith("Include#") || "StaticInclude".equals(name) || name.startsWith("StaticInclude#");
	}
}
