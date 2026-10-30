package lu.kbra.glij.engine;

import lu.kbra.glij.binding.BindingContext;
import lu.kbra.glij.binding.BindingPlan;
import lu.kbra.glij.binding.BindingProvider;
import lu.kbra.glij.binding.BindingRegistry;
import lu.kbra.glij.binding.GpuNames;
import lu.kbra.glij.binding.ParameterSpec;
import lu.kbra.glij.binding.ShaderBindingRule;
import lu.kbra.glij.binding.ShaderTarget;

/**
 * Build-time binding rules for StandaloneGameEngine 0.2. No engine classes are loaded by the
 * compiler.
 */
public final class EngineBindingProvider implements BindingProvider {
	private static final String GL = "lu.kbra.standalone.gameengine.generated.gl_wrapper.GL_W";

	@Override
	public void register(final BindingRegistry registry) {
		registry.register(new ControlRule());
		registry.register(new UniformRule());
		registry.register(new TextureRule());
		registry.register(new ArrayRule());
		registry.register(new MeshRule());
	}

	private static String gpuType(final String javaType) {
		final String simple = javaType.substring(javaType.lastIndexOf('.') + 1);
		return switch (simple) {
		case "boolean", "Boolean" -> "bool";
		case "int", "Integer" -> "int";
		case "float", "Float" -> "float";
		case "Vector2f", "Vector2fc" -> "vec2";
		case "Vector3f", "Vector3fc" -> "vec3";
		case "Vector4f", "Vector4fc" -> "vec4";
		case "Vector2i", "Vector2ic" -> "ivec2";
		case "Vector3i", "Vector3ic" -> "ivec3";
		case "Vector4i", "Vector4ic" -> "ivec4";
		case "Matrix4f", "Matrix4fc", "Mat4fAttribArray" -> "mat4";
		case "Matrix3f", "Matrix3fc" -> "mat3";
		case "Mat3x2fAttribArray" -> "mat3x2";
		case "FloatAttribArray" -> "float";
		case "IntAttribArray" -> "int";
		case "UIntAttribArray" -> "uint";
		case "UByteAttribArray" -> "uint";
		case "Vec2fAttribArray" -> "vec2";
		case "Vec3fAttribArray", "Vec3fPaddedAttribArray" -> "vec3";
		case "Vec4fAttribArray", "SyntheticVec4fAttribArray" -> "vec4";
		case "Vec3iAttribArray" -> "ivec3";
		case "Vec4iAttribArray" -> "ivec4";
		default -> throw new IllegalArgumentException("Unknown GPU type: " + javaType);
		};
	}

	private static class ControlRule implements ShaderBindingRule {
		@Override
		public boolean supports(final ParameterSpec p, final ShaderTarget target) {
			return p.has("Blocking") || p.has("GroupSize") || p.has("CacheManager") || p.has("ShaderCache") || p.has("LocalSize");
		}

		@Override
		public int priority() {
			return 200;
		}

		@Override
		public BindingPlan plan(final ParameterSpec p, final ShaderTarget target, final BindingContext c) {
			return BindingPlan.empty();
		}
	}

	private static class UniformRule implements ShaderBindingRule {
		@Override
		public boolean supports(final ParameterSpec p, final ShaderTarget target) {
			return p.has("Uniform");
		}

		@Override
		public int priority() {
			return 100;
		}

		@Override
		public BindingPlan plan(final ParameterSpec p, final ShaderTarget target, final BindingContext c) {
			String name = p.property("Uniform", "name", "");
			if (name.isBlank()) {
				name = p.name();
			}
			name = GpuNames.shaderName(name);
			final String defaultValue = p.property("Uniform", "defaultValue", "");
			String type = p.property("GpuType", "value", "");
			if (type.isBlank()) {
				type = EngineBindingProvider.gpuType(p.javaType());
			}
			final String declaration = "uniform " + type + " " + name + (defaultValue.isBlank() ? "" : " = " + defaultValue) + ";\n";
			return new BindingPlan(declaration, "super.setUniform(\"" + name + "\", " + p.name() + ");\n", "", name);
		}
	}

