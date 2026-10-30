package lu.kbra.glij.eclipse.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dependency-free, full-identifier GLSL tokenizer for painting GLSL spans. */
public final class GpuLexicalHighlighter {
    public record TokenSpan(int start, int end, String category) {}

    private static final Set<String> KEYWORDS = Set.of("void", "return", "if", "else", "for", "while", "do",
            "switch", "case", "break", "continue", "in", "out", "inout", "uniform", "layout",
            "readonly", "writeonly", "buffer", "shared", "const", "flat", "smooth", "noperspective",
            "highp", "mediump", "lowp", "precision", "discard", "struct", "true", "false");
    private static final Set<String> TYPES = Set.of("int", "uint", "float", "double", "bool", "vec2", "vec3", "vec4",
            "ivec2", "ivec3", "ivec4", "uvec2", "uvec3", "uvec4", "bvec2", "bvec3", "bvec4",
            "dvec2", "dvec3", "dvec4", "mat2", "mat3", "mat4", "mat2x3", "mat2x4", "mat3x2",
            "mat3x4", "mat4x2", "mat4x3", "sampler2D", "sampler3D", "samplerCube", "image2D",
            "image3D", "atomic_uint");
    private static final Set<String> BUILTINS = Set.of("gl_GlobalInvocationID", "gl_LocalInvocationID",
            "gl_WorkGroupID", "gl_NumWorkGroups", "gl_WorkGroupSize", "gl_Position", "gl_FragCoord",
            "gl_VertexID", "gl_InstanceID", "gl_LocalInvocationIndex", "gl_FrontFacing", "gl_FragDepth",
            "sin", "cos", "tan", "asin", "acos", "atan", "pow", "exp", "log", "sqrt", "inversesqrt",
            "abs", "floor", "ceil", "fract", "length", "distance", "normalize", "dot", "cross",
            "texture", "textureLod", "mix", "clamp", "min", "max", "step", "smoothstep", "mod",
            "determinant", "inverse", "transpose", "barrier", "memoryBarrier", "memoryBarrierShared",
            "atomicAdd", "atomicMin", "atomicMax", "atomicExchange");
    private static final Set<String> SYSTEM_CONSTANTS = Set.of("gl_GlobalInvocationID", "gl_LocalInvocationID",
            "gl_WorkGroupID", "gl_NumWorkGroups", "gl_WorkGroupSize", "gl_Position", "gl_FragCoord",
            "gl_VertexID", "gl_InstanceID", "gl_LocalInvocationIndex", "gl_FrontFacing", "gl_FragDepth");
    // GLSL variables supplied by Java parameters annotated @Uniform or @GroupSize.
    private static final Pattern UNIFORM = Pattern.compile(
            "@(?:[\\w$]+\\.)*(?:Uniform|GroupSize)\\b(?:\\s*\\([^)]*\\))?\\s+" +
            "(?:(?:final|volatile)\\s+)?[\\w$.<>?, \\t]+?\\s+([A-Za-z_$][\\w$]*)\\s*(?=[,)])");

    private GpuLexicalHighlighter() {}

    /** Recognize only full tokens. The 'in' of 'main' and 'out' of 'Layout' never match. */
    public static List<TokenSpan> scan(final String source, final int start, final int end, final Set<String> uniforms) {
        if (start < 0 || end < start || end > source.length()) throw new IllegalArgumentException("Invalid scan range");
        final List<TokenSpan> result = new ArrayList<>();
        int p = start;
        while (p < end) {
            final int from = p;
            final char c = source.charAt(p);
            if (Character.isWhitespace(c)) { p++; continue; }
            if (c == '/' && p + 1 < end && source.charAt(p + 1) == '/') {
                p += 2;
                while (p < end && source.charAt(p) != '\n' && source.charAt(p) != '\r') p++;
                result.add(new TokenSpan(from, p, "comment"));
                continue;
            }
            if (c == '/' && p + 1 < end && source.charAt(p + 1) == '*') {
                p += 2;
                while (p + 1 < end && !(source.charAt(p) == '*' && source.charAt(p + 1) == '/')) p++;
                p = Math.min(end, p + 2);
                result.add(new TokenSpan(from, p, "comment"));
                continue;
            }
            if (c == '"' || c == '\'') {
                p++;
                while (p < end) {
                    if (source.charAt(p) == '\\' && p + 1 < end) { p += 2; continue; }
                    if (source.charAt(p++) == c) break;
                }
                result.add(new TokenSpan(from, p, "string"));
                continue;
            }
            if (c == '#' && lineStart(source, start, p)) {
                while (p < end && source.charAt(p) != '\n' && source.charAt(p) != '\r') p++;
                result.add(new TokenSpan(from, p, "preprocessor"));
                continue;
            }
            if (Character.isDigit(c) || (c == '.' && p + 1 < end && Character.isDigit(source.charAt(p + 1)))) {
                p++;
                while (p < end && (Character.isDigit(source.charAt(p)) || source.charAt(p) == '.' ||
                        source.charAt(p) == '_' || source.charAt(p) == 'x' || source.charAt(p) == 'X' ||
                        (source.charAt(p) >= 'a' && source.charAt(p) <= 'f') ||
                        (source.charAt(p) >= 'A' && source.charAt(p) <= 'F') ||
                        source.charAt(p) == 'u' || source.charAt(p) == 'U')) p++;
                result.add(new TokenSpan(from, p, "number"));
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                p++;
                while (p < end && Character.isJavaIdentifierPart(source.charAt(p))) p++;
                final String word = source.substring(from, p);
                int after = p;
                while (after < end && Character.isWhitespace(source.charAt(after))) after++;
                final String category;
                if (TYPES.contains(word)) category = "type";
                else if (KEYWORDS.contains(word)) category = "keyword";
                else if (uniforms.contains(word)) category = "uniform";
                else if (SYSTEM_CONSTANTS.contains(word)) category = "systemConstant";
                else if (BUILTINS.contains(word)) category = "builtin";
                else if (after < end && source.charAt(after) == '(') category = "function";
                else category = "variable";
                result.add(new TokenSpan(from, p, category));
                continue;
            }
            p++;
        }
        return List.copyOf(result);
    }

    public static Set<String> uniforms(final String source, final int declarationEnd) {
        final Matcher matcher = UNIFORM.matcher(source.substring(0, Math.min(declarationEnd, source.length())));
        final java.util.HashSet<String> names = new java.util.HashSet<>();
        while (matcher.find()) names.add(matcher.group(1));
        return Set.copyOf(names);
    }

    private static boolean lineStart(final String source, final int start, final int at) {
        for (int i = at - 1; i >= start; i--) {
            if (source.charAt(i) == '\n' || source.charAt(i) == '\r') return true;
            if (!Character.isWhitespace(source.charAt(i))) return false;
        }
        return true;
    }
}
