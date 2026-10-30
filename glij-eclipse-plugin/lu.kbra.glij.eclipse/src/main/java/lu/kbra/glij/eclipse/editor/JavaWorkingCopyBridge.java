package lu.kbra.glij.eclipse.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.manipulation.OrganizeImportsOperation;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.AnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.swt.widgets.Display;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IFileEditorInput;

import lu.kbra.glij.compiler.JavaSourceProjection;
import lu.kbra.glij.compiler.GLiJCompiler;
import lu.kbra.glij.compiler.SourceDirectory;
import lu.kbra.glij.eclipse.EclipseSourceLayouts;
import lu.kbra.glij.eclipse.GLiJPlugin;

/**
 * A per-editor, private JDT working copy of the generated Java counterpart.
 * The source of truth stays the (possibly unsaved) .glij IDocument.
 * Never commits the working copy and never writes edits into generated files.
 */
final class JavaWorkingCopyBridge implements AutoCloseable {
    private static final String ERROR_ANNOTATION = "org.eclipse.ui.workbench.texteditor.error";
    private static final String WARNING_ANNOTATION = "org.eclipse.ui.workbench.texteditor.warning";
    private static final int MAX_COMPLETIONS = 120;
    private static final long RECONCILE_DELAY_MS = 350L;

    private final GLiJEditor editor;
    private final IDocument document;
    private final IFile sourceFile;
    private final Object lock = new Object();
    private final ProblemCollector problems = new ProblemCollector();
    private final WorkingCopyOwner owner = new WorkingCopyOwner() {
        @Override
        public IProblemRequestor getProblemRequestor(ICompilationUnit workingCopy) { return problems; }
    };
    private final IDocumentListener listener = new IDocumentListener() {
        @Override public void documentAboutToBeChanged(DocumentEvent event) { }
        @Override public void documentChanged(DocumentEvent event) { schedule(); }
    };
    private volatile Job pendingJob;

    private volatile boolean disposed;
    private volatile String latestSource;
    private volatile JavaSourceProjection projection;
    private volatile List<IProblem> latestProblems = List.of();
    private volatile ICompilationUnit copy;
    private final Object annotationKey = new Object();
    private final AnnotationModel annotations = new AnnotationModel();
    private final IAnnotationModel baseAnnotations;
    private final IAnnotationModel liveAnnotations;
    private Annotation[] shown = new Annotation[0];

    private JavaWorkingCopyBridge(GLiJEditor editor, IDocument document, IFile sourceFile) {
        this.editor = editor;
        this.document = document;
        this.sourceFile = sourceFile;
        this.baseAnnotations = editor.getDocumentProvider().getAnnotationModel(editor.getEditorInput());
        if (baseAnnotations instanceof IAnnotationModelExtension root) {
            root.addAnnotationModel(annotationKey, annotations);
            this.liveAnnotations = annotations;
        } else {
            this.liveAnnotations = baseAnnotations;
        }
        this.document.addDocumentListener(listener);
        schedule();
    }

    static JavaWorkingCopyBridge attach(GLiJEditor editor) {
        if (!(editor.getEditorInput() instanceof IFileEditorInput fileInput)) return null;
        if (editor.activeDocument() == null) return null;
        return new JavaWorkingCopyBridge(editor, editor.activeDocument(), fileInput.getFile());
    }

    private void schedule() {
        if (disposed) return;
        final Job previous = pendingJob;
        if (previous != null) previous.cancel();
        final Job next = new Job("Reconcile GLiJ working copy") {
            @Override protected IStatus run(IProgressMonitor monitor) {
                if (disposed || monitor.isCanceled()) return Status.CANCEL_STATUS;
                try {
                    synchronized (lock) {
                        if (disposed || monitor.isCanceled()) return Status.CANCEL_STATUS;
                        updateWorkingCopy(monitor);
                    }
                } catch (JavaModelException | IOException | IllegalArgumentException exception) {
                    // Incomplete shader declarations are normal while typing.
                    return Status.OK_STATUS;
                }
                return Status.OK_STATUS;
            }
        };
        next.setSystem(true);
        pendingJob = next;
        next.schedule(RECONCILE_DELAY_MS);
    }

    /** Called under lock; never commits the working copy or mutates the generated source file. */
    private void updateWorkingCopy(IProgressMonitor monitor) throws IOException, JavaModelException {
        if (disposed) return;
        final String original = document.get();
        if (original.equals(latestSource) && projection != null && copy != null) return;
        final Path path = sourceFile.getLocation().toFile().toPath();
        final var mapping = EclipseSourceLayouts.read(sourceFile.getProject()).forSource(path);
        if (mapping.isEmpty()) return;
        final SourceDirectory layout = mapping.get();
        final Path javaPath = layout.generatedJavaFor(path, original);
        final IFile file = EclipseSourceLayouts.workspaceFile(sourceFile.getProject(), javaPath);
        if (file == null || !file.exists()) return; // Builder has not generated this compilation unit yet.
        final ICompilationUnit primary = JavaCore.createCompilationUnitFrom(file);
        if (primary == null) return; // Generated folder must be in the JDT source path.
        final String javaName = javaPath.getFileName().toString();
        final JavaSourceProjection next = GLiJCompiler.projectJava(javaName, original);
        if (copy == null || !copy.getPath().equals(primary.getPath())) {
            if (copy != null) copy.discardWorkingCopy();
            copy = primary.getWorkingCopy(owner, monitor);
        }
        problems.clear();
        copy.getBuffer().setContents(next.javaSource());
        copy.reconcile(ICompilationUnit.NO_AST, true, owner, monitor);
        latestSource = original;
        projection = next;
        latestProblems = problems.snapshot();
        showProblems(original, next, latestProblems);
    }

