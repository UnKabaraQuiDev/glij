package lu.kbra.glij.tests;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import lu.kbra.glij.binding.BindingPlan;
import lu.kbra.glij.binding.ParameterSpec;
import lu.kbra.glij.compiler.ast.MethodInfo;
import lu.kbra.glij.compiler.generation.ShaderClassGenerator;

/** Regression tests for the updated plugin integration fixture signatures. */
class ShaderClassGeneratorTest {
    private static ParameterSpec p(String name, String type, String... annotations) {
        java.util.Map<String, Map<String, String>> flags = new java.util.HashMap<>();
        for (String annotation : annotations) flags.put(annotation, Map.of());
        return new ParameterSpec(name, type, flags);
    }
    private static MethodInfo method(String name, String returnType, String annotation, List<ParameterSpec> params) {
        return new MethodInfo("demo", "SceneJob", name, returnType,
                List.of("import lu.kbra.standalone.gameengine.geom.Mesh;", "import org.joml.Vector3i;"),
                params, Map.of(annotation, Map.of(), "ShaderCache", Map.of("value", "cacheManager")), null);
    }
    @Test void renderingDrawAndBindingArgumentsAreDistinct() {
        MethodInfo m = method("draw", "void", "GlRender", List.of(p("mesh", "Mesh"),
                p("custom", "CustomResource"), p("image", "ImageResource", "Uniform"),
                p("brightness", "float", "Uniform")));
        var bindings = List.of(BindingPlan.empty(), new BindingPlan("", "custom.bind();", "", null),
                new BindingPlan("", "image.bindUniform(this, \"image\", 0);", "", "image"),
                new BindingPlan("", "super.setUniform(\"brightness\", brightness);", "", "brightness"));
        String java = ShaderClassGenerator.javaClass(m, bindings, "SceneJob_draw_RenderShader", "shaders/draw");
        assertTrue(java.contains("bindResources(CustomResource custom, ImageResource image, float brightness)"));
        assertTrue(java.contains("render(Mesh mesh)"));
        assertFalse(java.contains("bindResources(mesh"));
        assertFalse(java.contains("bindResources(custom, image, brightness);"));
        String delegate = ShaderClassGenerator.delegate(m, "SceneJob_draw_RenderShader");
        assertTrue(delegate.contains("__polyShader.bindResources(custom, image, brightness)"));
        assertTrue(delegate.contains("__polyShader.render(mesh)"));
        assertFalse(delegate.contains("\n"));
    }
    @Test void computeDispatchHasOnlyDeclaredControls() {
        MethodInfo none = method("compute", "void", "GlCompute", List.of(p("input", "float", "Uniform")));
        String minimal = ShaderClassGenerator.javaClass(none, List.of(BindingPlan.empty()), "Job_compute_ComputeShader", "shaders/compute");
        assertTrue(minimal.contains("dispatch()"));
        assertTrue(ShaderClassGenerator.delegate(none, "Job_compute_ComputeShader").contains("dispatch()"));
        MethodInfo both = method("compute", "Semaphore", "GlCompute", List.of(
                p("blocking", "boolean", "Blocking"), p("groups", "Vector3i", "GroupSize")));
        String full = ShaderClassGenerator.javaClass(both, List.of(BindingPlan.empty(), BindingPlan.empty()),
                "Job_compute_ComputeShader", "shaders/compute");
        assertTrue(full.contains("dispatch(boolean blocking, Vector3ic groups)"));
        assertTrue(ShaderClassGenerator.delegate(both, "Job_compute_ComputeShader").contains("dispatch(blocking, groups)"));
    }
}
