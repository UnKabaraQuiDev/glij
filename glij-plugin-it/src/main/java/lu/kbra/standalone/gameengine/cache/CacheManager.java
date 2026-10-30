package lu.kbra.standalone.gameengine.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import lu.kbra.standalone.gameengine.graph.shader.part.AbstractShader;

public class CacheManager {
	private final ConcurrentHashMap<Class<?>, AbstractShader> shaders = new ConcurrentHashMap<>();

	public <T extends AbstractShader> T getAbstractShader(final Class<T> type, final Supplier<T> supplier) {
		return type.cast(this.shaders.computeIfAbsent(type, ignored -> supplier.get()));
	}
}
