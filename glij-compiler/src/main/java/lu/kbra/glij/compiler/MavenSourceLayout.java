package lu.kbra.glij.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Reads a project's explicit GLiJ Maven configuration for Eclipse.
 * Maven itself uses injected Mojo parameters, not this raw-POM reader.
 * Only project-local properties and basedir expressions are supported here;
 * configurations inherited from an external parent/effective POM require m2e.
 */
public final class MavenSourceLayout {
    private MavenSourceLayout() { }

    public static SourceLayout read(Path projectRoot) throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();
        Path pom = root.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) return SourceLayout.defaults(root);
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Element project = factory.newDocumentBuilder().parse(pom.toFile()).getDocumentElement();
            Map<String, String> properties = new HashMap<>();
            properties.put("project.basedir", root.toString());
            properties.put("basedir", root.toString());
            properties.put("project.build.sourceDirectory", root.resolve(GLiJCompiler.DEFAULT_SOURCE_DIRECTORY).toString());
            Element props = child(project, "properties");
            if (props != null) for (Element property : children(props)) properties.put(property.getTagName(), property.getTextContent().trim());
            Element build = child(project, "build");
            Element config = findConfiguration(build == null ? null : child(build, "plugins"));
            if (config == null && build != null) {
                Element management = child(build, "pluginManagement");
                config = findConfiguration(management == null ? null : child(management, "plugins"));
            }
            if (config == null) return SourceLayout.defaults(root);
            String globalExt = value(config, "fileExtensions", GLiJCompiler.DEFAULT_EXTENSION, properties);
            Path resources = path(root, value(config, "generatedResourcesDirectory",
                    GLiJCompiler.DEFAULT_RESOURCE_OUTPUT_DIRECTORY, properties));
            Element dirs = child(config, "directories");
            List<SourceDirectory> mappings = new ArrayList<>();
            if (dirs != null) {
                for (Element entry : children(dirs, "directory")) {
                    Path source = path(root, value(entry, "source", GLiJCompiler.DEFAULT_SOURCE_DIRECTORY, properties));
                    Path output = path(root, value(entry, "output", GLiJCompiler.DEFAULT_JAVA_OUTPUT_DIRECTORY, properties));
                    String ext = value(entry, "fileExtensions", globalExt, properties);
                    mappings.add(new SourceDirectory(source, output, GLiJCompiler.normalizeExtensions(ext)));
                }
            }
            if (mappings.isEmpty()) {
                // Migration from the old configuration supported for existing projects.
                mappings.add(new SourceDirectory(
                        path(root, value(config, "sourceDirectory", GLiJCompiler.DEFAULT_SOURCE_DIRECTORY, properties)),
                        path(root, value(config, "generatedJavaDirectory", GLiJCompiler.DEFAULT_JAVA_OUTPUT_DIRECTORY, properties)),
                        GLiJCompiler.normalizeExtensions(globalExt)));
            }
            return new SourceLayout(mappings, resources);
        } catch (Exception failure) {
            if (failure instanceof IOException io) throw io;
            throw new IOException("Cannot read GLiJ configuration from " + pom + ": " + failure.getMessage(), failure);
        }
    }

    private static Path path(Path root, String raw) {
        Path path = Path.of(raw);
        return (path.isAbsolute() ? path : root.resolve(path)).normalize();
    }

    private static String value(Element parent, String name, String fallback, Map<String, String> properties) {
        Element element = child(parent, name);
        String result = element == null ? fallback : element.getTextContent().trim();
        if (result.isEmpty()) return fallback;
        for (int n = 0; n < 12; n++) {
            var matcher = java.util.regex.Pattern.compile("\\$\\{([^}]+)}").matcher(result);
            if (!matcher.find()) return result;
            StringBuilder replaced = new StringBuilder();
            do {
                String variable = properties.get(matcher.group(1));
                if (variable == null) throw new IllegalArgumentException("Unresolved Maven property ${" + matcher.group(1) + "}");
                matcher.appendReplacement(replaced, java.util.regex.Matcher.quoteReplacement(variable));
            } while (matcher.find());
            matcher.appendTail(replaced);
            result = replaced.toString();
        }
        throw new IllegalArgumentException("Cyclic Maven property in: " + result);
    }

    private static Element findConfiguration(Element plugins) {
        if (plugins == null) return null;
        for (Element plugin : children(plugins, "plugin")) {
            if ("glij-maven-plugin".equals(text(plugin, "artifactId"))) {
                Element direct = child(plugin, "configuration");
                if (direct != null) return direct;
                // Maven also permits per-execution configuration.
                Element executions = child(plugin, "executions");
                for (Element execution : children(executions, "execution")) {
                    Element configured = child(execution, "configuration");
                    if (configured != null) return configured;
                }
            }
        }
        return null;
    }

    private static String text(Element parent, String name) {
        Element element = child(parent, name);
        return element == null ? "" : element.getTextContent().trim();
    }

    private static Element child(Element parent, String name) {
        if (parent == null) return null;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getTagName())) return element;
        }
        return null;
    }

    private static List<Element> children(Element parent) {
        List<Element> result = new ArrayList<>();
        if (parent != null) for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) result.add(element);
        }
        return result;
    }

    private static List<Element> children(Element parent, String name) {
        return children(parent).stream().filter(element -> name.equals(element.getTagName())).toList();
    }
}
