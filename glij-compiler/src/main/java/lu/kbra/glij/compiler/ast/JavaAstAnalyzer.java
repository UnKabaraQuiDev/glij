package lu.kbra.glij.compiler.ast;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import lu.kbra.glij.binding.ParameterSpec;
import lu.kbra.glij.compiler.source.MethodRegion;

/** Uses the real Java 21 parser only after shader bodies are masked. */
public final class JavaAstAnalyzer {
	private JavaAstAnalyzer() {
	}

    /** Editor-only parsing: keep discoverable shader methods while Java is temporarily invalid. */
    public static List<MethodInfo> analyzeLenient(final String filename, final String masked, final List<MethodRegion> regions) {
        return analyze(filename, masked, regions, false);
    }

    public static List<MethodInfo> analyze(final String filename, final String masked, final List<MethodRegion> regions) {
        return analyze(filename, masked, regions, true);
    }

    private static List<MethodInfo> analyze(final String filename, final String masked,
            final List<MethodRegion> regions, final boolean strict) {
		final var compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new IllegalStateException("JDK 21 compiler required (not a JRE)");
		}
		final var diagnostics = new DiagnosticCollector<JavaFileObject>();
		final JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///" + filename), JavaFileObject.Kind.SOURCE) {
			@Override
			public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
				return masked;
			}
		};
		if (Runtime.version().feature() < 21) {
			throw new IllegalStateException("GLiJ requires JDK 21 or newer");
		}
		final JavacTask task = (JavacTask) compiler.getTask(null,
				null,
				diagnostics,
				List.of("--release", Integer.toString(Runtime.version().feature()), "-proc:none"),
				null,
				List.of(file));
		final List<MethodInfo> methods = new ArrayList<>();
		try {
			final var units = task.parse();
			final Trees trees = Trees.instance(task);
			final SourcePositions positions = trees.getSourcePositions();
			for (final CompilationUnitTree unit : units) {
				final String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
				final List<String> imports = unit.getImports().stream().map(Object::toString).toList();
				new TreePathScanner<Void, Void>() {
					private final List<String> classes = new ArrayList<>();

					@Override
					public Void visitClass(final ClassTree tree, final Void ignored) {
						this.classes.add(tree.getSimpleName().toString());
						try {
							return super.visitClass(tree, ignored);
						} finally {
							this.classes.removeLast();
						}
					}

					@Override
					public Void visitMethod(final MethodTree tree, final Void ignored) {
						if (tree.getBody() == null) {
							return super.visitMethod(tree, ignored);
						}
						final long offset = positions.getStartPosition(unit, tree.getBody());
						for (final MethodRegion region : regions) {
							if (offset != region.openBrace()) {
								continue;
							}
							if (this.classes.size() != 1) {
								throw new IllegalArgumentException("Nested shader classes are not supported yet: " + this.classes);
							}
							final List<ParameterSpec> params = new ArrayList<>();
							for (final VariableTree variable : tree.getParameters()) {
								params.add(new ParameterSpec(variable.getName().toString(),
										variable.getType().toString(),
										JavaAstAnalyzer.readAnnotations(variable.getModifiers().getAnnotations())));
							}
							methods.add(new MethodInfo(packageName,
									this.classes.getFirst(),
									tree.getName().toString(),
									tree.getReturnType().toString(),
									imports,
									List.copyOf(params),
									JavaAstAnalyzer.readAnnotations(tree.getModifiers().getAnnotations()),
									region));
						}
						return super.visitMethod(tree, ignored);
					}
				}.scan(unit, null);
			}
		} catch (final IOException exception) {
			throw new IllegalStateException("Java parsing failed", exception);
		}
		for (final var diagnostic : diagnostics.getDiagnostics()) {
			if (strict && diagnostic.getKind() == Diagnostic.Kind.ERROR) {
				throw new IllegalArgumentException(filename + ":" + diagnostic.getLineNumber() + ": " + diagnostic.getMessage(null));
			}
		}
		if (methods.size() != regions.size()) {
			throw new IllegalArgumentException("Found " + regions.size() + " shader bodies but only " + methods.size() + " Java methods");
		}
		return methods;
	}

	public static Map<String, Map<String, String>> readAnnotations(final List<? extends AnnotationTree> annotations) {
		final Map<String, Map<String, String>> result = new LinkedHashMap<>();
		for (final AnnotationTree annotation : annotations) {
			String name = annotation.getAnnotationType().toString();
			if (name.contains(".")) {
				name = name.substring(name.lastIndexOf('.') + 1);
			}
			final Map<String, String> attributes = new HashMap<>();
			for (final ExpressionTree argument : annotation.getArguments()) {
				if (argument instanceof final AssignmentTree assignment) {
					attributes.put(assignment.getVariable().toString(), JavaAstAnalyzer.unquote(assignment.getExpression().toString()));
				} else {
					attributes.put("value", JavaAstAnalyzer.unquote(argument.toString()));
				}
			}
			String key = name;
			for (int index = 2; result.containsKey(key); index++) {
				key = name + "#" + index;
			}
			result.put(key, Map.copyOf(attributes));
		}
		return java.util.Collections.unmodifiableMap(result);
	}

	private static String unquote(final String value) {
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}
}
