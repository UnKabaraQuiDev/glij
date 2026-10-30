package lu.kbra.glij.compiler.generation;

import java.util.List;
import java.util.Locale;

import javax.lang.model.element.Modifier;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import lu.kbra.glij.binding.BindingPlan;
import lu.kbra.glij.binding.ParameterSpec;
import lu.kbra.glij.compiler.ast.MethodInfo;

/** Generates JavaPoet wrappers and a same-line Java delegate for GLSL methods. */
public final class ShaderClassGenerator {
	private static final String ENGINE = "lu.kbra.standalone.gameengine";
	private static final String GL = ENGINE + ".generated.gl_wrapper.GL_W";

	private ShaderClassGenerator() {
	}

	public static boolean isControl(ParameterSpec p) {
		return p.has("Blocking") || p.has("GroupSize") || p.has("CacheManager") || p.has("ShaderCache") || p.has("LocalSize");
	}

	public static boolean isDraw(ParameterSpec p) {
		return "Mesh".equals(p.simpleType());
	}

	public static List<ParameterSpec> boundParameters(MethodInfo method) {
		return method.parameters().stream().filter(p -> !isControl(p) && !isDraw(p)).toList();
	}

	private static List<ParameterSpec> drawParameters(MethodInfo method) {
		return method.parameters().stream().filter(p -> !isControl(p) && isDraw(p)).toList();
	}

	private static String names(List<ParameterSpec> params) {
		return String.join(", ", params.stream().map(ParameterSpec::name).toList());
	}

	private static TypeName typeOf(String raw, MethodInfo method) {
		String type = raw.trim();
		return switch (type) {
		case "boolean" -> TypeName.BOOLEAN;
		case "byte" -> TypeName.BYTE;
		case "short" -> TypeName.SHORT;
		case "int" -> TypeName.INT;
		case "long" -> TypeName.LONG;
		case "float" -> TypeName.FLOAT;
		case "double" -> TypeName.DOUBLE;
		case "char" -> TypeName.CHAR;
		default -> {
			if (type.endsWith("[]"))
				yield com.squareup.javapoet.ArrayTypeName.of(typeOf(type.substring(0, type.length() - 2), method));
			// Preserve generic signatures even when written as imported simple types.
			if (type.contains("<")) {
				String base = type.substring(0, type.indexOf('<')).trim();
				String args = type.substring(type.indexOf('<') + 1, type.lastIndexOf('>'));
				String[] parts = args.split(",");
				TypeName[] resolved = new TypeName[parts.length];
				for (int i = 0; i < parts.length; i++)
					resolved[i] = typeOf(parts[i], method);
				yield com.squareup.javapoet.ParameterizedTypeName.get((ClassName) typeOf(base, method), resolved);
			}
			String fullyQualified = type;
			if (!type.contains("."))
				for (String imported : method.imports()) {
					String imp = imported.strip().replace("import ", "").replace("static ", "").replace(";", "").strip();
					if (imp.endsWith("." + type)) {
						fullyQualified = imp;
						break;
					}
				}
			yield ClassName.bestGuess(fullyQualified);
		}
		};
	}

	private static MethodSpec.Builder params(MethodSpec.Builder method, List<ParameterSpec> parameters, MethodInfo info) {
		for (ParameterSpec p : parameters)
			method.addParameter(typeOf(p.javaType(), info), p.name());
		return method;
	}

	private static String constantName(String name) {
		return name.replaceAll("([a-z])([A-Z])", "$1_$2").replaceAll("[^A-Za-z0-9_]", "_").toUpperCase(Locale.ROOT);
	}

