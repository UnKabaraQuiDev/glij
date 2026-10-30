package lu.kbra.glij.eclipse;

import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import lu.kbra.glij.compiler.GLiJCompiler;
import lu.kbra.glij.eclipse.editor.DiagnosticMirror;
import lu.kbra.glij.eclipse.editor.EditorAssociations;
import lu.kbra.glij.eclipse.editor.GLiJColors;

/** The bundle activator owns user preferences and diagnostic forwarding. */
public final class GLiJPlugin extends AbstractUIPlugin {
	public static final String ID = "lu.kbra.glij.eclipse";
	public static final String NATURE = GLiJPlugin.ID + ".nature";
	public static final String BUILDER = GLiJPlugin.ID + ".builder";
	public static final String PROBLEM = GLiJPlugin.ID + ".problem";
	public static final String EDITOR = GLiJPlugin.ID + ".editor";
	public static final String EDITOR_EXTENSIONS = "editorExtensions";
	private static GLiJPlugin instance;
	private final IResourceChangeListener newSources = event -> {
		if (event.getDelta() == null) {
			return;
		}
		try {
			final boolean[] found = { false };
			event.getDelta().accept(delta -> {
				if (delta.getKind() == IResourceDelta.ADDED && delta.getResource() instanceof IFile file) {
					final String extension = file.getFileExtension();
					if (extension != null && GLiJPlugin.configuredExtensions().contains(extension.toLowerCase(java.util.Locale.ROOT))) {
						found[0] = true;
					}
				}
				return !found[0];
			});
			if (found[0]) {
				EditorAssociations.schedule();
			}
		} catch (final CoreException error) {
			GLiJPlugin.getDefault()
					.getLog()
					.log(new org.eclipse.core.runtime.Status(org.eclipse.core.runtime.IStatus.ERROR,
							GLiJPlugin.ID,
							"GLiJ editor association failed",
							error));
		}
	};

	private final IResourceChangeListener mirror = event -> {
		if (event.getType() == IResourceChangeEvent.POST_BUILD) {
			DiagnosticMirror.schedule();
		}
	};

	public static Set<String> configuredExtensions() {
		final GLiJPlugin instance = GLiJPlugin.getDefault();
		return GLiJCompiler.normalizeExtensions(
				instance == null ? GLiJCompiler.DEFAULT_EXTENSION : instance.getPreferenceStore().getString(GLiJPlugin.EDITOR_EXTENSIONS));
	}

	public static GLiJPlugin getDefault() {
		return GLiJPlugin.instance;
	}

	@Override
	public void start(final BundleContext context) throws Exception {
		super.start(context);
		GLiJPlugin.instance = this;
		final IPreferenceStore prefs = this.getPreferenceStore();
		prefs.setDefault(GLiJColors.STYLE, GLiJColors.FOLLOW_JAVA);
		prefs.setDefault(GLiJPlugin.EDITOR_EXTENSIONS, GLiJCompiler.DEFAULT_EXTENSION);
		for (final String key : GLiJColors.CATEGORIES) {
			prefs.setDefault(GLiJColors.colorKey(key), GLiJColors.defaultRgb(key));
		}
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this.mirror, IResourceChangeEvent.POST_BUILD);
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this.newSources, IResourceChangeEvent.POST_CHANGE);
		EditorAssociations.schedule();
	}

	@Override
	public void stop(final BundleContext context) throws Exception {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this.mirror);
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this.newSources);
		GLiJPlugin.instance = null;
		super.stop(context);
	}
}
