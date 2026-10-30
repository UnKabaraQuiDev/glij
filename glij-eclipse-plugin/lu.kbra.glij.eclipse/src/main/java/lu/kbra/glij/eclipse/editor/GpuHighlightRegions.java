package lu.kbra.glij.eclipse.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lu.kbra.glij.compiler.source.MethodRegion;
import lu.kbra.glij.compiler.source.MethodRegionFinder;

/**
 * Source-offset-preserving GLSL ranges. Java annotations, signatures, imports and
 * code between GPU functions intentionally remain under JDT's Java highlighting.
 */
public final class GpuHighlightRegions {
    /** Half-open offsets in the original (not generated) document. */
    public record Span(int start, int end) {
        public boolean contains(int offset) {
            return start <= offset && offset < end;
        }
    }

    private static final Pattern GLSL_FUNCTION = Pattern.compile(
            "\\b(?:void|bool|int|uint|float|double|[biud]?vec[234]|d?mat[234](?:x[234])?|[A-Za-z_$][\\w$]*)\\s+([A-Za-z_$][\\w$]*)\\s*\\(");
    private static final Pattern RENDER_STAGE = Pattern.compile("\\b(?:vertex|fragment|geometry|tessControl|tessEvaluation)\\s*\\(");
    private static final Pattern SHADER_ANNOTATION = Pattern.compile(
            "@(?:[\\w$]+\\.)*(?:GlCompute|GlRender|ShaderFunction)\\b");

    private GpuHighlightRegions() {}

    public static List<Span> find(final String source) {
        final String lexical;
        try {
            lexical = MethodRegionFinder.maskNonCode(source);
        } catch (final IllegalArgumentException incomplete) {
            return List.of();
        }
        final List<Span> spans = new ArrayList<>();
        try {
            for (final MethodRegion method : MethodRegionFinder.find(source)) {
                if ("ShaderFunction".equals(method.kind())) {
                    // The Java method signature stays Java; only the shader body is GLSL.
                    spans.add(new Span(method.openBrace() + 1, method.closeBrace()));
                } else {
                    collectFunctions(lexical, method.openBrace() + 1, method.closeBrace(), spans);
                }
            }
        } catch (final IllegalArgumentException incomplete) {
            // A partially typed method must not disable highlighting in preceding methods.
            collectCompletedMethods(source, lexical, spans);
        }
        return List.copyOf(spans);
    }

    private static void collectCompletedMethods(final String source, final String lexical, final List<Span> spans) {
        final Matcher annotations = SHADER_ANNOTATION.matcher(lexical);
        while (annotations.find()) {
            final int open = findMethodBrace(lexical, annotations.end());
            if (open < 0) continue;
            final int close = MethodRegionFinder.matching(lexical, open, '{', '}');
            if (close < 0) {
                if (source.substring(annotations.start(), annotations.end()).endsWith("ShaderFunction")) {
                    spans.add(new Span(open + 1, source.length()));
                } else {
                    collectFunctions(lexical, open + 1, source.length(), spans);
                }
                break;
            }
            if (source.substring(annotations.start(), annotations.end()).endsWith("ShaderFunction")) {
                spans.add(new Span(open + 1, close));
            } else {
                collectFunctions(lexical, open + 1, close, spans);
            }
        }
    }

    private static int findMethodBrace(final String text, final int from) {
        int parentheses = 0;
        for (int i = from; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '(') parentheses++;
            else if (c == ')') parentheses--;
            else if (c == '{' && parentheses == 0) return i;
            else if (c == ';' && parentheses == 0) return -1;
        }
        return -1;
    }

    private static void collectFunctions(final String lexical, final int start, final int end, final List<Span> result) {
        // Render stages have the DSL syntax vertex(...): { ... } / fragment(...): { ... }.
        final Matcher stages = RENDER_STAGE.matcher(lexical);
        stages.region(start, end);
        while (stages.find()) {
            final int opening = lexical.indexOf('(', stages.start());
            final int paramsEnd = MethodRegionFinder.matching(lexical, opening, '(', ')');
            if (paramsEnd < 0 || paramsEnd >= end) continue;
            int brace = paramsEnd + 1;
            while (brace < end && Character.isWhitespace(lexical.charAt(brace))) brace++;
            if (brace < end && lexical.charAt(brace) == ':') brace++;
            while (brace < end && Character.isWhitespace(lexical.charAt(brace))) brace++;
            if (brace >= end || lexical.charAt(brace) != '{') continue;
            final int stageClose = MethodRegionFinder.matching(lexical, brace, '{', '}');
            if (stageClose < 0 || stageClose >= end) {
                result.add(new Span(stages.start(), end));
                return;
            }
            result.add(new Span(stages.start(), stageClose + 1));
            stages.region(stageClose + 1, end);
        }
        final Matcher methods = GLSL_FUNCTION.matcher(lexical);
        methods.region(start, end);
        while (methods.find()) {
            final int openParenthesis = lexical.indexOf('(', methods.start());
            final int closeParenthesis = MethodRegionFinder.matching(lexical, openParenthesis, '(', ')');
            if (closeParenthesis < 0 || closeParenthesis >= end) {
                result.add(new Span(methods.start(), end));
                break;
            }
            int brace = closeParenthesis + 1;
            while (brace < end && Character.isWhitespace(lexical.charAt(brace))) brace++;
            if (brace >= end || lexical.charAt(brace) != '{') continue;
            final int close = MethodRegionFinder.matching(lexical, brace, '{', '}');
            if (close < 0 || close >= end) {
                result.add(new Span(methods.start(), end));
                break;
            }
            // Nested void main() belongs to its enclosing render-stage range.
            if (result.stream().noneMatch(stage -> stage.start() <= methods.start() && stage.end() >= close + 1)) {
                result.add(new Span(methods.start(), close + 1));
            }
            methods.region(close + 1, end);
        }
    }

    public static boolean contains(final String source, final int offset) {
        return find(source).stream().anyMatch(span -> span.contains(offset));
    }
}