	private static class ArrayRule implements ShaderBindingRule {
		@Override
		public boolean supports(final ParameterSpec p, final ShaderTarget target) {
			return p.simpleType().endsWith("AttribArray") && !p.has("Uniform");
		}

		@Override
		public int priority() {
			return 40;
		}

		@Override
		public BindingPlan plan(final ParameterSpec p, final ShaderTarget target, final BindingContext c) {
			if (target == ShaderTarget.OPENGL_RENDER && !p.has("Layout")) {
				return new BindingPlan("", p.name() + ".bind();\n", "", null);
			}
			final String layout = p.property("Layout", "value", "std430");
			String access = p.property("Access", "value", "READ_WRITE");
			access = access.substring(access.lastIndexOf('.') + 1);
			final String qualifier = switch (access) {
			case "READ_ONLY" -> "readonly ";
			case "WRITE_ONLY" -> "writeonly ";
			default -> "";
			};
			final int index = c.storageBinding(p);
			final String shaderName = GpuNames.shaderName(p.name());
			final String glsl = "layout(" + layout + ", binding = " + index + ") " + qualifier + "buffer "
					+ Character.toUpperCase(p.name().charAt(0)) + p.name().substring(1) + "Buffer { "
					+ EngineBindingProvider.gpuType(p.javaType()) + " " + shaderName + "[]; };\n";
			final String java = EngineBindingProvider.GL + ".glBindBufferBase(" + EngineBindingProvider.GL + ".GL_SHADER_STORAGE_BUFFER, "
					+ index + ", " + p.name() + ".getGlId());\n";
			return new BindingPlan(glsl, java, "", null);
		}
	}

	private static class TextureRule implements ShaderBindingRule {
		@Override
		public boolean supports(final ParameterSpec p, final ShaderTarget target) {
			return p.simpleType().endsWith("Texture");
		}

		@Override
		public int priority() {
			return 120;
		}

		@Override
		public BindingPlan plan(final ParameterSpec p, final ShaderTarget target, final BindingContext c) {
			final int slot = c.textureBinding(p);
			String name = p.property("Uniform", "name", "");
			if (name.isBlank()) {
				name = p.name();
			}
			final String shaderName = GpuNames.shaderName(name);
			final String gpuType = p.property("GpuType", "value", "sampler2D");
			return new BindingPlan("uniform " + gpuType + " " + shaderName + ";\n",
					p.name() + ".bind(" + slot + ");\nsuper.setUniform(\"" + shaderName + "\", " + slot + ");\n",
					"",
					shaderName);
		}
	}

	private static class MeshRule implements ShaderBindingRule {
		@Override
		public boolean supports(final ParameterSpec p, final ShaderTarget target) {
			return target == ShaderTarget.OPENGL_RENDER && "Mesh".equals(p.simpleType());
		}

		@Override
		public int priority() {
			return 70;
		}

		@Override
		public BindingPlan plan(final ParameterSpec p, final ShaderTarget target, final BindingContext c) {
			final String mesh = p.name();
			final String draw = mesh + ".bind();\n" + "if (" + mesh + ".getIndicesCount() > 0) {\n" + "  " + EngineBindingProvider.GL
					+ ".glDrawElements(" + EngineBindingProvider.GL + ".GL_TRIANGLES, " + mesh + ".getIndicesCount(), "
					+ EngineBindingProvider.GL + ".GL_UNSIGNED_INT, 0L);\n" + "} else {\n" + "  " + EngineBindingProvider.GL
					+ ".glDrawArrays(" + EngineBindingProvider.GL + ".GL_TRIANGLES, 0, " + mesh + ".getVertexCount());\n" + "}\n" + mesh
					+ ".unbind();\n";
			return new BindingPlan("", "", draw, null);
		}
	}
}
