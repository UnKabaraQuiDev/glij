package lu.kbra.glij.compiler.shader;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lu.kbra.glij.compiler.source.MethodRegionFinder;

/** Parses the two named rendering stage scopes independently of the Java AST. */
public final class ShaderBodyParser {
	private static final Pattern STAGE = Pattern.compile("\\b(vertex|fragment)\\s*\\(");
	private static final Pattern ARG = Pattern.compile("(?:@AttributeLocation\\((\\d+)\\)\\s*)?(in|out)\\s+(\\w+)\\s+(\\w+)");

	public record StageArgument(String qualifier, String type, String name, String location) {
		public String declaration() {
			return (this.location == null ? "" : "layout(location = " + this.location + ") ") + this.qualifier + " " + this.type + " "
					+ this.name + ";\n";
		}
	}

	public record Stage(String name, List<StageArgument> parameters, String body) {
	}

	private ShaderBodyParser() {
	}

	public static List<Stage> renderingStages(final String body) {
		final String lexical = MethodRegionFinder.maskNonCode(body);
		final Matcher matcher = ShaderBodyParser.STAGE.matcher(lexical);
		final List<Stage> stages = new ArrayList<>();
		int end = 0;
		while (matcher.find(end)) {
			final int openParen = matcher.end() - 1;
			final int closeParen = MethodRegionFinder.matching(lexical, openParen, '(', ')');
			if (closeParen < 0) {
				throw new IllegalArgumentException("Unclosed stage arguments for " + matcher.group(1));
			}
			int brace = closeParen + 1;
			while (brace < lexical.length() && Character.isWhitespace(lexical.charAt(brace))) {
				brace++;
			}
			if (brace < lexical.length() && lexical.charAt(brace) == ':') {
				brace++;
			}
			while (brace < lexical.length() && Character.isWhitespace(lexical.charAt(brace))) {
				brace++;
			}
			if (brace >= lexical.length() || lexical.charAt(brace) != '{') {
				throw new IllegalArgumentException("Expected stage body for " + matcher.group(1));
			}
			final int close = MethodRegionFinder.matching(lexical, brace, '{', '}');
			if (close < 0) {
				throw new IllegalArgumentException("Unclosed stage body for " + matcher.group(1));
			}
			final List<StageArgument> args = new ArrayList<>();
			final String arguments = body.substring(openParen + 1, closeParen).trim();
			if (!arguments.isEmpty()) {
				for (final String value : arguments.split(",")) {
					final Matcher arg = ShaderBodyParser.ARG.matcher(value.trim());
					if (!arg.matches()) {
						throw new IllegalArgumentException("Invalid stage parameter: " + value);
					}
					args.add(new StageArgument(arg.group(2), arg.group(3), arg.group(4), arg.group(1)));
				}
			}
			stages.add(new Stage(matcher.group(1), List.copyOf(args), body.substring(brace + 1, close)));
			end = close + 1;
		}
		if (stages.size() != 2 || !"vertex".equals(stages.get(0).name()) || !"fragment".equals(stages.get(1).name())) {
			throw new IllegalArgumentException("@GlRender requires vertex(...) and fragment(...) stages in this order");
		}
		return stages;
	}
}
