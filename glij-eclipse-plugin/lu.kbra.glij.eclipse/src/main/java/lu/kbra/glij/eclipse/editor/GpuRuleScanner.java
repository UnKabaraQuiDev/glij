package lu.kbra.glij.eclipse.editor;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.NumberRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WordRule;

/** GLSL and Java tokens coexist in .glij, so color both in one viewer. */
public final class GpuRuleScanner extends RuleBasedScanner {
	private static final String[] KEYWORDS = {
			"void",
			"return",
			"if",
			"else",
			"for",
			"while",
			"do",
			"switch",
			"case",
			"break",
			"continue",
			"in",
			"out",
			"inout",
			"uniform",
			"layout",
			"readonly",
			"writeonly",
			"buffer",
			"shared",
			"const",
			"flat",
			"smooth",
			"noperspective",
			"highp",
			"mediump",
			"lowp",
			"precision",
			"discard",
			"struct",
			"public",
			"private",
			"protected",
			"static",
			"final",
			"new",
			"class",
			"interface",
			"extends",
			"implements",
			"import",
			"package",
			"this",
			"super" };
	private static final String[] TYPES = {
			"int",
			"uint",
			"float",
			"double",
			"bool",
			"vec2",
			"vec3",
			"vec4",
			"ivec2",
			"ivec3",
			"ivec4",
			"uvec2",
			"uvec3",
			"uvec4",
			"bvec2",
			"bvec3",
			"bvec4",
			"mat2",
			"mat3",
			"mat4",
			"mat3x2",
			"mat4x3",
			"sampler2D",
			"sampler3D",
			"samplerCube",
			"image2D",
			"image3D" };
	private static final String[] BUILTINS = {
			"gl_GlobalInvocationID",
			"gl_LocalInvocationID",
			"gl_WorkGroupID",
			"gl_NumWorkGroups",
			"gl_WorkGroupSize",
			"gl_Position",
			"gl_FragCoord",
			"gl_VertexID",
			"gl_InstanceID",
			"sin",
			"cos",
			"pow",
			"sqrt",
			"length",
			"normalize",
			"dot",
			"cross",
			"texture",
			"mix",
			"clamp",
			"min",
			"max",
			"determinant",
			"inverse",
			"barrier",
			"memoryBarrier" };

	public GpuRuleScanner(final GLiJColors colors) {
		final var keyword = new Token(colors.text("keyword"));
		final var types = new Token(colors.text("type"));
		final var builtins = new Token(colors.text("builtin"));
		final var comments = new Token(colors.text("comment"));
		final var strings = new Token(colors.text("string"));
		final var numbers = new Token(colors.text("number"));
		final var preprocessing = new Token(colors.text("preprocessor"));
		final List<IRule> rules = new ArrayList<>();
		rules.add(new EndOfLineRule("//", comments));
		rules.add(new MultiLineRule("/*", "*/", comments));
		rules.add(new SingleLineRule("\"", "\"", strings, '\\'));
		rules.add(new SingleLineRule("'", "'", strings, '\\'));
		rules.add(new EndOfLineRule("#", preprocessing));
		rules.add(new NumberRule(numbers));
		final WordRule words = new WordRule(new IWordDetector() {
			@Override
			public boolean isWordStart(final char c) {
				return Character.isJavaIdentifierStart(c);
			}

			@Override
			public boolean isWordPart(final char c) {
				return Character.isJavaIdentifierPart(c);
			}
		});
		for (final String word : GpuRuleScanner.KEYWORDS) {
			words.addWord(word, keyword);
		}
		for (final String word : GpuRuleScanner.TYPES) {
			words.addWord(word, types);
		}
		for (final String word : GpuRuleScanner.BUILTINS) {
			words.addWord(word, builtins);
		}
		rules.add(words);
		this.setRules(rules.toArray(IRule[]::new));
		this.setDefaultReturnToken(new Token(colors.text("default")));
	}
}
