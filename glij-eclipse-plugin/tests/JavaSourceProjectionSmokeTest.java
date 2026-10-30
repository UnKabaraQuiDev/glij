import java.util.List;
import lu.kbra.glij.compiler.JavaSourceProjection;

public class JavaSourceProjectionSmokeTest {
    public static void main(String[] args) {
        String original = "import java.util.List;\nclass X {\n  void test() { glsl(); }\n  int value;\n}\n";
        int from = original.indexOf("{ glsl()");
        int to = original.indexOf("}", from) + 1;
        var map = JavaSourceProjection.create(original, List.of(
                new JavaSourceProjection.Replacement(from, to, "{ return; } /* generated */")));
        int before = original.indexOf("List");
        int after = original.indexOf("value");
        if (map.toJavaOffset(before).orElseThrow() != before) throw new AssertionError("pre-shader offset");
        int generatedAfter = map.toJavaOffset(after).orElseThrow();
        if (!map.toOriginalRange(generatedAfter, generatedAfter + 5).orElseThrow()
                .equals(new JavaSourceProjection.Range(after, after + 5))) throw new AssertionError("after-shader mapping");
        if (map.toJavaOffset(from + 3).isPresent()) throw new AssertionError("GLSL offset was mapped");
        if (map.toOriginalOffset(from + 3).isPresent()) throw new AssertionError("delegate offset was mapped");
        if (map.toOriginalRange(from, from + 6).isPresent()) throw new AssertionError("generated edit was mapped");
        if (map.toOriginalRange(0, before).isEmpty()) throw new AssertionError("unchanged import range");
        String organized = map.javaSource().replace("import java.util.List;", "import java.util.ArrayList;\nimport java.util.List;");
        var edit = map.translateJavaChange(organized).orElseThrow();
        String updatedOriginal = original.substring(0, edit.start()) + edit.replacement() + original.substring(edit.end());
        if (!updatedOriginal.contains("import java.util.ArrayList;")) throw new AssertionError("import change not applied");
        if (!updatedOriginal.contains("{ glsl(); }")) throw new AssertionError("shader code modified");
        if (map.translateJavaChange(map.javaSource().replace("generated", "changed")).isPresent())
            throw new AssertionError("edit inside generated delegate was allowed");
        try {
            JavaSourceProjection.create(original, List.of(new JavaSourceProjection.Replacement(5, 3, "")));
            throw new AssertionError("invalid range accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("JavaSourceProjectionSmokeTest passed");
    }
}
