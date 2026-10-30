package lu.kbra.glij.eclipse.editor;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import lu.kbra.glij.eclipse.EclipseSourceLayouts;
import lu.kbra.glij.compiler.SourceDirectory;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ui.IFileEditorInput;

/** Reuses Eclipse JDT completion on the transformed Java source, outside GLSL. */
final class JavaCompletionBridge {
	
	private JavaCompletionBridge() {
	}

	static List<ICompletionProposal> proposals(final GLiJEditor editor, final IDocument original, final int offset, final int wordStart) {
		if (!(editor.getEditorInput() instanceof final IFileEditorInput fileInput)) {
			return List.of();
		}
		final IFile input = fileInput.getFile();
        final IFile javaFile;
        try {
            var location = EclipseSourceLayouts.read(input.getProject());
            var mapping = location.forSource(input.getLocation().toFile().toPath());
            if (mapping.isEmpty()) return List.of();
            javaFile = EclipseSourceLayouts.workspaceFile(input.getProject(),
                    mapping.get().generatedJavaFor(input.getLocation().toFile().toPath(), original.get()));
            if (javaFile == null) return List.of();
        } catch (IOException | IllegalArgumentException failure) {
            return List.of();
        }
		if (!javaFile.exists()) {
			return List.of();
		}
		final ICompilationUnit unit = JavaCore.createCompilationUnitFrom(javaFile);
		if (unit == null) {
			return List.of();
		}
		final List<ICompletionProposal> suggestions = new ArrayList<>();
		try {
			final Document generated = new Document(unit.getSource());
			final int line = original.getLineOfOffset(offset);
			if (line >= generated.getNumberOfLines()) {
				return List.of();
			}
			final int column = offset - original.getLineOffset(line);
			final int generatedPosition = generated.getLineOffset(line) + Math.min(column, Math.max(0, generated.getLineLength(line) - 1));
			unit.codeComplete(generatedPosition, new CompletionRequestor() {
				@Override
				public void accept(final org.eclipse.jdt.core.CompletionProposal candidate) {
					final String value = new String(candidate.getCompletion());
					if (value.isBlank() || value.contains("\n") || value.contains("\r")) {
						return;
					}
					suggestions.add(new org.eclipse.jface.text.contentassist.CompletionProposal(value,
							wordStart,
							offset - wordStart,
							value.length(),
							null,
							value,
							null,
							null));
				}
			});
		} catch (org.eclipse.jdt.core.JavaModelException | BadLocationException failure) {
			// No working copy yet, or the user has edited unsaved Java lines.
			return List.of();
		}
		return suggestions;
	}
}
