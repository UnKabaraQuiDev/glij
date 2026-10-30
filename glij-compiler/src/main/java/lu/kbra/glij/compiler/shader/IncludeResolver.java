package lu.kbra.glij.compiler.shader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Portable, build-time #include resolution; paths cannot escape the configured root. */
public final class IncludeResolver {
	private static final Pattern INCLUDE = Pattern.compile("(?m)^\\s*#include\\s+[\"<]([^\">]+)[\">]\\s*$");
	private final Path root;

	public IncludeResolver(final Path root) {
		this.root = root.toAbsolutePath().normalize();
	}

	public String resolve(final String source) throws IOException {
		return this.resolve(source, this.root, new HashSet<>());
	}

	public String resolveFile(final String path) throws IOException {
		final Path file = this.checked(this.root.resolve(path));
		final Set<Path> active = new HashSet<>();
		active.add(file);
		return this.resolve(Files.readString(file), file.getParent(), active);
	}

	private String resolve(final String source, final Path directory, final Set<Path> active) throws IOException {
		final Matcher m = IncludeResolver.INCLUDE.matcher(source);
		final StringBuilder result = new StringBuilder();
		while (m.find()) {
			final Path file = this.checked(directory.resolve(m.group(1)));
			if (!Files.isRegularFile(file)) {
				throw new IOException("Missing shader include: " + file);
			}
			if (!active.add(file)) {
				throw new IOException("Cyclic shader include: " + file);
			}
			String included;
			try {
				included = this.resolve(Files.readString(file), file.getParent(), active);
			} finally {
				active.remove(file);
			}
			m.appendReplacement(result, Matcher.quoteReplacement(included));
		}
		m.appendTail(result);
		return result.toString();
	}

	private Path checked(final Path path) throws IOException {
		final Path normalized = path.toAbsolutePath().normalize();
		if (!normalized.startsWith(this.root)) {
			throw new IOException("Include escapes shader source root: " + normalized);
		}
		return normalized;
	}
}
