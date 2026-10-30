package lu.kbra.glij.eclipse.editor;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;

/** Syntax coloring and Ctrl+Space proposals for GLSL/GLiJ. */
public final class GLiJViewerConfiguration extends SourceViewerConfiguration {
	private final GLiJColors colors;
	private final GLiJEditor editor;

	public GLiJViewerConfiguration(final GLiJColors colors, final GLiJEditor editor) {
		this.colors = colors;
		this.editor = editor;
	}

	@Override
	public IPresentationReconciler getPresentationReconciler(final ISourceViewer viewer) {
		final PresentationReconciler reconciler = new PresentationReconciler();
		final DefaultDamagerRepairer repairer = new DefaultDamagerRepairer(new GpuRuleScanner(this.colors));
		reconciler.setDamager(repairer, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.setRepairer(repairer, IDocument.DEFAULT_CONTENT_TYPE);
		return reconciler;
	}

	@Override
	public IContentAssistant getContentAssistant(final ISourceViewer viewer) {
		final ContentAssistant assistant = new ContentAssistant();
		assistant.setContentAssistProcessor(new GpuCompletionProcessor(), IDocument.DEFAULT_CONTENT_TYPE);
		assistant.enableAutoActivation(true);
		assistant.setAutoActivationDelay(200);
		return assistant;
	}
}
