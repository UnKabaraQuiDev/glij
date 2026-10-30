package lu.kbra.glij.eclipse.editor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lu.kbra.glij.compiler.source.MethodRegionFinder;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextPresentationListener;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.jface.text.TextAttribute;

/** Paints only GLSL expressions after JDT's normal Java presentation is built. */
final class GpuPresentationOverlay implements ITextPresentationListener {
    private static final Pattern ANNOTATIONS = Pattern.compile("@(?:[A-Za-z_$][\\w$]*\\.)*([A-Za-z_$][\\w$]*)");
    private static final Pattern CLASSES = Pattern.compile("\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern METHODS = Pattern.compile("\\b(?:void|[A-Za-z_$][\\w$<>\\[\\].?]*)\\s+([A-Za-z_$][\\w$]*)\\s*\\(");
    private final GLiJColors colors;
    private final Supplier<IDocument> documentSupplier;
    private String cachedSource;
    private List<GpuHighlightRegions.Span> cachedScopes = List.of();

    GpuPresentationOverlay(final GLiJColors colors, final Supplier<IDocument> supplier) {
        this.colors = colors;
        this.documentSupplier = supplier;
    }

    @Override
    public void applyTextPresentation(final TextPresentation presentation) {
        final IDocument document = this.documentSupplier.get();
        if (document == null) return;
        final IRegion extent = presentation.getExtent();
        if (extent == null || extent.getLength() == 0) return;
        final String source = document.get();
        if (!source.equals(this.cachedSource)) {
            this.cachedSource = source;
            this.cachedScopes = GpuHighlightRegions.find(source);
        }
        final int displayStart = extent.getOffset();
        final int displayEnd = displayStart + extent.getLength();
        // JDT's semantic highlighting is absent when .glij is not an ICompilationUnit.
        // Supplement names only; keep lexical Java coloring under JDT's control.
        final String lexical = MethodRegionFinder.maskNonCode(source);
        applyJavaNames(presentation, lexical, displayStart, displayEnd, ANNOTATIONS, "annotation");
        applyJavaNames(presentation, lexical, displayStart, displayEnd, CLASSES, "class");
        applyJavaNames(presentation, lexical, displayStart, displayEnd, METHODS, "method");
        for (final GpuHighlightRegions.Span shader : this.cachedScopes) {
            final int from = Math.max(shader.start(), displayStart);
            final int to = Math.min(shader.end(), displayEnd);
            if (from >= to) continue;
            // Clear any incorrect Java treatment in this span, then apply GLSL tokens.
            presentation.replaceStyleRange(style(from, to, this.colors.text("default")));
            final Set<String> uniforms = GpuLexicalHighlighter.uniforms(source, shader.start());
            for (final GpuLexicalHighlighter.TokenSpan token : GpuLexicalHighlighter.scan(source, shader.start(), shader.end(), uniforms)) {
                final int left = Math.max(from, token.start());
                final int right = Math.min(to, token.end());
                if (left < right) {
                    presentation.replaceStyleRange(style(left, right, this.colors.text(token.category())));
                }
            }
        }
    }

    private void applyJavaNames(TextPresentation presentation, String lexical, int start, int end,
                                Pattern pattern, String category) {
        Matcher matcher = pattern.matcher(lexical);
        while (matcher.find()) {
            int a = matcher.start(1), b = matcher.end(1);
            if (a < start || b > end) continue;
            boolean inGpu = false;
            for (GpuHighlightRegions.Span span : cachedScopes) {
                if (a >= span.start() && a < span.end()) { inGpu = true; break; }
            }
            if (!inGpu) presentation.replaceStyleRange(style(a, b, colors.javaSemantic(category)));
        }
    }

    private static StyleRange style(final int start, final int end, final TextAttribute attribute) {
        return new StyleRange(start, end - start, attribute.getForeground(), attribute.getBackground(), attribute.getStyle());
    }
}
