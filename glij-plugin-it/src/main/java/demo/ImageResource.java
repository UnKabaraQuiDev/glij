package demo;

import lu.kbra.standalone.gameengine.graph.shader.part.AbstractShader;

/** Example of an image-like custom Java type with an engine-specific binding call. */
public final class ImageResource {
	private int boundSlot = -1;
	private String boundName;

	public void bindUniform(final AbstractShader shader, final String name, final int slot) {
		this.boundName = name;
		this.boundSlot = slot;
	}

	public String boundName() {
		return this.boundName;
	}

	public int boundSlot() {
		return this.boundSlot;
	}
}
