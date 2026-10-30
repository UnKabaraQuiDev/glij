package lu.kbra.glij.binding;

import java.util.HashSet;
import java.util.Set;

public final class BindingContext {
	private final Set<Integer> storage = new HashSet<>();
	private final Set<Integer> textures = new HashSet<>();

	public int storageBinding(final ParameterSpec parameter) {
		return this.allocate(this.storage, parameter, "Binding");
	}

	public int textureBinding(final ParameterSpec parameter) {
		return this.allocate(this.textures, parameter, "Binding");
	}

	private int allocate(final Set<Integer> reserved, final ParameterSpec parameter, final String annotation) {
		int index = parameter.has(annotation) ? Integer.parseInt(parameter.property(annotation, "value", "0")) : 0;
		if (!parameter.has(annotation)) {
			while (reserved.contains(index)) {
				index++;
			}
		}
		if (index < 0 || !reserved.add(index)) {
			throw new IllegalArgumentException("Duplicate or invalid binding " + index + " for " + parameter.name());
		}
		return index;
	}
}
