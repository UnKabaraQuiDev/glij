package lu.kbra.glij.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import lu.kbra.glij.binding.BindingContext;
import lu.kbra.glij.binding.BindingPlan;
import lu.kbra.glij.binding.BindingRegistry;
import lu.kbra.glij.binding.ParameterSpec;
import lu.kbra.glij.binding.ShaderTarget;
import lu.kbra.glij.compiler.ast.JavaAstAnalyzer;
import lu.kbra.glij.compiler.ast.MethodInfo;
import lu.kbra.glij.compiler.generation.ShaderClassGenerator;
import lu.kbra.glij.compiler.shader.IncludeResolver;
import lu.kbra.glij.compiler.shader.ShaderFunctionResolver;
import lu.kbra.glij.compiler.shader.ShaderSourceGenerator;
import lu.kbra.glij.compiler.source.MethodRegionFinder;

/** Build-time frontend. Extended sources (.glij by default) never reach javac. */
public final class GLiJCompiler {
    /** Default extension for source files containing embedded GPU code (without a dot). */
    public static final String DEFAULT_EXTENSION = "glij";
    /** Default Maven source folder, shared by ordinary Java and GLiJ sources. */
    public static final String DEFAULT_SOURCE_DIRECTORY = "src/main/java";
    /** Default output root for the transformed Java classes. */
    public static final String DEFAULT_JAVA_OUTPUT_DIRECTORY = "src/gen/java";
    /** Default shader resource output root. */
    public static final String DEFAULT_RESOURCE_OUTPUT_DIRECTORY = "src/gen/resources";

	private final BindingRegistry registry;

	public GLiJCompiler(final BindingRegistry registry) {
		this.registry = registry;
	}

    /** Compile all configured mappings, preserving each mapping's extension overrides. */
    public CompilationResult compile(final SourceLayout layout) throws IOException {
        final List<Path> javaFiles = new ArrayList<>();
        final List<Path> shaderFiles = new ArrayList<>();
        for (SourceDirectory directory : layout.directories()) {
            final CompilationResult result = this.compile(directory.source(), directory.output(),
                    layout.resourcesOutput(), directory.fileExtensions().toArray(String[]::new));
            javaFiles.addAll(result.javaFiles());
            shaderFiles.addAll(result.shaderFiles());
        }
        return new CompilationResult(List.copyOf(javaFiles), List.copyOf(shaderFiles));
    }

	public CompilationResult compile(final Path sourceRoot, final Path javaRoot, final Path resourcesRoot) throws IOException {
		return this.compile(sourceRoot, javaRoot, resourcesRoot, DEFAULT_EXTENSION);
	}

	/** Scans mixed Java source directories; only files with selected extensions are transformed. */
	public CompilationResult compile(final Path sourceRoot, final Path javaRoot, final Path resourcesRoot, final String... extensions)
			throws IOException {
		final List<Path> javaFiles = new ArrayList<>();
		final List<Path> shaderFiles = new ArrayList<>();
		if (!Files.isDirectory(sourceRoot)) {
			return new CompilationResult(List.of(), List.of());
		}
		final Map<Path, SourceUnit> units = GLiJCompiler.readUnits(sourceRoot, extensions);
		final ShaderFunctionResolver resolver = GLiJCompiler.functions(units, sourceRoot);
		for (final var unit : units.entrySet()) {
			final CompilationResult compiled = this.compileUnit(unit.getKey(), unit.getValue(), javaRoot, resourcesRoot, resolver);
			javaFiles.addAll(compiled.javaFiles());
			shaderFiles.addAll(compiled.shaderFiles());
		}
		return new CompilationResult(List.copyOf(javaFiles), List.copyOf(shaderFiles));
	}

	public CompilationResult compileFile(final Path file, final Path sourceRoot, final Path javaRoot, final Path resourcesRoot)
			throws IOException {
		return this.compileFile(file, sourceRoot, javaRoot, resourcesRoot, DEFAULT_EXTENSION);
	}

	public CompilationResult
			compileFile(final Path file, final Path sourceRoot, final Path javaRoot, final Path resourcesRoot, final String... extensions)
					throws IOException {
		final Map<Path, SourceUnit> units = GLiJCompiler.readUnits(sourceRoot, extensions);
		final SourceUnit unit = units.get(file);
		if (unit == null) {
			throw new IOException("Not a configured GLiJ file under source root: " + file);
		}
		return this.compileUnit(file, unit, javaRoot, resourcesRoot, GLiJCompiler.functions(units, sourceRoot));
	}

