package lu.kbra.glij.compiler.source;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Locates GPU method bodies without asking a Java parser to accept embedded GLSL. */
public final class MethodRegionFinder {
	private static final Pattern SHADER = Pattern.compile("@(?:[\\w$]+\\.)*(GlCompute|GlRender|ShaderFunction)\\b");

	private MethodRegionFinder() {
	}

	public static List<MethodRegion> find(final String source) {
		final String lexical = MethodRegionFinder.maskNonCode(source);
		final Matcher matcher = MethodRegionFinder.SHADER.matcher(lexical);
		final List<MethodRegion> regions = new ArrayList<>();
		int processedUntil = -1;
		while (matcher.find()) {
			if (matcher.start() < processedUntil) {
				continue;
			}
			final int open = MethodRegionFinder.bodyOpen(lexical, matcher.end());
			if (open < 0) {
				throw new IllegalArgumentException("No method body following @" + matcher.group(1));
			}
			final int close = MethodRegionFinder.matching(lexical, open, '{', '}');
			if (close < 0) {
				throw new IllegalArgumentException("Unclosed shader method body at offset " + open);
			}
			regions.add(new MethodRegion(open, close, source.substring(open + 1, close), matcher.group(1)));
			processedUntil = close + 1;
		}
		return List.copyOf(regions);
	}

	public static String maskBodies(final String source, final List<MethodRegion> regions) {
		final char[] chars = source.toCharArray();
		for (final MethodRegion region : regions) {
			for (int i = region.openBrace() + 1; i < region.closeBrace(); i++) {
				if (chars[i] != '\n' && chars[i] != '\r') {
					chars[i] = ' ';
				}
			}
			// The temporary source must remain offset-identical for JavacTask.
			// Block comments make it clear to tools that shader bodies are opaque.
			int first = region.openBrace() + 1;
			int last = region.closeBrace() - 1;
			while (first + 1 < last && (MethodRegionFinder.isLineBreak(chars[first]) || MethodRegionFinder.isLineBreak(chars[first + 1]))) {
				first++;
			}
			while (last - 1 > first && (MethodRegionFinder.isLineBreak(chars[last]) || MethodRegionFinder.isLineBreak(chars[last - 1]))) {
				last--;
			}
			if (last - first >= 3) {
				chars[first] = '/';
				chars[first + 1] = '*';
				chars[last - 1] = '*';
				chars[last] = '/';
			}
		}
		return new String(chars);
	}

	private static boolean isLineBreak(final char value) {
		return value == '\n' || value == '\r';
	}

	/**
	 * Keep every original line break in generated Java. Diagnostic line numbers after the shader method
	 * therefore refer to the same lines in the original GLiJ source. The dispatch logic must fit on one physical
	 * line; later lines are comments.
	 */
	public static String preserveLines(final String source, final MethodRegion region, final String javaBody) {
		final String trimmed = javaBody.strip();
		if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
			throw new IllegalArgumentException("Expected a generated Java block");
		}
		// The replacement spans the original opening/closing braces. Keep the exact number
		// of line terminators to preserve diagnostics for every following Java line.
		final String statements = trimmed.substring(1, trimmed.length() - 1)
				.replaceAll("\\R+", " ").strip();
		final java.util.regex.Matcher breaks = Pattern.compile("\\r\\n|\\n|\\r")
				.matcher(source.substring(region.openBrace(), region.closeBrace() + 1));
		final java.util.List<String> endings = new java.util.ArrayList<>();
		while (breaks.find()) endings.add(breaks.group());
		if (endings.isEmpty()) return "{ " + statements + " }";
		final StringBuilder result = new StringBuilder("{").append(endings.getFirst())
				.append("    ").append(statements);
		for (int i = 1; i < endings.size(); i++) {
			result.append(endings.get(i));
			if (i < endings.size() - 1) result.append("    /* Original GLiJ shader line. */");
		}
		return result.append("}").toString();
	}

	/**
	 * Find an opening method brace, skipping annotation arguments and array initializers inside
	 * parentheses.
	 */
	private static int bodyOpen(final String lexical, final int from) {
		int parens = 0;
		int square = 0;
		for (int i = from; i < lexical.length(); i++) {
			final char c = lexical.charAt(i);
			switch (c) {
			case '(' -> parens++;
			case ')' -> parens--;
			case '[' -> square++;
			case ']' -> square--;
			default -> {
				if (c == '{' && parens == 0 && square == 0) {
					return i;
				} else if (c == ';' && parens == 0 && square == 0) {
					return -1;
				}
			}
			}
		}
		return -1;
	}

	public static int matching(final String lexical, final int start, final char opening, final char closing) {
		int depth = 0;
		for (int i = start; i < lexical.length(); i++) {
			final char c = lexical.charAt(i);
			if (c == opening) {
				depth++;
			} else if (c == closing && --depth == 0) {
				return i;
			}
		}
		return -1;
	}

	/** Retains offsets and line numbers while hiding comments, strings and character literals. */
	public static String maskNonCode(final String text) {
		final char[] result = text.toCharArray();
		int i = 0;
		while (i < result.length) {
			final char c = text.charAt(i);
			if (c == '/' && i + 1 < result.length && (text.charAt(i + 1) == '/' || text.charAt(i + 1) == '*')) {
				final boolean line = text.charAt(i + 1) == '/';
				int end = i + 2;
				if (line) {
					while (end < result.length && text.charAt(end) != '\n') {
						end++;
					}
				} else {
					while (end + 1 < result.length && !(text.charAt(end) == '*' && text.charAt(end + 1) == '/')) {
						end++;
					}
					if (end + 1 >= result.length) {
						throw new IllegalArgumentException("Unterminated block comment");
					}
					end += 2;
				}
				MethodRegionFinder.hide(result, i, end);
				i = end;
				continue;
			}
			if (c == '"' || c == '\'') {
				final boolean triple = c == '"' && text.startsWith("\"\"\"", i);
				int end = i + (triple ? 3 : 1);
				boolean finished = false;
				while (end < result.length) {
					if (text.charAt(end) == '\\') {
						end += 2;
						continue;
					}
					if (triple ? text.startsWith("\"\"\"", end) : text.charAt(end) == c) {
						end += triple ? 3 : 1;
						finished = true;
						break;
					}
					end++;
				}
				if (!finished) {
					throw new IllegalArgumentException("Unterminated string or character literal");
				}
				MethodRegionFinder.hide(result, i, end);
				i = end;
				continue;
			}
			i++;
		}
		return new String(result);
	}

	private static void hide(final char[] target, final int start, final int end) {
		for (int j = start; j < end; j++) {
			if (target[j] != '\n' && target[j] != '\r') {
				target[j] = ' ';
			}
		}
	}
}
