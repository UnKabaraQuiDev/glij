package lu.kbra.glij.binding;

/** Register rules via ServiceLoader or directly with BindingRegistry. */
public interface BindingProvider {
	void register(BindingRegistry registry);
}