    private void showProblems(String original, JavaSourceProjection map, List<IProblem> found) {
        // JDT reconciliation happens off the UI thread; annotation updates must not.
        final List<PositionedProblem> visible = new ArrayList<>();
        for (IProblem problem : found) {
            if (!problem.isError() && !problem.isWarning()) continue;
            var range = map.toOriginalRange(problem.getSourceStart(), problem.getSourceEnd() + 1);
            if (range.isEmpty()) continue; // Suppress diagnostics in synthetic delegate code.
            var value = range.get();
            if (value.start() < 0 || value.end() > original.length()) continue;
            visible.add(new PositionedProblem(problem.getMessage(), problem.isError(),
                    value.start(), Math.max(1, value.end() - value.start())));
        }
        Display display = Display.getDefault();
        if (display.isDisposed()) return;
        display.asyncExec(() -> {
            if (disposed || !document.get().equals(original)) return;
            IAnnotationModel model = liveAnnotations;
            if (model == null) return;
            final java.util.Map<Annotation, Position> additions = new java.util.LinkedHashMap<>();
            for (PositionedProblem issue : visible) {
                Annotation annotation = new Annotation(issue.error ? ERROR_ANNOTATION : WARNING_ANNOTATION,
                        false, issue.message);
                additions.put(annotation, new Position(issue.start, issue.length));
            }
            if (model instanceof IAnnotationModelExtension extension) {
                extension.replaceAnnotations(shown, additions);
            } else {
                for (Annotation marker : shown) model.removeAnnotation(marker);
                additions.forEach(model::addAnnotation);
            }
            shown = additions.keySet().toArray(Annotation[]::new);
        });
    }

    /** JDT completion on a current unsaved Java working copy; only safe original ranges are editable. */
    ICompletionProposal[] complete(int originalOffset) {
        synchronized (lock) {
            try {
                updateWorkingCopy(null); // Explicit Ctrl+Space must not use a stale document.
                if (copy == null || projection == null || !document.get().equals(latestSource)) return new ICompletionProposal[0];
                var offset = projection.toJavaOffset(originalOffset);
                if (offset.isEmpty()) return new ICompletionProposal[0];
                var result = new ArrayList<ICompletionProposal>();
                Set<String> unique = new HashSet<>();
                copy.codeComplete(offset.getAsInt(), new CompletionRequestor() {
                    @Override public void accept(org.eclipse.jdt.core.CompletionProposal candidate) {
                        if (result.size() >= MAX_COMPLETIONS) return;
                        String completion = new String(candidate.getCompletion());
                        if (completion.isBlank() || completion.indexOf('\n') >= 0 || completion.indexOf('\r') >= 0)
                            return;
                        var range = projection.toOriginalRange(candidate.getReplaceStart(), candidate.getReplaceEnd());
                        if (range.isEmpty()) return;
                        var edit = range.get();
                        if (!unique.add(completion + ":" + edit.start())) return;
                        result.add(new CompletionProposal(completion, edit.start(), edit.end() - edit.start(),
                                completion.length(), null, completion, null, null));
                    }
                });
                return result.toArray(ICompletionProposal[]::new);
            } catch (IOException | JavaModelException | IllegalArgumentException error) {
                return new ICompletionProposal[0];
            }
        }
    }

    /**
     * JDT computes the import edits against the private working copy. Apply them to a temporary
     * generated Document, translate the changed span, then edit ONLY the original .glij buffer.
     */
    void organizeImports() {
        try {
            synchronized (lock) {
                updateWorkingCopy(null);
                if (copy == null || projection == null || !document.get().equals(latestSource)) return;
                OrganizeImportsOperation operation = new OrganizeImportsOperation(copy, null,
                        false, false, true, null);
                TextEdit edits = operation.createTextEdit(null);
                if (edits == null) return;
                Document afterDocument = new Document(projection.javaSource());
                edits.apply(afterDocument);
                var sourceEdit = projection.translateJavaChange(afterDocument.get());
                if (sourceEdit.isEmpty()) return; // No edit, or edit crosses generated shader code.
                var edit = sourceEdit.get();
                // IDocument edits are undoable and retain the actual .glij buffer as source of truth.
                document.replace(edit.start(), edit.end() - edit.start(), edit.replacement());
            }
        } catch (org.eclipse.core.runtime.CoreException | BadLocationException | IOException | IllegalArgumentException failure) {
            GLiJPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, GLiJPlugin.ID,
                    "Cannot organize imports in GLiJ source", failure));
        }
    }

    @Override public void close() {
        disposed = true;
        document.removeDocumentListener(listener);
        Job pending = pendingJob;
        if (pending != null) pending.cancel();
        synchronized (lock) {
            if (copy != null) {
                try { copy.discardWorkingCopy(); } catch (JavaModelException ignored) { }
                copy = null;
            }
        }
        if (liveAnnotations != null) for (Annotation marker : shown) liveAnnotations.removeAnnotation(marker);
        shown = new Annotation[0];
        if (baseAnnotations instanceof IAnnotationModelExtension root)
            root.removeAnnotationModel(annotationKey);
    }

    private record PositionedProblem(String message, boolean error, int start, int length) { }

    private static final class ProblemCollector implements IProblemRequestor {
        private final List<IProblem> collected = new ArrayList<>();
        @Override public synchronized void beginReporting() { collected.clear(); }
        @Override public synchronized void acceptProblem(IProblem problem) { collected.add(problem); }
        @Override public void endReporting() { }
        @Override public boolean isActive() { return true; }
        synchronized void clear() { collected.clear(); }
        synchronized List<IProblem> snapshot() { return List.copyOf(collected); }
    }
}
