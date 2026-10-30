import java.util.List;
import java.util.Set;

import lu.kbra.glij.eclipse.editor.GpuHighlightRegions;
import lu.kbra.glij.eclipse.editor.GpuLexicalHighlighter;

/** Dependency-free regression checks. Run via javac/java as documented in README. */
public final class GpuHighlightingSmokeTest {
    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String category(final String source, final List<GpuLexicalHighlighter.TokenSpan> tokens, final String word) {
        return tokens.stream().filter(t -> source.substring(t.start(), t.end()).equals(word))
                .map(GpuLexicalHighlighter.TokenSpan::category).findFirst().orElse("not-colored");
    }

    public static void main(final String[] args) {
        final String source = """
                package demo;
                @GlCompute(version=460)
                @LocalSize(x=64)
                public Semaphore compute(@Uniform int count, @Layout("std430") Object output) {
                    int javaBefore = 0;
                    void main() {
                        uint id = gl_GlobalInvocationID.x;
                        if (id >= uint(count)) return;
                        output[id] = square(id);
                        // shader comment
                    }
                    int javaAfter = 1;
                }
                class Layout { void mainJava() {} }
                """;
        final List<GpuHighlightRegions.Span> spans = GpuHighlightRegions.find(source);
        check(spans.size() == 1, "expected exactly one shader function");
        final var shader = spans.getFirst();
        for (String java : List.of("@GlCompute", "@LocalSize", "@Uniform", "@Layout", "javaBefore", "javaAfter", "class Layout", "mainJava")) {
            check(!shader.contains(source.indexOf(java)), "Java was incorrectly colorized as GLSL: " + java);
        }
        check(shader.contains(source.indexOf("void main()")), "GLSL function header not colorized");
        check(shader.contains(source.indexOf("gl_GlobalInvocationID")), "GLSL function body not colorized");
        final var tokens = GpuLexicalHighlighter.scan(source, shader.start(), shader.end(),
                GpuLexicalHighlighter.uniforms(source, shader.start()));
        check("keyword".equals(category(source, tokens, "void main".substring(0, 4))), "void keyword missing");
        check("systemConstant".equals(category(source, tokens, "gl_GlobalInvocationID")), "system constant color missing");
        check("type".equals(category(source, tokens, "uint id".substring(0, 4))), "GLSL type missing");
        check("uniform".equals(category(source, tokens, "count))".substring(0, 5))), "uniform parameter missing");
        check("function".equals(category(source, tokens, "main()".substring(0, 4))), "main must be function, not 'in' keyword");
        check("function".equals(category(source, tokens, "square(id)".substring(0, 6))), "function name missing");
        check("variable".equals(category(source, tokens, "output[id]".substring(0, 6))), "variable name missing");
        check(tokens.stream().noneMatch(t -> t.start() == source.indexOf("main()") + 2), "in inside main must not match");
        check(tokens.stream().noneMatch(t -> t.start() == source.indexOf("output[id]") + 0 + 3), "out inside output must not match");
        final String standalone = "void main() { int mainValue; in out inout; Layout layout; }";
        final var words = GpuLexicalHighlighter.scan(standalone, 0, standalone.length(), Set.of());
        check(words.stream().filter(t -> t.category().equals("keyword") && standalone.substring(t.start(), t.end()).equals("in")).count() == 1, "in whole-word matching");
        check(words.stream().filter(t -> t.category().equals("keyword") && standalone.substring(t.start(), t.end()).equals("out")).count() == 1, "out whole-word matching");
        check("function".equals(category(standalone, words, "main")), "main is one function token");
        check("variable".equals(category(standalone, words, "mainValue")), "mainValue is one variable token");
        check("variable".equals(category(standalone, words, "Layout")), "Layout is one variable token");
        final String shaderFunction = "@ShaderFunction public float square(float x) { return x*x; }";
        final var functionScopes = GpuHighlightRegions.find(shaderFunction);
        check(functionScopes.size() == 1 && functionScopes.getFirst().contains(shaderFunction.indexOf("return")), "ShaderFunction body");
        check(!functionScopes.getFirst().contains(shaderFunction.indexOf("@ShaderFunction")), "ShaderFunction Java annotation stays Java");
        final String render = "@GlRender public void render() { vertex(in vec3 pos): { void main() { gl_Position = vec4(pos,1.0); } } fragment(out vec4 color): { void main() { color=vec4(1.0); } } int javaAfter = 0; }";
        final var renderSpans = GpuHighlightRegions.find(render);
        check(renderSpans.size() == 2, "render stage shader scopes");
        check(renderSpans.stream().anyMatch(r -> r.contains(render.indexOf("vertex("))), "vertex header shader scope");
        check(renderSpans.stream().anyMatch(r -> r.contains(render.indexOf("fragment("))), "fragment header shader scope");
        check(renderSpans.stream().noneMatch(r -> r.contains(render.indexOf("javaAfter"))), "Java following render stages");
        final String partial = "@GlCompute public void run() { void main() { uint id = 3;";
        check(!GpuHighlightRegions.find(partial).isEmpty(), "Live incomplete shader function must still be colored");
        System.out.println("PASS: JDT/GLSL scopes, annotations, words, uniforms, variables, functions, builtins, partial edits");
    }
}
