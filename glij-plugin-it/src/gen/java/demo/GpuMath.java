package demo;

import lu.kbra.glij.api.ShaderFunction;

/** GLSL-only functions are stripped from the generated Java source. */
public final class GpuMath {
    @ShaderFunction
    public static float square(float value) {
    throw new UnsupportedOperationException("GPU-only shader function");
}
}
