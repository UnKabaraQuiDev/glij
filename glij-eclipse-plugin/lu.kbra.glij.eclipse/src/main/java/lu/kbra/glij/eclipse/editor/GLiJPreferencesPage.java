package lu.kbra.glij.eclipse.editor;

import org.eclipse.jface.preference.ColorFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import lu.kbra.glij.compiler.GLiJCompiler;
import lu.kbra.glij.eclipse.GLiJPlugin;

/** Window > Preferences > GLiJ: presets and per-token custom colors. */
public final class GLiJPreferencesPage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	public GLiJPreferencesPage() {
		super(FieldEditorPreferencePage.GRID);
	}

	@Override
	public void init(final IWorkbench workbench) {
		this.setPreferenceStore(GLiJPlugin.getDefault().getPreferenceStore());
		this.setDescription("GLiJ file associations and GLSL colors. Reopen editors after changing a palette.");
	}

	@Override
	protected void createFieldEditors() {
		this.addField(new StringFieldEditor(GLiJPlugin.EDITOR_EXTENSIONS,
				"Editor extensions (comma-separated; no .java)",
				this.getFieldEditorParent()) {
			@Override
			protected boolean doCheckState() {
				try {
					GLiJCompiler.normalizeExtensions(this.getStringValue());
					return true;
				} catch (final IllegalArgumentException invalid) {
					this.setErrorMessage(invalid.getMessage());
					return false;
				}
			}
		});
		this.addField(new ComboFieldEditor(GLiJColors.STYLE,
				"Color style",
				new String[][] {
						{ "Follow Java editor", GLiJColors.FOLLOW_JAVA },
						{ "Dark GLSL", GLiJColors.DARK },
						{ "Light GLSL", GLiJColors.LIGHT },
						{ "Custom", GLiJColors.CUSTOM } },
				this.getFieldEditorParent()));
		for (final String key : GLiJColors.CATEGORIES) {
			this.addField(new ColorFieldEditor(GLiJColors.colorKey(key), "Custom: " + key, this.getFieldEditorParent()));
		}
	}

	@Override
	public boolean performOk() {
		final boolean ok = super.performOk();
		if (ok) {
			EditorAssociations.schedule();
		}
		return ok;
	}
}
