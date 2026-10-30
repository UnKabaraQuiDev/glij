package lu.kbra.glij.eclipse.editor;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

import lu.kbra.glij.eclipse.GLiJPlugin;

/** Palette for GLSL only. Ordinary Java uses JDT's Java editor preferences. */
public final class GLiJColors {
    public static final String STYLE = "palette";
    public static final String FOLLOW_JAVA = "java";
    public static final String DARK = "dark";
    public static final String LIGHT = "light";
    public static final String CUSTOM = "custom";
    public static final String[] CATEGORIES = { "default", "keyword", "type", "builtin", "uniform", "variable",
            "function", "systemConstant", "comment", "string", "number", "preprocessor" };
    private static final Map<String, String> DARK_RGB = Map.ofEntries(
            Map.entry("default", "220,220,220"), Map.entry("keyword", "86,156,214"),
            Map.entry("type", "78,201,176"), Map.entry("builtin", "220,220,170"),
            Map.entry("uniform", "156,220,254"), Map.entry("variable", "220,220,220"),
            Map.entry("function", "220,220,170"), Map.entry("systemConstant", "235,185,110"), Map.entry("comment", "106,153,85"),
            Map.entry("string", "206,145,120"), Map.entry("number", "181,206,168"),
            Map.entry("preprocessor", "197,134,192"));
    private static final Map<String, String> LIGHT_RGB = Map.ofEntries(
            Map.entry("default", "25,25,25"), Map.entry("keyword", "127,0,85"),
            Map.entry("type", "42,0,255"), Map.entry("builtin", "98,69,0"),
            Map.entry("uniform", "0,96,128"), Map.entry("variable", "25,25,25"),
            Map.entry("function", "98,69,0"), Map.entry("systemConstant", "122,61,0"), Map.entry("comment", "63,127,95"),
            Map.entry("string", "42,0,255"), Map.entry("number", "100,0,125"),
            Map.entry("preprocessor", "125,0,80"));
    // Java color configuration is the fallback for GLSL when 'Follow Java' is selected.
    private static final Map<String, String> JAVA_KEYS = Map.ofEntries(
            Map.entry("default", "java_default"), Map.entry("keyword", "java_keyword"),
            Map.entry("type", "java_keyword"), Map.entry("builtin", "java_static_field"),
            Map.entry("uniform", "java_field"), Map.entry("variable", "java_local_variable"),
            Map.entry("function", "java_method"), Map.entry("systemConstant", "java_static_field"), Map.entry("comment", "java_single_line_comment"),
            Map.entry("string", "java_string"), Map.entry("number", "java_default"),
            Map.entry("preprocessor", "java_annotation"));
    private final Map<String, Color> cache = new HashMap<>();

    public static String colorKey(final String category) { return "color." + category; }
    public static String defaultRgb(final String category) { return DARK_RGB.get(category); }

    public TextAttribute text(final String category) {
        final Color color = this.cache.computeIfAbsent(category, key -> new Color(Display.getCurrent(), pick(key)));
        final int style = "keyword".equals(category) || "preprocessor".equals(category) ? SWT.BOLD : SWT.NORMAL;
        return new TextAttribute(color, null, style);
    }

    private static RGB pick(final String category) {
        final IPreferenceStore prefs = GLiJPlugin.getDefault().getPreferenceStore();
        final String style = prefs.getString(STYLE);
        final String value = switch (style) {
        case LIGHT -> LIGHT_RGB.get(category);
        case DARK -> DARK_RGB.get(category);
        case CUSTOM -> prefs.getString(colorKey(category));
        default -> {
            // JDT preferences use RGB triplets in the Java UI preference store.
            // Do not silently switch to the light palette for a dark editor theme.
            final String key = JAVA_KEYS.get(category);
            final String selected = InstanceScope.INSTANCE.getNode("org.eclipse.jdt.ui").get(key, "");
            yield selected.isBlank() ? prefs.getString(colorKey(category)) : selected;
        }
        };
        try {
            final String[] parts = value.split(",");
            return new RGB(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
        } catch (final RuntimeException invalid) {
            return new RGB(25, 25, 25);
        }
    }

    /** Java semantic color fallback for .glij files not recognized as JDT compilation units. */
    public TextAttribute javaSemantic(String category) {
        String key = switch (category) {
            case "annotation" -> "semanticHighlighting.annotation.color";
            case "class" -> "semanticHighlighting.class.color";
            case "method" -> "semanticHighlighting.method.color";
            default -> "java_default";
        };
        String rgb = InstanceScope.INSTANCE.getNode("org.eclipse.jdt.ui").get(key, "");
        if (rgb.isBlank()) rgb = InstanceScope.INSTANCE.getNode("org.eclipse.jdt.ui").get("java_default", "0,0,0");
        final String selected = rgb;
        final Color color = cache.computeIfAbsent("javaSemantic." + category + "." + selected, ignored -> {
            try {
                String[] parts = selected.split(",");
                return new Color(Display.getCurrent(), new RGB(Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim())));
            } catch (RuntimeException invalid) { return new Color(Display.getCurrent(), new RGB(0, 0, 0)); }
        });
        return new TextAttribute(color);
    }

    public void reset() {
        this.cache.values().forEach(Color::dispose);
        this.cache.clear();
    }

    public void dispose() { this.reset(); }
}
