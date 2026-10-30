package lu.kbra.glij.tests;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import lu.kbra.glij.compiler.ast.JavaAstAnalyzer;
import lu.kbra.glij.compiler.ast.MethodInfo;
import lu.kbra.glij.compiler.source.MethodRegionFinder;

public class SourceScannerTest {
	@Test
	void ignoresStringsCommentsAndHandlesMultipleShaders() {
		final String source = """
				class Sample {
				  String text = "@GlCompute { }";
				  // @GlRender public void bogus() { }
				  @GlCompute(version = 460)
				  @LocalSize(x = 32)
				  public void compute(@Uniform int n) {
				      void main() { if (n > 0) { /* { } */ return; } }
				  }
				  @GlRender
				  public void render() {
				      vertex(in vec3 position): { void main() { gl_Position = vec4(position, 1.0); } }
				      fragment(out vec4 color): { void main() { color = vec4(1.0); } }
				  }
				}
				""";
		final var regions = MethodRegionFinder.find(source);
		Assertions.assertEquals(2, regions.size());
		Assertions.assertTrue(regions.getFirst().shaderBody().contains("void main"));
		final String masked = MethodRegionFinder.maskBodies(source, regions);
		Assertions.assertEquals(source.length(), masked.length());
		final var methods = JavaAstAnalyzer.analyze("Sample.java", masked, regions);
		Assertions.assertEquals(List.of("compute", "render"), methods.stream().map(MethodInfo::name).toList());
		Assertions.assertEquals("32", methods.getFirst().annotationValue("LocalSize", "x", "?"));
		Assertions.assertEquals("int", methods.getFirst().parameters().getFirst().javaType());
	}

	@Test
	void rejectsUnclosedBody() {
		Assertions.assertThrows(IllegalArgumentException.class,
				() -> MethodRegionFinder.find("class Test { @GlCompute void f() { void main() { }"));
	}

	@Test
	void supportsJava21FeaturesOutsideShaderBody() {
		final String src = """
				package example;
				public class C {
				  record Input(int x) { }
				  public int ordinary(int n) { return switch (n) { case 0 -> 2; default -> 3; }; }
				  @GlCompute public void gpu() { void main() { uint id = gl_GlobalInvocationID.x; } }
				}
				""";
		final var regions = MethodRegionFinder.find(src);
		final var methods = JavaAstAnalyzer.analyze("C.java", MethodRegionFinder.maskBodies(src, regions), regions);
		Assertions.assertEquals("example", methods.getFirst().packageName());
		Assertions.assertEquals("gpu", methods.getFirst().name());
	}

	@Test
	void commentsPreserveOffsetsAndLineBreaksInParsedJava() {
		final String src = "class C {\r\n @GlCompute void gpu() {\r\n void main() { uint i = 0; }\r\n }\r\n int value;\r\n}";
		final var regions = MethodRegionFinder.find(src);
		final String masked = MethodRegionFinder.maskBodies(src, regions);
		Assertions.assertEquals(src.length(), masked.length());
		Assertions.assertEquals(src.chars().filter(c -> c == '\n').count(), masked.chars().filter(c -> c == '\n').count());
		Assertions.assertTrue(masked.contains("/*"));
		Assertions.assertEquals(1, JavaAstAnalyzer.analyze("C.java", masked, regions).size());
	}

	@Test
	void generatedDispatchRetainsOriginalLinesForDiagnostics() {
		final String src = "class C {\n @GlCompute void gpu() {\n void main() {\n   uint i = 0;\n }\n }\n int broken = \"x\";\n}";
		final var region = MethodRegionFinder.find(src).getFirst();
		final String generated = MethodRegionFinder.preserveLines(src, region, "{\n helper();\n return;\n}");
		final String rewritten = new StringBuilder(src).replace(region.openBrace(), region.closeBrace() + 1, generated).toString();
		Assertions.assertEquals(src.lines().toList().size(), rewritten.lines().toList().size());
        Assertions.assertTrue(rewritten.contains("helper();  return;"));
		Assertions.assertEquals(src.substring(0, src.indexOf(" int broken")).lines().count(),
				rewritten.substring(0, rewritten.indexOf(" int broken")).lines().count());
	}
}
