package lu.kbra.glij.api;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** An OpenGL completion fence. All operations must be called with the owning GL context current. */
public class Semaphore implements AutoCloseable {
	private final BooleanSupplier poll;
	private final Runnable waitForCompletion;
	private final Runnable release;
	private final AtomicBoolean closed = new AtomicBoolean();

	public Semaphore(final BooleanSupplier poll, final Runnable waitForCompletion, final Runnable release) {
		this.poll = Objects.requireNonNull(poll);
		this.waitForCompletion = Objects.requireNonNull(waitForCompletion);
		this.release = Objects.requireNonNull(release);
	}

	public boolean isDone() {
		return this.poll.getAsBoolean();
	}

	public void await() {
		this.waitForCompletion.run();
	}

	@Override
	public void close() {
		if (this.closed.compareAndSet(false, true)) {
			this.release.run();
		}
	}
}
