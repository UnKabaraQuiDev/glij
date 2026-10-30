package lu.kbra.glij.binding;

import java.util.Map;

public record ParameterSpec(String name, String javaType, Map<String, Map<String, String>> annotations) {
	public boolean has(final String annotation) {
		return this.annotations.containsKey(annotation);
	}

	public String property(final String annotation, final String property, final String fallback) {
		return this.annotations.getOrDefault(annotation, Map.of()).getOrDefault(property, fallback);
	}

	public String simpleType() {
		String type = this.javaType;
		final int generic = type.indexOf('<');
		if (generic >= 0) {
			type = type.substring(0, generic);
		}
		final int dot = type.lastIndexOf('.');
		return dot < 0 ? type : type.substring(dot + 1);
	}
}
