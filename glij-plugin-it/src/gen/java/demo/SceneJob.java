package demo;

import lu.kbra.glij.api.*;
import lu.kbra.standalone.gameengine.cache.CacheManager;
import lu.kbra.standalone.gameengine.geom.Mesh;

public final class SceneJob {

	private final CacheManager cacheManager = new CacheManager();

	@GlRender(version=460)
    @ShaderCache("cacheManager")
    public void draw(Mesh mesh, CustomResource custom, @Uniform ImageResource image, @Uniform float brightness) {
    final var __polyCache = cacheManager; final SceneJob_draw_RenderShader __polyShader = __polyCache.getAbstractShader(SceneJob_draw_RenderShader.class, SceneJob_draw_RenderShader::new); __polyShader.bindResources(custom, image, brightness); __polyShader.render(mesh);
    /* Original GLiJ shader line. */
    /* Original GLiJ shader line. */
    /* Original GLiJ shader line. */
    /* Original GLiJ shader line. */
    /* Original GLiJ shader line. */
    /* Original GLiJ shader line. */
    /* Original GLiJ shader line. */
    /* Original GLiJ shader line. */
    /* Original GLiJ shader line. */
    /* Original GLiJ shader line. */
}

}
