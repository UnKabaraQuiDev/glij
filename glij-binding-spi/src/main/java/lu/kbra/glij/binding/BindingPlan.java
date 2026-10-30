package lu.kbra.glij.binding;

/** GPU declaration, Java binding operation, optional draw operation and uniform setup. */
public record BindingPlan(String shaderDeclaration, String javaBinding, String javaDraw, String uniformName) {
	public static BindingPlan empty() {
		return new BindingPlan("", "", "", null);
	}
}
