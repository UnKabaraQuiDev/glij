package lu.kbra.glij.binding;

import java.util.Set;

/** Java parameter names may be reserved GLSL identifiers; generated names are isolated. */
public final class GpuNames {
	private static final Set<String> RESERVED = Set.of("input",
			"output",
			"attribute",
			"varying",
			"buffer",
			"common",
			"partition",
			"active",
			"filter",
			"resource",
			"namespace",
			"using",
			"cast",
			"class",
			"union",
			"enum",
			"typedef",
			"template",
			"this",
			"long",
			"short",
			"half",
			"fixed",
			"unsigned",
			"superp",
			"subroutine",
			"sampler",
			"interface",
			"public",
			"static",
			"extern",
			"external",
			"sizeof",
			"goto",
			"inline",
			"noinline",
			"volatile",
			"restrict",
			"readable",
			"writable",
			"atomic_uint",
			"packed",
			"row_major");

	private GpuNames() {
	}

	public static String shaderName(final String original) {
		return GpuNames.RESERVED.contains(original) || original.startsWith("gl_") ? "poly_" + original : original;
	}
}
