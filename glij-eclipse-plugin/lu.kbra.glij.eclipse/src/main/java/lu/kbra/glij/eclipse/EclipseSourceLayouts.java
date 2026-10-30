package lu.kbra.glij.eclipse;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;

import lu.kbra.glij.compiler.MavenSourceLayout;
import lu.kbra.glij.compiler.SourceLayout;

/** Filesystem-to-workspace translation for any configured source/output directory. */
public final class EclipseSourceLayouts {
    private EclipseSourceLayouts() { }
    private record Cached(FileTime modified, SourceLayout layout) { }
    private static final Map<IProject, Cached> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    public static SourceLayout read(IProject project) throws IOException {
        if (project.getLocation() == null) throw new IOException("Project has no local filesystem location: " + project.getName());
        Path root = project.getLocation().toFile().toPath();
        Path pom = root.resolve("pom.xml");
        FileTime modified = Files.exists(pom) ? Files.getLastModifiedTime(pom) : null;
        Cached previous = CACHE.get(project);
        if (previous != null && java.util.Objects.equals(previous.modified(), modified)) {
            return previous.layout();
        }
        SourceLayout layout = MavenSourceLayout.read(root);
        CACHE.put(project, new Cached(modified, layout));
        return layout;
    }

    public static IFile workspaceFile(IProject project, Path file) {
        if (project.getLocation() == null) return null;
        Path root = project.getLocation().toFile().toPath().toAbsolutePath().normalize();
        Path target = file.toAbsolutePath().normalize();
        if (!target.startsWith(root)) return null;
        return project.getFile(new org.eclipse.core.runtime.Path(root.relativize(target).toString().replace('\\', '/')));
    }

    public static org.eclipse.core.resources.IFolder workspaceFolder(IProject project, Path folder) {
        if (project.getLocation() == null) return null;
        Path root = project.getLocation().toFile().toPath().toAbsolutePath().normalize();
        Path target = folder.toAbsolutePath().normalize();
        if (!target.startsWith(root) || target.equals(root)) return null;
        return project.getFolder(new org.eclipse.core.runtime.Path(root.relativize(target).toString().replace('\\', '/')));
    }
}
