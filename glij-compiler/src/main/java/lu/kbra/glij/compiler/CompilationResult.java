package lu.kbra.glij.compiler;

import java.nio.file.Path;
import java.util.List;

public record CompilationResult(List<Path> javaFiles, List<Path> shaderFiles) {
}