	public static String javaClass(MethodInfo method, List<BindingPlan> plans, String name, String resourceBase) {
		boolean compute = method.has("GlCompute");
		ClassName parent = ClassName.bestGuess(ENGINE + ".graph.shader." + (compute ? "ComputeShader" : "RenderShader"));
		TypeSpec.Builder clazz = TypeSpec.classBuilder(name)
				.addModifiers(Modifier.PUBLIC, Modifier.FINAL)
				.superclass(parent)
				.addAnnotation(com.squareup.javapoet.AnnotationSpec.builder(ClassName.get("javax.annotation.processing", "Generated"))
						.addMember("value", "$S", "GLiJ")
						.build());
		if (compute) {
			clazz.addField(FieldSpec
					.builder(ClassName.bestGuess("org.joml.Vector3ic"), "LOCAL_SIZE", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
					.initializer("new $T($L, $L, $L)",
							ClassName.bestGuess("org.joml.Vector3i"),
							method.annotationValue("LocalSize", "x", "1"),
							method.annotationValue("LocalSize", "y", "1"),
							method.annotationValue("LocalSize", "z", "1"))
					.build());
		}
		for (BindingPlan plan : plans)
			if (plan.uniformName() != null) {
				clazz.addField(
						FieldSpec.builder(String.class, constantName(plan.uniformName()), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
								.initializer("$S", plan.uniformName())
								.build());
			}
		ClassName part = ClassName.bestGuess(ENGINE + ".graph.shader.part.AbstractShaderPart");
		MethodSpec.Builder constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
		if (compute) {
			ClassName computePart = ClassName.bestGuess(ENGINE + ".graph.shader.part.ComputeShaderPart");
			constructor.addStatement("super(($T) $T.load($S, getBaseBuildingDeps(LOCAL_SIZE)), LOCAL_SIZE)",
					computePart,
					part,
					"classpath:/" + resourceBase + ".comp");
		} else {
			constructor.addStatement("super($T.load($S), $T.load($S))",
					part,
					"classpath:/" + resourceBase + ".vert",
					part,
					"classpath:/" + resourceBase + ".frag");
		}
		clazz.addMethod(constructor.build());
		MethodSpec.Builder uniforms = MethodSpec.methodBuilder("createUniforms")
				.addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC)
				.returns(TypeName.VOID);
		if (compute)
			uniforms.addStatement("super.createUniforms()");
		for (BindingPlan plan : plans)
			if (plan.uniformName() != null)
				uniforms.addStatement("super.createUniform($S)", plan.uniformName());
		clazz.addMethod(uniforms.build());
		MethodSpec.Builder bind = params(MethodSpec.methodBuilder("bindResources").addModifiers(Modifier.PUBLIC).returns(TypeName.VOID),
				boundParameters(method),
				method);
		bind.addStatement("super.bind()");
		for (BindingPlan plan : plans)
			if (plan.javaBinding() != null && !plan.javaBinding().isBlank())
				bind.addCode("$L\n", plan.javaBinding());
		clazz.addMethod(bind.build());
		if (compute) {
			clazz.addMethod(computeDispatch(method));
		} else {
			MethodSpec.Builder render = params(MethodSpec.methodBuilder("render").addModifiers(Modifier.PUBLIC).returns(TypeName.VOID),
					drawParameters(method),
					method);
			for (BindingPlan plan : plans)
				if (plan.javaDraw() != null && !plan.javaDraw().isBlank())
					render.addCode("$L\n", plan.javaDraw());
			clazz.addMethod(render.build());
		}
		return JavaFile.builder(method.packageName(), clazz.build()).indent("    ").build().toString();
	}

	private static MethodSpec computeDispatch(MethodInfo method) {
		boolean hasBlocking = method.parameters().stream().anyMatch(p -> p.has("Blocking"));
		boolean hasGroups = method.parameters().stream().anyMatch(p -> p.has("GroupSize"));
		MethodSpec.Builder dispatch = MethodSpec.methodBuilder("dispatch")
				.addModifiers(Modifier.PUBLIC)
				.returns(ClassName.bestGuess("lu.kbra.glij.api.Semaphore"));
		if (hasBlocking)
			dispatch.addParameter(TypeName.BOOLEAN, "blocking");
		if (hasGroups)
			dispatch.addParameter(ClassName.bestGuess("org.joml.Vector3ic"), "groups");
		if (!hasBlocking)
			dispatch.addStatement("boolean blocking = $L", method.annotationValue("Blocking", "value", "true"));
		if (!hasGroups)
			dispatch.addStatement("$T groups = new $T($L, $L, $L)",
					ClassName.bestGuess("org.joml.Vector3ic"),
					ClassName.bestGuess("org.joml.Vector3i"),
					method.annotationValue("GroupSize", "x", "1"),
					method.annotationValue("GroupSize", "y", "1"),
					method.annotationValue("GroupSize", "z", "1"));
		ClassName gl = ClassName.bestGuess(GL);
		dispatch.addStatement("$T.glDispatchCompute(groups.x(), groups.y(), groups.z())", gl);
		dispatch.addStatement("$T.glMemoryBarrier($T.GL_SHADER_STORAGE_BARRIER_BIT)", gl, gl);
		dispatch.addStatement("long fence = $T.glFenceSync($T.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)", gl, gl);
		dispatch.beginControlFlow("if (blocking)");
		dispatch.addStatement("int status");
		dispatch.beginControlFlow("try");
		dispatch.addStatement("status = $T.glClientWaitSync(fence, $T.GL_SYNC_FLUSH_COMMANDS_BIT, (int) 1e12)", gl, gl);
		dispatch.nextControlFlow("finally");
		dispatch.addStatement("$T.glDeleteSync(fence)", gl);
		dispatch.endControlFlow();
		dispatch.beginControlFlow("if (status == $T.GL_WAIT_FAILED)", gl);
		dispatch.addStatement("throw new $T($S)", IllegalStateException.class, "GPU sync failed");
		dispatch.endControlFlow();
		dispatch.addStatement("return null");
		dispatch.endControlFlow();
		dispatch.addCode("return new $T(\n", ClassName.bestGuess("lu.kbra.glij.api.Semaphore"));
		dispatch.addCode("    () -> { int status = $T.glClientWaitSync(fence, 0, 0L);\n", gl);
		dispatch.addCode("        if (status == $T.GL_WAIT_FAILED) throw new IllegalStateException(\"GPU sync failed\");\n", gl);
		dispatch.addCode("        return status == $T.GL_ALREADY_SIGNALED || status == $T.GL_CONDITION_SATISFIED; },\n", gl, gl);
		dispatch.addCode("    () -> { int status = $T.glClientWaitSync(fence, $T.GL_SYNC_FLUSH_COMMANDS_BIT, (int) 1e12);\n", gl, gl);
		dispatch.addCode("        if (status == $T.GL_WAIT_FAILED) throw new IllegalStateException(\"GPU sync failed\"); },\n", gl);
		dispatch.addCode("    () -> $T.glDeleteSync(fence));\n", gl);
		return dispatch.build();
	}

	public static String delegate(MethodInfo method, String wrapper) {
		String cache = method.annotationValue("CacheManager", "value", "");
		if (cache.isBlank())
			cache = method.annotationValue("ShaderCache", "value", "");
		for (ParameterSpec p : method.parameters())
			if (p.has("CacheManager") || p.has("ShaderCache"))
				cache = p.name();
		if (cache.isBlank())
			throw new IllegalArgumentException("@CacheManager is required on " + method.name());
		StringBuilder code = new StringBuilder("{ final var __polyCache = ").append(cache)
				.append("; final ")
				.append(wrapper)
				.append(" __polyShader = __polyCache.getAbstractShader(")
				.append(wrapper)
				.append(".class, ")
				.append(wrapper)
				.append("::new); ");
		code.append("__polyShader.bindResources(").append(names(boundParameters(method))).append("); ");
		if (method.has("GlCompute")) {
			String blocking = method.annotationValue("Blocking", "value", "true");
			String groups = "new org.joml.Vector3i(" + method.annotationValue("GroupSize", "x", "1") + ", "
					+ method.annotationValue("GroupSize", "y", "1") + ", " + method.annotationValue("GroupSize", "z", "1") + ")";
			boolean hasBlocking = false, hasGroups = false;
			for (ParameterSpec p : method.parameters()) {
				if (p.has("Blocking")) {
					blocking = p.name();
					hasBlocking = true;
				}
				if (p.has("GroupSize")) {
					groups = p.name();
					hasGroups = true;
				}
			}
			String args = (hasBlocking ? blocking : "") + (hasBlocking && hasGroups ? ", " : "") + (hasGroups ? groups : "");
			if ("void".equals(method.returnType()))
				code.append("__polyShader.dispatch(").append(args).append("); ");
			else if ("Semaphore".equals(method.returnType()) || "lu.kbra.glij.api.Semaphore".equals(method.returnType()))
				code.append("return __polyShader.dispatch(").append(args).append("); ");
			else
				throw new IllegalArgumentException("Compute method must return void or lu.kbra.glij.api.Semaphore");
		} else {
			if (!"void".equals(method.returnType()))
				throw new IllegalArgumentException("Render method must return void");
			code.append("__polyShader.render(").append(names(drawParameters(method))).append("); ");
		}
		return code.append("}").toString();
	}
}
