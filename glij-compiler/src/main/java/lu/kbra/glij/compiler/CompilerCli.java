package lu.kbra.glij.compiler;

import java.nio.file.Path;

import lu.kbra.glij.binding.BindingRegistry;

/**
 * Standalone entrypoint for testing/custom integrations. The engine adapter must be on the
 * classpath.
 */
public final class CompilerCli {
	private CompilerCli() {
	}

	public static void main(final String[] args) throws Exception {
		if (args.length != 3) {
			System.err.println("Usage: CompilerCli <source-root> <generated-java-root> <generated-resource-root>");
			System.exit(2);
		}
		final BindingRegistry bindings = new BindingRegistry().discover(CompilerCli.class.getClassLoader());
		final CompilationResult result = new GLiJCompiler(bindings).compile(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
		System.out
				.println("Generated " + result.javaFiles().size() + " Java files and " + result.shaderFiles().size() + " shader resources");
	}
}
