package lu.kbra.glij.eclipse.editor;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.ITextViewerExtension4;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.handlers.IHandlerService;

import lu.kbra.glij.eclipse.GLiJPlugin;

/**
 * JDT's real Java editor, with a targeted GLSL presentation overlay.
 *
 * <p>
 * JDT owns Java partitions, annotation colors, Java preferences, and editor actions. Only
 * explicitly detected GPU function ranges receive GLiJ colors. Extending a JDT internal class
 * is intentional, but must be checked when updating Eclipse/JDT.
 *
 * <p>
 * A private JDT working copy of the generated Java source handles completion, import organization
 * and live Java diagnostics. Only original Java spans are mapped back to this editor.
 */
@SuppressWarnings("restriction")
public final class GLiJEditor extends CompilationUnitEditor {

	private final GLiJColors gpuColors = new GLiJColors();
	private JavaWorkingCopyBridge workingCopy;
    private IHandlerActivation organizeImportsHandler;
	private final GpuPresentationOverlay gpuOverlay = new GpuPresentationOverlay(this.gpuColors,
			() -> this.getSourceViewer() == null ? null : this.getSourceViewer().getDocument());

	private final IPropertyChangeListener gpuPreferencesChanged = event -> {
		if (GLiJColors.STYLE.equals(event.getProperty()) || event.getProperty().startsWith("color.")) {
			this.gpuColors.reset();
			final ISourceViewer viewer = this.getSourceViewer();
			if (viewer != null)
				viewer.invalidateTextPresentation();
		}
	};

	@Override
	protected void setPreferenceStore(final IPreferenceStore store) {
		super.setPreferenceStore(store);
		if (store != null) {
			this.setSourceViewerConfiguration(
					new GLiJJavaViewerConfiguration(JavaUI.getColorManager(), store, this, IJavaPartitions.JAVA_PARTITIONING));
		}
	}

    IDocument activeDocument() {
        return this.getSourceViewer() == null ? null : this.getSourceViewer().getDocument();
    }

    JavaWorkingCopyBridge workingCopy() { return this.workingCopy; }

    @Override
    protected void createActions() {
        super.createActions();
        // Override the inherited action because JDT's original action resolves the editor's
        // .glij input as a non-Java file, not as our generated working copy.
        Action organize = new Action("Organize Imports") {
            @Override public void run() {
                if (workingCopy != null) workingCopy.organizeImports();
            }
        };
        organize.setActionDefinitionId(IJavaEditorActionDefinitionIds.ORGANIZE_IMPORTS);
        this.setAction("OrganizeImports", organize);
    }

	@Override
	public void createPartControl(final Composite parent) {
		super.createPartControl(parent);
        this.workingCopy = JavaWorkingCopyBridge.attach(this);
        // Register in the active editor site's handler scope as well as the JDT action
        // table. This covers the command/key binding path used by Ctrl+Shift+O.
        IHandlerService handlers = this.getSite().getService(IHandlerService.class);
        if (handlers != null) {
            this.organizeImportsHandler = handlers.activateHandler(IJavaEditorActionDefinitionIds.ORGANIZE_IMPORTS,
                    new AbstractHandler() {
                        @Override public Object execute(ExecutionEvent event) throws ExecutionException {
                            if (workingCopy != null) workingCopy.organizeImports();
                            return null;
                        }
                    });
        }
		if (this.getSourceViewer() instanceof final ITextViewerExtension4 textViewer) {
			textViewer.addTextPresentationListener(this.gpuOverlay);
			this.getSourceViewer().invalidateTextPresentation();
		}
		GLiJPlugin.getDefault().getPreferenceStore().addPropertyChangeListener(this.gpuPreferencesChanged);
	}

	@Override
	public void dispose() {
        if (this.organizeImportsHandler != null) {
            IHandlerService handlers = this.getSite().getService(IHandlerService.class);
            if (handlers != null) handlers.deactivateHandler(this.organizeImportsHandler);
            this.organizeImportsHandler = null;
        }
        if (this.workingCopy != null) {
            this.workingCopy.close();
            this.workingCopy = null;
        }
		GLiJPlugin.getDefault().getPreferenceStore().removePropertyChangeListener(this.gpuPreferencesChanged);
		if (this.getSourceViewer() instanceof final ITextViewerExtension4 textViewer) {
			textViewer.removeTextPresentationListener(this.gpuOverlay);
		}
		super.dispose();
		this.gpuColors.dispose();
	}

}
