package demo;

import java.lang.IllegalStateException;
import java.lang.Override;
import java.lang.String;
import javax.annotation.processing.Generated;
import lu.kbra.glij.api.Semaphore;
import lu.kbra.standalone.gameengine.cache.attrib.FloatAttribArray;
import lu.kbra.standalone.gameengine.generated.gl_wrapper.GL_W;
import lu.kbra.standalone.gameengine.graph.shader.ComputeShader;
import lu.kbra.standalone.gameengine.graph.shader.part.AbstractShaderPart;
import lu.kbra.standalone.gameengine.graph.shader.part.ComputeShaderPart;
import org.joml.Vector3i;
import org.joml.Vector3ic;

@Generated("GLiJ")
public final class ParticleJob_compute_ComputeShader extends ComputeShader {
    public static final Vector3ic LOCAL_SIZE = new Vector3i(64, 1, 1);

    public static final String COUNT = "count";

    public ParticleJob_compute_ComputeShader() {
        super((ComputeShaderPart) AbstractShaderPart.load("classpath:/shaders/ParticleJob_compute_ComputeShader.comp", getBaseBuildingDeps(LOCAL_SIZE)), LOCAL_SIZE);
    }

    @Override
    public void createUniforms() {
        super.createUniforms();
        super.createUniform("count");
    }

    public void bindResources(FloatAttribArray input, FloatAttribArray output, int count) {
        super.bind();
        lu.kbra.standalone.gameengine.generated.gl_wrapper.GL_W.glBindBufferBase(lu.kbra.standalone.gameengine.generated.gl_wrapper.GL_W.GL_SHADER_STORAGE_BUFFER, 0, input.getGlId());

        lu.kbra.standalone.gameengine.generated.gl_wrapper.GL_W.glBindBufferBase(lu.kbra.standalone.gameengine.generated.gl_wrapper.GL_W.GL_SHADER_STORAGE_BUFFER, 1, output.getGlId());

        super.setUniform("count", count);

    }

    public Semaphore dispatch(boolean blocking, Vector3ic groups) {
        GL_W.glDispatchCompute(groups.x(), groups.y(), groups.z());
        GL_W.glMemoryBarrier(GL_W.GL_SHADER_STORAGE_BARRIER_BIT);
        long fence = GL_W.glFenceSync(GL_W.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        if (blocking) {
            int status;
            try {
                status = GL_W.glClientWaitSync(fence, GL_W.GL_SYNC_FLUSH_COMMANDS_BIT, (int) 1e12);
            } finally {
                GL_W.glDeleteSync(fence);
            }
            if (status == GL_W.GL_WAIT_FAILED) {
                throw new IllegalStateException("GPU sync failed");
            }
            return null;
        }
        return new Semaphore(
            () -> { int status = GL_W.glClientWaitSync(fence, 0, 0L);
                if (status == GL_W.GL_WAIT_FAILED) throw new IllegalStateException("GPU sync failed");
                return status == GL_W.GL_ALREADY_SIGNALED || status == GL_W.GL_CONDITION_SATISFIED; },
            () -> { int status = GL_W.glClientWaitSync(fence, GL_W.GL_SYNC_FLUSH_COMMANDS_BIT, (int) 1e12);
                if (status == GL_W.GL_WAIT_FAILED) throw new IllegalStateException("GPU sync failed"); },
            () -> GL_W.glDeleteSync(fence));
    }
}
