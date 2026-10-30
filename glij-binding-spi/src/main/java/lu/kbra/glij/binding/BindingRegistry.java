package lu.kbra.glij.binding;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

public final class BindingRegistry {
	private final List<ShaderBindingRule> rules = new ArrayList<>();

	public BindingRegistry register(final ShaderBindingRule rule) {
		this.rules.add(rule);
		return this;
	}

	/**
	 * Fluent registration for simple Java-type bindings; complex cases can implement ShaderBindingRule.
	 */
	public RuleBuilder rule(final String... javaTypes) {
		return new RuleBuilder(Set.of(javaTypes));
	}

	public RuleBuilder rule(final Class<?>... javaTypes) {
		return new RuleBuilder(
				java.util.Arrays.stream(javaTypes).map(Class::getName).collect(java.util.stream.Collectors.toUnmodifiableSet()));
	}

	public BindingRegistry discover(final ClassLoader loader) {
		ServiceLoader.load(BindingProvider.class, loader).forEach(provider -> provider.register(this));
		return this;
	}

	public BindingPlan plan(final ParameterSpec p, final ShaderTarget target, final BindingContext context) {
		ShaderBindingRule chosen = null;
		for (final ShaderBindingRule rule : this.rules) {
			if (!rule.supports(p, target)) {
				continue;
			}
			if (chosen == null || rule.priority() > chosen.priority()) {
				chosen = rule;
			} else if (rule.priority() == chosen.priority()) {
				throw new IllegalArgumentException("Ambiguous binding rules for " + p.name());
			}
		}
		if (chosen == null) {
			throw new IllegalArgumentException("No binding rule for " + p.javaType() + " " + p.name());
		}
		return chosen.plan(p, target, context);
	}

	@FunctionalInterface
	public interface BindingFactory {
		BindingPlan create(ParameterSpec parameter, ShaderTarget target, BindingContext context);
	}

	public final class RuleBuilder {
		private final Set<String> types;
		private EnumSet<ShaderTarget> targets = EnumSet.allOf(ShaderTarget.class);
		private int priority = 50;
		private BindingFactory factory;

		private RuleBuilder(final Set<String> types) {
			this.types = types;
		}

		public RuleBuilder forPipeline(final ShaderTarget first, final ShaderTarget... more) {
			this.targets = EnumSet.of(first, more);
			return this;
		}

		public RuleBuilder priority(final int value) {
			this.priority = value;
			return this;
		}

		public RuleBuilder binding(final BindingFactory value) {
			this.factory = value;
			return this;
		}

		public BindingRegistry register() {
			if (this.factory == null) {
				throw new IllegalStateException("Binding function required");
			}
			final int rank = this.priority;
			final EnumSet<ShaderTarget> accepted = this.targets.clone();
			final BindingFactory binding = this.factory;
			BindingRegistry.this.register(new ShaderBindingRule() {
				@Override
				public boolean supports(final ParameterSpec parameter, final ShaderTarget target) {
					return accepted.contains(target) && RuleBuilder.this.types.stream()
							.anyMatch(type -> type.equals(parameter.javaType()) || type.equals(parameter.simpleType())
									|| type.endsWith("." + parameter.simpleType()));
				}

				@Override
				public int priority() {
					return rank;
				}

				@Override
				public BindingPlan plan(final ParameterSpec p, final ShaderTarget t, final BindingContext context) {
					return binding.create(p, t, context);
				}
			});
			return BindingRegistry.this;
		}
	}
}
