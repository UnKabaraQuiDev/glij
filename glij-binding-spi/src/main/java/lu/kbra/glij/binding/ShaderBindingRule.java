package lu.kbra.glij.binding;

public interface ShaderBindingRule {
	boolean supports(ParameterSpec parameter, ShaderTarget target);

	int priority();

	BindingPlan plan(ParameterSpec parameter, ShaderTarget target, BindingContext context);
}
