package lu.kbra.glij.compiler;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Compiler layout shared by the Maven goal, Eclipse builder, and diagnostic mapper. */
public record SourceLayout(List<SourceDirectory> directories, Path resourcesOutput) {
    public SourceLayout {
        directories = List.copyOf(directories);
        if (directories.isEmpty()) throw new IllegalArgumentException("At least one directory must be configured");
        resourcesOutput = resourcesOutput.toAbsolutePath().normalize();
        Set<Path> outputs = new HashSet<>();
        for (SourceDirectory directory : directories) {
            if (!outputs.add(directory.output())) {
                throw new IllegalArgumentException("Generated Java output reused by multiple directories: " + directory.output());
            }
        }
        for (int i = 0; i < directories.size(); i++) {
            for (int j = i + 1; j < directories.size(); j++) {
                Path a = directories.get(i).source();
                Path b = directories.get(j).source();
                if (a.startsWith(b) || b.startsWith(a)) {
                    throw new IllegalArgumentException("Overlapping GLiJ source directories: " + a + " / " + b);
                }
            }
        }
    }

    public static SourceLayout defaults(Path projectRoot) {
        Path root = projectRoot.toAbsolutePath().normalize();
        return new SourceLayout(List.of(new SourceDirectory(
                root.resolve(GLiJCompiler.DEFAULT_SOURCE_DIRECTORY),
                root.resolve(GLiJCompiler.DEFAULT_JAVA_OUTPUT_DIRECTORY),
                GLiJCompiler.normalizeExtensions(GLiJCompiler.DEFAULT_EXTENSION))),
                root.resolve(GLiJCompiler.DEFAULT_RESOURCE_OUTPUT_DIRECTORY));
    }

    public Optional<SourceDirectory> forSource(Path sourceFile) {
        return directories.stream().filter(directory -> directory.accepts(sourceFile)).findFirst();
    }
}
