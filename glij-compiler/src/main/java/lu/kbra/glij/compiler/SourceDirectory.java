package lu.kbra.glij.compiler;

import java.nio.file.Path;
import java.util.Set;

/** A source-to-generated-Java mapping; resource output is shared across mappings. */
public record SourceDirectory(Path source, Path output, Set<String> fileExtensions) {
    public SourceDirectory {
        if (source == null || output == null) {
            throw new IllegalArgumentException("Source and output paths are required");
        }
        source = source.toAbsolutePath().normalize();
        output = output.toAbsolutePath().normalize();
        fileExtensions = GLiJCompiler.normalizeExtensions(
                (fileExtensions == null ? Set.<String>of() : fileExtensions).toArray(String[]::new));
        if (source.equals(output) || source.startsWith(output) || output.startsWith(source)) {
            throw new IllegalArgumentException("Source and output folders must not overlap: " + source + " / " + output);
        }
    }

    public boolean accepts(Path file) {
        if (file == null || !file.toAbsolutePath().normalize().startsWith(source)) return false;
        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return fileExtensions.stream().anyMatch(extension -> name.endsWith("." + extension));
    }

    /** The source relative to this mapping, rather than a fixed number of path segments. */
    public Path relativePath(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(source)) throw new IllegalArgumentException("Outside source directory: " + file);
        return source.relativize(normalized);
    }

    /** Java output is laid out by package, matching the GLiJ compiler. */
    public Path generatedJavaFor(Path sourceFile, CharSequence sourceText) {
        if (!accepts(sourceFile)) throw new IllegalArgumentException("Not a GLiJ source: " + sourceFile);
        var matcher = java.util.regex.Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(sourceText);
        String packagePath = matcher.find() ? matcher.group(1).replace('.', '/') : "";
        String name = sourceFile.getFileName().toString();
        return output.resolve(packagePath).resolve(name.substring(0, name.lastIndexOf('.')) + ".java");
    }
}
