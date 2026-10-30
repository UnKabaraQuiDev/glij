package lu.kbra.standalone.gameengine.graph.shader.part;

public abstract class AbstractShader {
	protected AbstractShader(final AbstractShaderPart... parts) {
		this.createUniforms();
	}

	public abstract void createUniforms();

	public void createUniform(final String name) {
	}

	public void setUniform(final String name, final Object value) {
	}

	public void bind() {
	}
}
