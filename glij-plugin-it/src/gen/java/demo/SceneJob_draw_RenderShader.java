package demo;

import java.lang.Override;
import java.lang.String;
import javax.annotation.processing.Generated;
import lu.kbra.standalone.gameengine.geom.Mesh;
import lu.kbra.standalone.gameengine.graph.shader.RenderShader;
import lu.kbra.standalone.gameengine.graph.shader.part.AbstractShaderPart;

@Generated("GLiJ")
public final class SceneJob_draw_RenderShader extends RenderShader {
    public static final String IMAGE = "image";

    public static final String BRIGHTNESS = "brightness";

    public SceneJob_draw_RenderShader() {
        super(AbstractShaderPart.load("classpath:/shaders/SceneJob_draw_RenderShader.vert"), AbstractShaderPart.load("classpath:/shaders/SceneJob_draw_RenderShader.frag"));
    }

    @Override
    public void createUniforms() {
        super.createUniform("image");
        super.createUniform("brightness");
    }

    public void bindResources(CustomResource custom, ImageResource image, float brightness) {
        super.bind();
        custom.bind();

        image.bindUniform(this, "image", 0);

        super.setUniform("brightness", brightness);

    }

    public void render(Mesh mesh) {
        mesh.bind();
        if (mesh.getIndicesCount() > 0) {
          lu.kbra.standalone.gameengine.generated.gl_wrapper.GL_W.glDrawElements(lu.kbra.standalone.gameengine.generated.gl_wrapper.GL_W.GL_TRIANGLES, mesh.getIndicesCount(), lu.kbra.standalone.gameengine.generated.gl_wrapper.GL_W.GL_UNSIGNED_INT, 0L);
        } else {
          lu.kbra.standalone.gameengine.generated.gl_wrapper.GL_W.glDrawArrays(lu.kbra.standalone.gameengine.generated.gl_wrapper.GL_W.GL_TRIANGLES, 0, mesh.getVertexCount());
        }
        mesh.unbind();

    }
}
