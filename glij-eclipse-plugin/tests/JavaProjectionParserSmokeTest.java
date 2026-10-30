import java.nio.file.Files;
import java.nio.file.Path;
import lu.kbra.glij.compiler.ast.JavaAstAnalyzer;
import lu.kbra.glij.compiler.source.MethodRegionFinder;

public class JavaProjectionParserSmokeTest {
    public static void main(String[] args) throws Exception {
        String input = Files.readString(Path.of(args[0]));
        var regions = MethodRegionFinder.find(input);
        if (regions.size() != 1) throw new AssertionError("Expected one GPU method");
        var methods = JavaAstAnalyzer.analyzeLenient("ParticleJob.java", MethodRegionFinder.maskBodies(input, regions), regions);
        if (methods.size() != 1 || !methods.getFirst().has("GlCompute"))
            throw new AssertionError("Expected to retain @GlCompute in the virtual Java unit");
        // The live projection must tolerate a Java syntax error outside the shader region
        // and let JDT's problem requestor report it.
        String broken = input.replace("private final CacheManager cacheManager =", "private final CacheManager cacheManager ??? =");
        var brokenRegions = MethodRegionFinder.find(broken);
        var live = JavaAstAnalyzer.analyzeLenient("ParticleJob.java", MethodRegionFinder.maskBodies(broken, brokenRegions), brokenRegions);
        if (live.size() != 1) throw new AssertionError("Lost the shader method during an ordinary Java typing error");
        System.out.println("JavaProjectionParserSmokeTest passed");
    }
}
