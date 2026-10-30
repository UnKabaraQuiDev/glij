package lu.kbra.glij.eclipse.editor;

import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.ide.IDE;

import lu.kbra.glij.eclipse.GLiJPlugin;

/**
 * Eclipse cannot register arbitrary editor extensions at runtime. Instead we set per-file editor
 * associations for matching workspace resources. This does not replace the default editor for
 * unrelated files or override external files.
 */
public final class EditorAssociations {
	private EditorAssociations() {
	}

	public static void schedule() {
		new WorkspaceJob("Associate configured GLiJ file extensions") {
			@Override
			public IStatus runInWorkspace(final IProgressMonitor monitor) {
				final Set<String> extensions = GLiJPlugin.configuredExtensions();
				try {
					for (final IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
						if (!project.isAccessible()) {
							continue;
						}
						project.accept(resource -> {
							if (resource instanceof final IFile file) {
								final String suffix = file.getFileExtension();
								final boolean matched = suffix != null && extensions.contains(suffix.toLowerCase(java.util.Locale.ROOT));
								final String current = file.getPersistentProperty(IDE.EDITOR_KEY);
								if (matched && !GLiJPlugin.EDITOR.equals(current)) {
									IDE.setDefaultEditor(file, GLiJPlugin.EDITOR);
								} else if (!matched && GLiJPlugin.EDITOR.equals(current)) {
									file.setPersistentProperty(IDE.EDITOR_KEY, null);
								}
							}
							return true;
						});
					}
					return Status.OK_STATUS;
				} catch (final CoreException e) {
					return new Status(IStatus.ERROR, GLiJPlugin.ID, "Cannot update GLiJ editor associations", e);
				}
			}
		}.schedule();
	}
}
