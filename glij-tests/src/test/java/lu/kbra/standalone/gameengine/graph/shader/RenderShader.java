package lu.kbra.standalone.gameengine.graph.shader;

import lu.kbra.standalone.gameengine.graph.shader.part.AbstractShader;
import lu.kbra.standalone.gameengine.graph.shader.part.AbstractShaderPart;

public abstract class RenderShader extends AbstractShader {
	public RenderShader(final AbstractShaderPart... parts) {
		super(parts);
	}
}
