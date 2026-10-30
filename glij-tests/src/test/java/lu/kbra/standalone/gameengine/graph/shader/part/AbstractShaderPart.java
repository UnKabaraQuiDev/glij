package lu.kbra.standalone.gameengine.graph.shader.part;

import java.util.Map;

public class AbstractShaderPart {
	public static AbstractShaderPart load(final String path) {
		return path.endsWith(".comp") ? new ComputeShaderPart() : new AbstractShaderPart();
	}

	public static AbstractShaderPart load(final String path, final Map<String, Object> values) {
		return AbstractShaderPart.load(path);
	}
}
