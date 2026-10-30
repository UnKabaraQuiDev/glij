package lu.kbra.glij.compiler.ast;

import java.util.List;
import java.util.Map;

import lu.kbra.glij.binding.ParameterSpec;
import lu.kbra.glij.compiler.source.MethodRegion;

public record MethodInfo(
		String packageName,
		String className,
		String name,
		String returnType,
		List<String> imports,
		List<ParameterSpec> parameters,
		Map<String, Map<String, String>> annotations,
		MethodRegion region) {
	public boolean has(final String name) {
		return this.annotations.containsKey(name);
	}

	public String annotationValue(final String name, final String property, final String fallback) {
		return this.annotations.getOrDefault(name, Map.of()).getOrDefault(property, fallback);
	}
}
