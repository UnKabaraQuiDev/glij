package demo;

public final class CustomResource {
	private int binds;

	public void bind() {
		this.binds++;
	}

	public int binds() {
		return this.binds;
	}
}
