package lu.kbra.standalone.gameengine.graph.shader;

import java.util.Map;

import org.joml.Vector3ic;

import lu.kbra.standalone.gameengine.graph.shader.part.AbstractShader;
import lu.kbra.standalone.gameengine.graph.shader.part.ComputeShaderPart;

public class ComputeShader extends AbstractShader {
	public ComputeShader(final ComputeShaderPart part, final Vector3ic size) {
		super(part);
	}

	public static Map<String, Object> getBaseBuildingDeps(final Vector3ic size) {
		return Map.of();
	}

	@Override
	public void createUniforms() {
	}
}
