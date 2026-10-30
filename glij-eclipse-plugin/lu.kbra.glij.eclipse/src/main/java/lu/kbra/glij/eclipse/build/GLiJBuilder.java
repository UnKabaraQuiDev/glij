package lu.kbra.glij.eclipse.build;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import lu.kbra.glij.binding.BindingRegistry;
import lu.kbra.glij.compiler.GLiJCompiler;
import lu.kbra.glij.compiler.SourceDirectory;
import lu.kbra.glij.compiler.SourceLayout;
import lu.kbra.glij.eclipse.EclipseSourceLayouts;
import lu.kbra.glij.eclipse.GLiJPlugin;

/** Generates all configured source mappings before JDT compiles the generated Java files. */
public final class GLiJBuilder extends IncrementalProjectBuilder {
    @Override
    protected IProject[] build(int kind, java.util.Map<String, String> args, IProgressMonitor monitor)
            throws CoreException {
        IProject project = getProject();
        try {
            SourceLayout layout = EclipseSourceLayouts.read(project);
            GLiJCompiler compiler = new GLiJCompiler(
                    new BindingRegistry().discover(GLiJBuilder.class.getClassLoader()));
            compiler.compile(layout);
            // Refresh first: otherwise newly generated folders do not exist yet in
            // the Eclipse resource model, and cannot be registered with JDT.
            project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
            registerGeneratedRoots(project, layout, monitor);
        } catch (IOException | IllegalArgumentException failure) {
            throw new CoreException(new Status(IStatus.ERROR, GLiJPlugin.ID,
                    "GLiJ generation failed for " + project.getName(), failure));
        }
        return null;
    }

    @Override
    protected void clean(IProgressMonitor monitor) throws CoreException {
        // Generated sources are not removed on Eclipse Clean. This preserves JDT references
        // until the following build, and matches Maven's explicit clean lifecycle.
    }

    private static void registerGeneratedRoots(IProject project, SourceLayout layout, IProgressMonitor monitor)
            throws CoreException {
        if (!project.hasNature(JavaCore.NATURE_ID)) return;
        IJavaProject javaProject = JavaCore.create(project);
        List<IClasspathEntry> entries = new ArrayList<>(Arrays.asList(javaProject.getRawClasspath()));
        boolean updated = false;
        List<java.nio.file.Path> roots = new ArrayList<>();
        for (SourceDirectory mapping : layout.directories()) roots.add(mapping.output());
        roots.add(layout.resourcesOutput());
        for (java.nio.file.Path location : roots) {
            IFolder folder = EclipseSourceLayouts.workspaceFolder(project, location);
            if (folder == null) {
                throw new CoreException(new Status(IStatus.ERROR, GLiJPlugin.ID,
                        "Generated output must be inside the Eclipse project, or added as a linked source folder: "
                        + location));
            }
            if (!folder.exists()) continue;
            boolean exists = entries.stream().anyMatch(entry -> entry.getEntryKind() == IClasspathEntry.CPE_SOURCE
                    && entry.getPath().equals(folder.getFullPath()));
            if (!exists) {
                entries.add(JavaCore.newSourceEntry(folder.getFullPath()));
                updated = true;
            }
        }
        if (updated) javaProject.setRawClasspath(entries.toArray(IClasspathEntry[]::new), monitor);
    }
}
