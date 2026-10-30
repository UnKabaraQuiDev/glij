package lu.kbra.glij.eclipse.editor;

import org.eclipse.jdt.ui.text.IColorManager;
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.source.ISourceViewer;

/**
 * Retains JDT's full Java scanner, partitioning, completion and editor behavior; appends GLSL
 * suggestions only where the original document contains GLSL.
 *
 * JavaSourceViewerConfiguration is not declared as an extensible API by Eclipse; keep this override
 * minimal and retest for each target-platform upgrade.
 */
final class GLiJJavaViewerConfiguration extends JavaSourceViewerConfiguration {
	private final GLiJEditor editor;

	GLiJJavaViewerConfiguration(
			final IColorManager colors,
			final IPreferenceStore store,
			final GLiJEditor editor,
			final String partitioning) {
		super(colors, store, editor, partitioning);
        this.editor = editor;
	}

	@Override
	public IContentAssistant getContentAssistant(final ISourceViewer viewer) {
		final IContentAssistant assistant = super.getContentAssistant(viewer);

		if (assistant instanceof final ContentAssistant contentAssistant) {
			final IContentAssistProcessor javaProcessor = contentAssistant.getContentAssistProcessor(IDocument.DEFAULT_CONTENT_TYPE);

			contentAssistant.setContentAssistProcessor(new GLiJCompletionProcessor(javaProcessor, new GpuCompletionProcessor(), this.editor),
					IDocument.DEFAULT_CONTENT_TYPE);
		}

		return assistant;
	}

}