	private static Map<Path, SourceUnit> readUnits(final Path sourceRoot, final String... extensions) throws IOException {
		final java.util.Set<String> accepted = GLiJCompiler.normalizeExtensions(extensions);
		final Map<Path, SourceUnit> units = new java.util.LinkedHashMap<>();
		try (Stream<Path> files = Files.walk(sourceRoot)) {
			for (final Path file : files
					.filter(f -> accepted.stream()
							.anyMatch(ext -> f.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith("." + ext)))
					.sorted()
					.toList()) {
				units.put(file, GLiJCompiler.readUnit(file));
			}
		}
		return units;
	}

	private static ShaderFunctionResolver functions(final Map<Path, SourceUnit> units, final Path sourceRoot) {
		final Map<String, List<MethodInfo>> functions = new HashMap<>();
		for (final SourceUnit unit : units.values()) {
			for (final MethodInfo method : unit.methods()) {
				if (method.has("ShaderFunction")) {
					final String owner = method.packageName().isEmpty() ? method.className()
							: method.packageName() + "." + method.className();
					functions.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(method);
				}
			}
		}
		return new ShaderFunctionResolver(functions, new IncludeResolver(sourceRoot));
	}

	/** Accept extensions with or without the leading dot, separated by commas or whitespace. */
	public static java.util.Set<String> normalizeExtensions(final String... extensions) {
		final java.util.Set<String> result = new java.util.LinkedHashSet<>();
		if (extensions != null) {
			for (final String group : extensions) {
				if (group == null) {
					continue;
				}
				for (final String token : group.split("[,;\\s]+")) {
					if (token.isBlank()) {
						continue;
					}
					final String normalized = token.replaceFirst("^\\.", "").toLowerCase(java.util.Locale.ROOT);
					if (!normalized.matches("[a-z][a-z0-9_-]*")) {
						throw new IllegalArgumentException("Invalid GLiJ extension: " + token);
					}
					if ("java".equals(normalized)) {
						throw new IllegalArgumentException(
								"A .java GLiJ extension would be compiled twice by javac; use a separate extension such as .glij");
					}
					result.add(normalized);
				}
			}
		}
		if (result.isEmpty()) {
			result.add(DEFAULT_EXTENSION);
		}
		return java.util.Collections.unmodifiableSet(result);
	}


    /**
     * Project an unsaved .glij buffer into Java without writing files or compiling GLSL.
     * Uses the same Java delegate generator and shader-region finder as the on-disk build.
     * Only non-shader spans are reversible in the resulting source map.
     *
     * @throws IllegalArgumentException when an incomplete edit temporarily cannot be parsed
     */
    public static JavaSourceProjection projectJava(final String fileName, final String source) {
        final var regions = MethodRegionFinder.find(source);
        if (regions.isEmpty()) return JavaSourceProjection.identity(source);
        final var methods = JavaAstAnalyzer.analyzeLenient(fileName, MethodRegionFinder.maskBodies(source, regions), regions);
        final List<JavaSourceProjection.Replacement> replacements = new ArrayList<>();
        for (final MethodInfo method : methods) {
            final String javaBody;
            if (method.has("ShaderFunction")) {
                javaBody = "{ throw new UnsupportedOperationException(\"GPU-only shader function\"); }";
            } else {
                final String wrapper = method.className() + "_" + method.name()
                        + (method.has("GlCompute") ? "_ComputeShader" : "_RenderShader");
                javaBody = ShaderClassGenerator.delegate(method, wrapper);
            }
            // The closing brace belongs to the synthetic Java region as well.
            replacements.add(new JavaSourceProjection.Replacement(method.region().openBrace(),
                    method.region().closeBrace() + 1,
                    MethodRegionFinder.preserveLines(source, method.region(), javaBody)));
        }
        return JavaSourceProjection.create(source, replacements);
    }

	private static String generatedJavaFilename(final Path file) {
		final String filename = file.getFileName().toString();
		return filename.substring(0, filename.lastIndexOf('.')) + ".java";
	}

	private static SourceUnit readUnit(final Path file) throws IOException {
		final String source = Files.readString(file);
		final var regions = MethodRegionFinder.find(source);
		final String masked = MethodRegionFinder.maskBodies(source, regions);
		final String originalFileName = GLiJCompiler.generatedJavaFilename(file);
		final List<MethodInfo> methods = JavaAstAnalyzer.analyze(originalFileName, masked, regions);
		return new SourceUnit(source, methods);
	}

