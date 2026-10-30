package demo;

import lu.kbra.glij.api.*;
import lu.kbra.standalone.gameengine.cache.CacheManager;
import lu.kbra.standalone.gameengine.cache.attrib.FloatAttribArray;
import org.joml.Vector3i;

/** Test input: Maven must generate, compile and invoke this class. */
public final class ParticleJob {

	private final CacheManager cacheManager = new CacheManager();

	@GlCompute(version=460)
    @LocalSize(x=64)
    @ShaderCache("cacheManager")
    @Include(type=GpuMath.class, methodName="square")
    public Semaphore compute(@Blocking boolean wait, @GroupSize Vector3i groups,
                             @Layout("std430") @Access(AccessMode.READ_ONLY) FloatAttribArray input,
                             @Layout("std430") @Access(AccessMode.WRITE_ONLY) FloatAttribArray output,
                             @Uniform int count) {
    final var __polyCache = cacheManager; final ParticleJob_compute_ComputeShader __polyShader = __polyCache.getAbstractShader(ParticleJob_compute_ComputeShader.class, ParticleJob_compute_ComputeShader::new); __polyShader.bindResources(input, output, count); return __polyShader.dispatch(wait, groups);
    /* Original GLiJ shader line. */
    /* Original GLiJ shader line. */
    /* Original GLiJ shader line. */
    /* Original GLiJ shader line. */
}

}
