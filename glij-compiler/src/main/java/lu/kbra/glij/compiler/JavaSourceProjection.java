package lu.kbra.glij.compiler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

/**
 * An in-memory Java representation of a GLiJ buffer and a bidirectional mapping
 * for unchanged Java spans. Generated shader delegates are deliberately not editable:
 * offsets within them have no inverse mapping to GLiJ shader statements.
 *
 * <p>All offsets are UTF-16 character offsets, matching JDT and Eclipse IDocument.
 */
public final class JavaSourceProjection {
    /** A half-open replacement range in the original document. */
    public record Replacement(int start, int end, String javaText) {
        public Replacement {
            if (start < 0 || end < start || javaText == null)
                throw new IllegalArgumentException("Invalid source replacement");
        }
    }

    /** One half-open, unchanged span with offsets in both documents. */
    public record Segment(int originalStart, int originalEnd, int javaStart, int javaEnd) { }

    public record Range(int start, int end) { }
    public record Edit(int start, int end, String replacement) { }

    private final String javaSource;
    private final List<Segment> unchanged;
    private final int originalLength;

    private JavaSourceProjection(String javaSource, List<Segment> unchanged, int originalLength) {
        this.javaSource = javaSource;
        this.unchanged = List.copyOf(unchanged);
        this.originalLength = originalLength;
    }

    public String javaSource() { return javaSource; }
    public List<Segment> unchanged() { return unchanged; }

    public static JavaSourceProjection identity(String source) {
        return create(source, List.of());
    }

    public static JavaSourceProjection create(String original, List<Replacement> replacements) {
        var sorted = new ArrayList<>(replacements);
        sorted.sort(Comparator.comparingInt(Replacement::start));
        var result = new StringBuilder();
        var mapped = new ArrayList<Segment>();
        int cursor = 0;
        for (var replacement : sorted) {
            if (replacement.start() < cursor || replacement.end() > original.length())
                throw new IllegalArgumentException("Overlapping or out-of-range shader replacement");
            appendUnchanged(original, result, mapped, cursor, replacement.start());
            result.append(replacement.javaText());
            cursor = replacement.end();
        }
        appendUnchanged(original, result, mapped, cursor, original.length());
        return new JavaSourceProjection(result.toString(), mapped, original.length());
    }

    private static void appendUnchanged(String original, StringBuilder result, List<Segment> mapped, int from, int to) {
        int javaStart = result.length();
        result.append(original, from, to);
        // Zero-length spans are meaningful only for empty files.
        if (to > from) mapped.add(new Segment(from, to, javaStart, result.length()));
    }

    public OptionalInt toJavaOffset(int originalOffset) {
        if (originalOffset < 0 || originalOffset > originalLength) return OptionalInt.empty();
        for (Segment span : unchanged) {
            if (originalOffset >= span.originalStart() && originalOffset < span.originalEnd())
                return OptionalInt.of(span.javaStart() + originalOffset - span.originalStart());
        }
        if (originalOffset == originalLength) return OptionalInt.of(javaSource.length());
        return OptionalInt.empty();
    }

    public OptionalInt toOriginalOffset(int javaOffset) {
        if (javaOffset < 0 || javaOffset > javaSource.length()) return OptionalInt.empty();
        for (Segment span : unchanged) {
            if (javaOffset >= span.javaStart() && javaOffset < span.javaEnd())
                return OptionalInt.of(span.originalStart() + javaOffset - span.javaStart());
        }
        if (javaOffset == javaSource.length()) return OptionalInt.of(originalLength);
        return OptionalInt.empty();
    }

    /** Map an edit or diagnostic only if it falls wholly in one unchanged Java span. */
    public java.util.Optional<Range> toOriginalRange(int javaStart, int javaEnd) {
        if (javaStart < 0 || javaEnd < javaStart || javaEnd > javaSource.length()) return java.util.Optional.empty();
        for (Segment span : unchanged) {
            if (javaStart >= span.javaStart() && javaEnd <= span.javaEnd()) {
                int start = span.originalStart() + javaStart - span.javaStart();
                return java.util.Optional.of(new Range(start, start + javaEnd - javaStart));
            }
        }
        if (javaStart == javaEnd && javaStart == javaSource.length())
            return java.util.Optional.of(new Range(originalLength, originalLength));
        return java.util.Optional.empty();
    }
    /**
     * Find a minimal contiguous edit between the original projection and an edited Java buffer.
     * Accept it only when the entire changed range belongs to untouched Java source. Used for
     * JDT's organize-import edits; refuses to rewrite synthetic shader delegates.
     */
    public java.util.Optional<Edit> translateJavaChange(String editedJava) {
        if (javaSource.equals(editedJava)) return java.util.Optional.empty();
        int prefix = 0;
        while (prefix < javaSource.length() && prefix < editedJava.length()
                && javaSource.charAt(prefix) == editedJava.charAt(prefix)) prefix++;
        int suffix = 0;
        while (suffix < javaSource.length() - prefix && suffix < editedJava.length() - prefix
                && javaSource.charAt(javaSource.length() - suffix - 1)
                == editedJava.charAt(editedJava.length() - suffix - 1)) suffix++;
        int javaEnd = javaSource.length() - suffix;
        final int insertStart = prefix;
        final int insertEnd = editedJava.length() - suffix;
        return toOriginalRange(prefix, javaEnd)
                .map(range -> new Edit(range.start(), range.end(),
                        editedJava.substring(insertStart, insertEnd)));
    }

}