	private CompilationResult compileUnit(
			final Path file,
			final SourceUnit unit,
			final Path javaRoot,
			final Path resourcesRoot,
			final ShaderFunctionResolver resolver)
			throws IOException {
		final String source = unit.source();
		final String originalFileName = GLiJCompiler.generatedJavaFilename(file);
		final List<MethodInfo> methods = unit.methods();
		final List<Path> javaFiles = new ArrayList<>();
		final List<Path> shaderFiles = new ArrayList<>();
		final List<Replacement> replacements = new ArrayList<>();
		for (final MethodInfo method : methods) {
			if (method.has("ShaderFunction")) {
				if (method.has("GlCompute") || method.has("GlRender")) {
					throw new IllegalArgumentException("@ShaderFunction cannot also be an entry point: " + method.name());
				}
				replacements.add(new Replacement(method.region().openBrace(),
						method.region().closeBrace(),
						MethodRegionFinder.preserveLines(source,
								method.region(),
								"{ throw new UnsupportedOperationException(\"GPU-only shader function\"); }")));
				continue;
			}
			final boolean compute = method.has("GlCompute");
			final ShaderTarget target = compute ? ShaderTarget.OPENGL_COMPUTE : ShaderTarget.OPENGL_RENDER;
			final BindingContext bindings = new BindingContext();
			final List<BindingPlan> plans = new ArrayList<>();
			for (final ParameterSpec parameter : method.parameters()) {
				if (parameter.has("LocalSize")) {
					throw new IllegalArgumentException("Runtime @LocalSize specialization is not implemented for " + method.name());
				}
				if (!compute && (parameter.has("Blocking") || parameter.has("GroupSize"))) {
					throw new IllegalArgumentException("@Blocking/@GroupSize are compute-only parameters");
				}
				plans.add(this.registry.plan(parameter, target, bindings));
			}

			final String wrapper = method.className() + "_" + method.name() + (compute ? "_ComputeShader" : "_RenderShader");
			final String packageDir = method.packageName().replace('.', '/');
			String folder = method.annotationValue("Location", "value", "").replace('\\', '/');
			folder = folder.replaceAll("^/+|/+$", "");
			if (folder.contains("..")) {
				throw new IllegalArgumentException("Invalid shader location: " + folder);
			}
			final String base = "shaders/" + (folder.isBlank() ? "" : folder + "/") + wrapper;
			final String classText = ShaderClassGenerator.javaClass(method, plans, wrapper, base);
			final Path classPath = javaRoot.resolve(packageDir).resolve(wrapper + ".java");
			GLiJCompiler.write(classPath, classText);
			javaFiles.add(classPath);

			if (compute) {
				final String shader = ShaderSourceGenerator.compute(method, plans);
				final Path path = resourcesRoot.resolve(base + ".comp");
				GLiJCompiler.write(path, resolver.apply(shader, method));
				shaderFiles.add(path);
			} else {
				final var stages = ShaderSourceGenerator.render(method, plans);
				for (int i = 0; i < 2; i++) {
					final String extension = i == 0 ? ".vert" : ".frag";
					final Path path = resourcesRoot.resolve(base + extension);
					GLiJCompiler.write(path, resolver.apply(stages.get(i), method));
					shaderFiles.add(path);
				}
			}
			replacements.add(new Replacement(method.region().openBrace(),
					method.region().closeBrace(),
					MethodRegionFinder.preserveLines(source, method.region(), ShaderClassGenerator.delegate(method, wrapper))));
		}
		final StringBuilder rewritten = new StringBuilder(source);
		replacements.stream()
				.sorted(Comparator.comparingInt(Replacement::start).reversed())
				.forEach(replacement -> rewritten.replace(replacement.start(), replacement.end() + 1, replacement.value()));
		final String packageDir = methods.isEmpty() ? GLiJCompiler.packageDirectoryFromSource(source)
				: methods.getFirst().packageName().replace('.', '/');
		final Path javaFile = javaRoot.resolve(packageDir).resolve(originalFileName);
		GLiJCompiler.write(javaFile, rewritten.toString());
		javaFiles.add(javaFile);
		return new CompilationResult(List.copyOf(javaFiles), List.copyOf(shaderFiles));
	}

	private static String packageDirectoryFromSource(final String source) {
		final var matcher = java.util.regex.Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(source);
		return matcher.find() ? matcher.group(1).replace('.', '/') : "";
	}

	private static void write(final Path file, final String source) throws IOException {
		Files.createDirectories(file.getParent());
		if (!Files.exists(file) || !Files.readString(file).equals(source)) {
			Files.writeString(file, source);
		}
	}

	private record Replacement(int start, int end, String value) {
	}

	private record SourceUnit(String source, List<MethodInfo> methods) {
	}
}
