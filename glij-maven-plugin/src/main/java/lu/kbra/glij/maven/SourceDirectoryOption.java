package lu.kbra.glij.maven;

import java.io.File;

/**
 * One configured GLiJ source folder and its generated Java destination.
 * <p>Used as a nested Maven {@code <directories><directory>...</directory></directories>} bean.
 * The extension list inherits the goal's {@code fileExtensions} when unset.
 */
public final class SourceDirectoryOption {
    /** Source directory, for example ${project.basedir}/src/main/java. */
    private File source;
    /** Generated Java output directory, for example ${project.basedir}/src/gen/java. */
    private File output;
    /** Optional comma-separated extensions, overriding the goal-level list. */
    private String fileExtensions;

    public File getSource() { return source; }
    public void setSource(File source) { this.source = source; }
    public File getOutput() { return output; }
    public void setOutput(File output) { this.output = output; }
    public String getFileExtensions() { return fileExtensions; }
    public void setFileExtensions(String fileExtensions) { this.fileExtensions = fileExtensions; }
}
