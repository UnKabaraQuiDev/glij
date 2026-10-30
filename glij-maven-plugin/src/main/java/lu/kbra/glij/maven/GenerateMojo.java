package lu.kbra.glij.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import lu.kbra.glij.binding.BindingRegistry;
import lu.kbra.glij.compiler.GLiJCompiler;
import lu.kbra.glij.compiler.SourceDirectory;
import lu.kbra.glij.compiler.SourceLayout;

/**
 * Generates Java and GLSL from GLiJ sources before regular Java compilation.
 * <p>
 * When {@code directories} is omitted, the default mapping is
 * {@code src/main/java -> src/gen/java}. Ordinary {@code .java} files are compiled as usual and are
 * never transformed.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public final class GenerateMojo extends AbstractMojo {

	/**
	 * Default GLiJ extensions, with or without dots, separated by commas. Each
	 * {@code directories.directory.fileExtensions} can override this list. Default:
	 * {@link GLiJCompiler#DEFAULT_EXTENSION}.
	 */
	@Parameter(property = "glij.fileExtensions")
	private String fileExtensions = GLiJCompiler.DEFAULT_EXTENSION;

	/**
	 * Source/output mappings. Each entry accepts {@code source}, {@code output}, and optional
	 * {@code fileExtensions}. Omitting this list uses a single default mapping from
	 * {@code src/main/java} to {@code src/gen/java}.
	 */
	@Parameter
	private List<SourceDirectoryOption> directories;

	/**
	 * Generated shader resource folder, shared by all directory mappings. Default:
	 * {@code ${project.basedir}/src/gen/resources}.
	 */
	@Parameter(property = "glij.generatedResourcesDirectory", defaultValue = "${project.basedir}/src/gen/resources")
	private File generatedResourcesDirectory;

	/** Base directory used to resolve default and relative source/output paths. */
	@Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
	private File basedir;

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@Override
	public void execute() throws MojoExecutionException {
		try {
			final SourceLayout layout = this.sourceLayout();
			final BindingRegistry bindings = new BindingRegistry().discover(this.getClass().getClassLoader());
			final GLiJCompiler compiler = new GLiJCompiler(bindings);
			for (final SourceDirectory directory : layout.directories()) {
				// Register even when the source directory has no GLiJ files yet.
				this.project.addCompileSourceRoot(directory.output().toString());
			}
			final var generated = compiler.compile(layout);
			final Resource resources = new Resource();
			resources.setDirectory(layout.resourcesOutput().toString());
			this.project.addResource(resources);
			this.getLog()
					.info("GLiJ: generated " + generated.javaFiles().size() + " Java files and " + generated.shaderFiles().size()
							+ " GLSL resources from " + layout.directories().size() + " source directories");
		} catch (IOException | IllegalArgumentException error) {
			throw new MojoExecutionException("GLiJ generation failed: " + error.getMessage(), error);
		}
	}

	/** Package-visible for Maven configuration regression tests. */
	SourceLayout sourceLayout() {
		final Path root = this.basedir == null ? this.project.getBasedir().toPath().toAbsolutePath().normalize()
				: this.basedir.toPath().toAbsolutePath().normalize();
		final String fallbackExtensions = this.fileExtensions == null || this.fileExtensions.isBlank() ? GLiJCompiler.DEFAULT_EXTENSION
				: this.fileExtensions;
		final List<SourceDirectory> resolved = new ArrayList<>();
		if (this.directories == null || this.directories.isEmpty()) {
			resolved.add(new SourceDirectory(root.resolve(GLiJCompiler.DEFAULT_SOURCE_DIRECTORY),
					root.resolve(GLiJCompiler.DEFAULT_JAVA_OUTPUT_DIRECTORY),
					GLiJCompiler.normalizeExtensions(fallbackExtensions)));
		} else {
			for (final SourceDirectoryOption option : this.directories) {
				if (option == null) {
					throw new IllegalArgumentException("Null <directory> entry");
				}
				final Path source = GenerateMojo.resolve(root, option.getSource(), GLiJCompiler.DEFAULT_SOURCE_DIRECTORY);
				final Path output = GenerateMojo.resolve(root, option.getOutput(), GLiJCompiler.DEFAULT_JAVA_OUTPUT_DIRECTORY);
				final String extensions = option.getFileExtensions() == null || option.getFileExtensions().isBlank() ? fallbackExtensions
						: option.getFileExtensions();
				resolved.add(new SourceDirectory(source, output, GLiJCompiler.normalizeExtensions(extensions)));
			}
		}
		final Path resources = GenerateMojo
				.resolve(root, this.generatedResourcesDirectory, GLiJCompiler.DEFAULT_RESOURCE_OUTPUT_DIRECTORY);
		return new SourceLayout(resolved, resources);
	}

	private static Path resolve(Path root, File configured, String defaultPath) {
		if (configured == null) {
			return root.resolve(defaultPath);
		}
		Path path = configured.toPath();
		return path.isAbsolute() ? path : root.resolve(path);
	}
}
