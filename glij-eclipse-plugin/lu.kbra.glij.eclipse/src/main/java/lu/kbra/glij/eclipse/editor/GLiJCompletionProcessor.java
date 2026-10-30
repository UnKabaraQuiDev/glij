package lu.kbra.glij.eclipse.editor;

import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

/** Routes Java positions to the existing JDT processor and shader positions to GLSL completion. */
final class GLiJCompletionProcessor implements IContentAssistProcessor {
	private static final ICompletionProposal[] NO_PROPOSALS = new ICompletionProposal[0];
	private final IContentAssistProcessor javaProcessor;
	private final IContentAssistProcessor gpuProcessor;
    private final GLiJEditor editor;

	GLiJCompletionProcessor(final IContentAssistProcessor javaProcessor, final IContentAssistProcessor gpuProcessor,
            final GLiJEditor editor) {
        this.editor = editor;
		this.javaProcessor = javaProcessor;
		this.gpuProcessor = gpuProcessor;
	}

	private boolean isGpu(final ITextViewer viewer, final int offset) {
		if (viewer == null || viewer.getDocument() == null)
			return false;
		final String source = viewer.getDocument().get();
		return offset >= 0 && offset <= source.length() && GpuHighlightRegions.contains(source, Math.max(0, offset - 1));
	}

	@Override
	public ICompletionProposal[] computeCompletionProposals(final ITextViewer viewer, final int offset) {
        if (isGpu(viewer, offset)) {
            final ICompletionProposal[] gpu = gpuProcessor.computeCompletionProposals(viewer, offset);
            return gpu == null ? NO_PROPOSALS : gpu;
        }
        final JavaWorkingCopyBridge bridge = editor.workingCopy();
        if (bridge != null) {
            final ICompletionProposal[] result = bridge.complete(offset);
            if (result.length != 0) return result;
        }
        // Retain JDT's normal processor as a fallback for projects without a generated file.
        if (javaProcessor == null) return NO_PROPOSALS;
        final ICompletionProposal[] result = javaProcessor.computeCompletionProposals(viewer, offset);
        return result == null ? NO_PROPOSALS : result;
	}

	@Override
	public IContextInformation[] computeContextInformation(final ITextViewer viewer, final int offset) {
		final IContentAssistProcessor processor = isGpu(viewer, offset) ? gpuProcessor : javaProcessor;
		return processor == null ? null : processor.computeContextInformation(viewer, offset);
	}

	@Override
	public char[] getCompletionProposalAutoActivationCharacters() {
		return javaProcessor == null ? null : javaProcessor.getCompletionProposalAutoActivationCharacters();
	}

	@Override
	public char[] getContextInformationAutoActivationCharacters() {
		return javaProcessor == null ? null : javaProcessor.getContextInformationAutoActivationCharacters();
	}

	@Override
	public String getErrorMessage() {
		final String gpuError = gpuProcessor.getErrorMessage();
		if (gpuError != null)
			return gpuError;
		return javaProcessor == null ? null : javaProcessor.getErrorMessage();
	}

	@Override
	public IContextInformationValidator getContextInformationValidator() {
		return javaProcessor == null ? gpuProcessor.getContextInformationValidator() : javaProcessor.getContextInformationValidator();
	}

}
