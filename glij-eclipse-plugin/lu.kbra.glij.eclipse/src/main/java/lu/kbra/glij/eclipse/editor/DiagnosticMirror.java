package lu.kbra.glij.eclipse.editor;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import lu.kbra.glij.compiler.JavaSourceProjection;
import lu.kbra.glij.compiler.GLiJCompiler;
import lu.kbra.glij.compiler.SourceDirectory;
import lu.kbra.glij.compiler.SourceLayout;
import lu.kbra.glij.eclipse.EclipseSourceLayouts;
import lu.kbra.glij.eclipse.GLiJPlugin;

/** Mirrors JDT errors from generated Java onto matching original GLiJ files. */
public final class DiagnosticMirror {
	private static final AtomicBoolean QUEUED = new AtomicBoolean();
	private static final String ORIGIN = "glij.origin";
	private static final String JDT_ORIGIN = "jdt";
	private static final String JDT_MARKER = "org.eclipse.jdt.core.problem";

	private DiagnosticMirror() {
	}

	public static void schedule() {
		if (!QUEUED.compareAndSet(false, true))
			return;
		new WorkspaceJob("Mirror GLiJ diagnostics") {
			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor) {
				try {
					for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
						if (!project.isAccessible() || !project.hasNature(GLiJPlugin.NATURE))
							continue;
						mirrorProject(project, EclipseSourceLayouts.read(project));
					}
					return Status.OK_STATUS;
				} catch (CoreException | IOException failure) {
					return new Status(IStatus.ERROR, GLiJPlugin.ID, "Cannot mirror GLiJ diagnostics", failure);
				} finally {
					QUEUED.set(false);
				}
			}
		}.schedule();
	}

	private static void mirrorProject(IProject project, SourceLayout layout) throws CoreException {
		// Remove old forwarded markers even when a source mapping was deleted or changed.
		for (IMarker marker : project.findMarkers(GLiJPlugin.PROBLEM, true, IResource.DEPTH_INFINITE)) {
			if (JDT_ORIGIN.equals(marker.getAttribute(ORIGIN, "")))
				marker.delete();
		}
		for (SourceDirectory mapping : layout.directories()) {
			org.eclipse.core.resources.IFolder folder = EclipseSourceLayouts.workspaceFolder(project, mapping.source());
			if (folder == null || !folder.exists())
				continue;
			folder.accept(resource -> {
				if (resource instanceof IFile file && mapping.accepts(file.getLocation().toFile().toPath())) {
					mirrorFile(project, file, mapping);
				}
				return true;
			});
		}
	}

	private static void mirrorFile(IProject project, IFile source, SourceDirectory mapping) throws CoreException {
		try {
			final java.nio.file.Path originalPath = source.getLocation().toFile().toPath();
			final String originalText = Files.readString(originalPath);
			final java.nio.file.Path generatedPath = mapping.generatedJavaFor(originalPath, originalText);
			final IFile generated = EclipseSourceLayouts.workspaceFile(project, generatedPath);
			if (generated == null || !generated.exists())
				return;
			// A generated file is derived from the saved source; matching the projection
			// prevents using stale columns after an incremental build.
			JavaSourceProjection projection;
			boolean exact;
			try {
				projection = GLiJCompiler.projectJava(generatedPath.getFileName().toString(), originalText);
				exact = projection.javaSource().equals(Files.readString(generatedPath));
			} catch (IllegalArgumentException failure) {
				// Keep coarse line mapping if the GLiJ parser itself cannot parse the file.
				exact = false;
				projection = null;
			}
			for (IMarker fromJava : generated.findMarkers(JDT_MARKER, true, IResource.DEPTH_ZERO)) {
				final int javaStart = fromJava.getAttribute(IMarker.CHAR_START, -1);
				final int javaEnd = fromJava.getAttribute(IMarker.CHAR_END, -1);
				JavaSourceProjection.Range mapped = null;
				if (exact && javaStart >= 0 && javaEnd >= javaStart) {
					var range = projection.toOriginalRange(javaStart, javaEnd);
					if (range.isEmpty())
						continue; // Diagnostic refers to synthetic delegate, not user code.
					mapped = range.get();
				}
				final IMarker marker = source.createMarker(GLiJPlugin.PROBLEM);
				marker.setAttribute(IMarker.MESSAGE, fromJava.getAttribute(IMarker.MESSAGE, ""));
				marker.setAttribute(IMarker.SEVERITY, fromJava.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR));
				marker.setAttribute(IMarker.LOCATION, fromJava.getAttribute(IMarker.LOCATION, ""));
				if (mapped != null) {
					marker.setAttribute(IMarker.CHAR_START, mapped.start());
					marker.setAttribute(IMarker.CHAR_END, Math.max(mapped.start() + 1, mapped.end()));
					marker.setAttribute(IMarker.LINE_NUMBER, lineAt(originalText, mapped.start()));
				} else {
					final int line = fromJava.getAttribute(IMarker.LINE_NUMBER, 1);
					marker.setAttribute(IMarker.LINE_NUMBER, line);
					int from = 0;
					for (int n = 1; n < line && from < originalText.length(); n++) {
						int next = originalText.indexOf('\n', from);
						from = next < 0 ? originalText.length() : next + 1;
					}
					if (from < originalText.length()) {
						int end = originalText.indexOf('\n', from);
						if (end < 0)
							end = originalText.length();
						marker.setAttribute(IMarker.CHAR_START, from);
						marker.setAttribute(IMarker.CHAR_END, Math.max(from + 1, end));
					}
				}
				marker.setAttribute(ORIGIN, JDT_ORIGIN);
			}
		} catch (IOException failure) {
			throw new CoreException(
					new Status(IStatus.ERROR, GLiJPlugin.ID, "Cannot map generated Java for " + source.getFullPath(), failure));
		}
	}

	private static int lineAt(String text, int offset) {
		int line = 1;
		for (int i = 0; i < Math.min(offset, text.length()); i++)
			if (text.charAt(i) == '\n')
				line++;
		return line;
	}
}
