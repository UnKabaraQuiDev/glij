package lu.kbra.glij.eclipse.editor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

/** Keyword, builtin and snippet completion without requiring a GLSL language server. */
public final class GpuCompletionProcessor implements IContentAssistProcessor {

	public GpuCompletionProcessor() {
	}

	private static final List<String> GLSL = List.of("void main() {\n    \n}",
			"gl_GlobalInvocationID",
			"gl_LocalInvocationID",
			"gl_WorkGroupID",
			"gl_NumWorkGroups",
			"gl_WorkGroupSize",
			"gl_Position",
			"gl_FragCoord",
			"gl_VertexID",
			"gl_InstanceID",
			"barrier()",
			"memoryBarrier()",
			"texture()",
			"normalize()",
			"dot()",
			"cross()",
			"determinant()",
			"inverse()",
			"clamp()",
			"mix()",
			"sqrt()",
			"vec2()",
			"vec3()",
			"vec4()",
			"mat3()",
			"mat4()",
			"sampler2D",
			"image2D",
			"layout(local_size_x = 64) in;",
			"layout(std430, binding = 0) buffer",
			"uint",
			"float",
			"int",
			"uniform",
			"readonly",
			"writeonly",
			"shared",
			"return",
			"discard");

	@Override
	public ICompletionProposal[] computeCompletionProposals(final ITextViewer viewer, final int offset) {
		final IDocument document = viewer.getDocument();
		final String source = document.get();
		if (offset < 0 || offset > source.length()) {
			return new ICompletionProposal[0];
		}
		final boolean gpu = GpuHighlightRegions.contains(source, Math.max(0, offset - 1));
		if (!gpu)
			return new ICompletionProposal[0];
		int start = offset;
		while (start > 0 && (Character.isJavaIdentifierPart(source.charAt(start - 1)) || source.charAt(start - 1) == '@')) {
			start--;
		}
		final String prefix = source.substring(start, offset);
		final int wordStart = start;
		final List<ICompletionProposal> result = new ArrayList<>();
		for (final String proposal : GpuCompletionProcessor.GLSL) {
			if (!proposal.startsWith(prefix)) {
				continue;
			}
			final int cursor = proposal.endsWith("()") ? proposal.length() - 1 : proposal.length();
			result.add(
					new CompletionProposal(proposal, wordStart, offset - wordStart, cursor, null, proposal.replace('\n', ' '), null, null));
		}
		result.sort(Comparator.comparing(ICompletionProposal::getDisplayString));
		return result.toArray(ICompletionProposal[]::new);
	}

	@Override
	public IContextInformation[] computeContextInformation(final ITextViewer viewer, final int offset) {
		return null;
	}

	@Override
	public char[] getCompletionProposalAutoActivationCharacters() {
		return null;
	}

	@Override
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	@Override
	public String getErrorMessage() {
		return null;
	}

	@Override
	public IContextInformationValidator getContextInformationValidator() {
		return null;
	}

}
