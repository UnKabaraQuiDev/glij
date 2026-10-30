package lu.kbra.glij.tests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lu.kbra.glij.compiler.MavenSourceLayout;
import lu.kbra.glij.compiler.GLiJCompiler;
import lu.kbra.glij.compiler.SourceDirectory;
import lu.kbra.glij.compiler.SourceLayout;

class SourceLayoutTest {
    @TempDir Path project;

    @Test
    void defaultsAndCentralConstants() throws Exception {
        SourceLayout layout = MavenSourceLayout.read(project);
        Assertions.assertEquals("glij", GLiJCompiler.DEFAULT_EXTENSION);
        Assertions.assertEquals(project.resolve("src/gen/resources"), layout.resourcesOutput());
        Assertions.assertEquals(1, layout.directories().size());
        SourceDirectory mapping = layout.directories().getFirst();
        Assertions.assertEquals(project.resolve("src/main/java"), mapping.source());
        Assertions.assertEquals(project.resolve("src/gen/java"), mapping.output());
        Assertions.assertEquals(Set.of(GLiJCompiler.DEFAULT_EXTENSION), mapping.fileExtensions());
    }

    @Test
    void readsMultipleDirectoriesWithExtensionInheritanceAndOverride() throws Exception {
        Files.writeString(project.resolve("pom.xml"), """
                <project><properties><custom.folder>src/custom</custom.folder></properties>
                  <build><plugins><plugin>
                    <artifactId>glij-maven-plugin</artifactId><configuration>
                      <fileExtensions>glij,other</fileExtensions>
                      <generatedResourcesDirectory>${project.basedir}/shaders-out</generatedResourcesDirectory>
                      <directories>
                        <directory><source>${project.basedir}/src/main/java</source>
                          <output>${project.basedir}/generated/one</output></directory>
                        <directory><source>${custom.folder}</source>
                          <output>generated/two</output><fileExtensions>.gpujava</fileExtensions></directory>
                      </directories>
                    </configuration>
                  </plugin></plugins></build>
                </project>
                """);
        SourceLayout layout = MavenSourceLayout.read(project);
        Assertions.assertEquals(2, layout.directories().size());
        Assertions.assertEquals(Set.of("glij", "other"), layout.directories().getFirst().fileExtensions());
        Assertions.assertEquals(Set.of("gpujava"), layout.directories().get(1).fileExtensions());
        Assertions.assertEquals(project.resolve("src/custom"), layout.directories().get(1).source());
        Assertions.assertEquals(project.resolve("generated/two"), layout.directories().get(1).output());
        Assertions.assertEquals(project.resolve("shaders-out"), layout.resourcesOutput());
        Path input = project.resolve("src/custom/demo/Compute.gpujava");
        Assertions.assertEquals(project.resolve("generated/two/demo/Compute.java"),
                layout.forSource(input).orElseThrow().generatedJavaFor(input, "package demo; class Compute {}"));
        Assertions.assertTrue(layout.forSource(project.resolve("src/custom/Compute.glij")).isEmpty());
    }

    @Test
    void supportsLegacySingleDirectoryConfiguration() throws Exception {
        Files.writeString(project.resolve("pom.xml"), """
                <project><build><plugins><plugin><artifactId>glij-maven-plugin</artifactId>
                  <configuration><sourceDirectory>legacy/in</sourceDirectory>
                  <generatedJavaDirectory>legacy/out</generatedJavaDirectory></configuration>
                </plugin></plugins></build></project>
                """);
        SourceLayout layout = MavenSourceLayout.read(project);
        Assertions.assertEquals(project.resolve("legacy/in"), layout.directories().getFirst().source());
        Assertions.assertEquals(project.resolve("legacy/out"), layout.directories().getFirst().output());
    }

    @Test
    void rejectsOverlapsAndDuplicateOutputs() {
        SourceDirectory first = new SourceDirectory(project.resolve("src/a"), project.resolve("gen/a"), Set.of("glij"));
        SourceDirectory duplicate = new SourceDirectory(project.resolve("src/b"), project.resolve("gen/a"), Set.of("glij"));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SourceLayout(java.util.List.of(first, duplicate), project.resolve("resources")));
        SourceDirectory nested = new SourceDirectory(project.resolve("src/a/sub"), project.resolve("gen/b"), Set.of("glij"));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SourceLayout(java.util.List.of(first, nested), project.resolve("resources")));
    }
}
