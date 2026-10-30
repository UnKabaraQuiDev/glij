package lu.kbra.standalone.gameengine.generated.gl_wrapper;

/** Test-only OpenGL facade: records calls without requiring a GPU. */
public final class GL_W {

	public static final int GL_SHADER_STORAGE_BUFFER = 0x90D2;
	public static final int GL_SHADER_STORAGE_BARRIER_BIT = 0x2000;
	public static final int GL_SYNC_GPU_COMMANDS_COMPLETE = 0x9117;
	public static final int GL_SYNC_FLUSH_COMMANDS_BIT = 1;
	public static final long GL_TIMEOUT_IGNORED = -1L;
	public static final int GL_WAIT_FAILED = 0x911D;
	public static final int GL_ALREADY_SIGNALED = 0x911A;
	public static final int GL_CONDITION_SATISFIED = 0x911C;
	public static final int GL_TRIANGLES = 4;
	public static final int GL_UNSIGNED_INT = 0x1405;
	public static int dispatches;
	public static int[] lastGroups;
	public static int drawCalls;
	public static int bufferBindings;
	public static int deletions;

	private GL_W() {
	}

	public static void reset() {
		GL_W.dispatches = 0;
		GL_W.drawCalls = 0;
		GL_W.bufferBindings = 0;
		GL_W.deletions = 0;
	}

	public static void glDispatchCompute(final int x, final int y, final int z) {
		GL_W.dispatches++;
		GL_W.lastGroups = new int[] { x, y, z };
	}

	public static void glMemoryBarrier(final int bits) {
	}

	public static long glFenceSync(final int type, final int flags) {
		return 42L;
	}

	public static int glClientWaitSync(final long fence, final int flags, final long nanos) {
		return GL_W.GL_ALREADY_SIGNALED;
	}

	public static void glDeleteSync(final long fence) {
		GL_W.deletions++;
	}

	public static void glBindBufferBase(final int target, final int index, final int id) {
		GL_W.bufferBindings++;
	}

	public static void glDrawElements(final int mode, final int count, final int type, final long offset) {
		GL_W.drawCalls++;
	}

	public static void glDrawArrays(final int mode, final int first, final int count) {
		GL_W.drawCalls++;
	}

}
