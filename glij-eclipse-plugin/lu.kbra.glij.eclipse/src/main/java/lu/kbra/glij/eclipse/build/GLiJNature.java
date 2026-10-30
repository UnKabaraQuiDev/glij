package lu.kbra.glij.eclipse.build;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;

import lu.kbra.glij.eclipse.GLiJPlugin;

/** Places the GLiJ generator before the Java builder in Eclipse's build order. */
public final class GLiJNature implements IProjectNature {
    private IProject project;

    @Override
    public void configure() throws CoreException {
        IProjectDescription description = project.getDescription();
        for (ICommand command : description.getBuildSpec()) {
            if (GLiJPlugin.BUILDER.equals(command.getBuilderName())) return;
        }
        ICommand generator = description.newCommand();
        generator.setBuilderName(GLiJPlugin.BUILDER);
        List<ICommand> commands = new ArrayList<>();
        commands.add(generator);
        commands.addAll(List.of(description.getBuildSpec()));
        description.setBuildSpec(commands.toArray(ICommand[]::new));
        project.setDescription(description, null);
    }

    @Override
    public void deconfigure() throws CoreException {
        IProjectDescription description = project.getDescription();
        description.setBuildSpec(java.util.Arrays.stream(description.getBuildSpec())
                .filter(command -> !GLiJPlugin.BUILDER.equals(command.getBuilderName()))
                .toArray(ICommand[]::new));
        project.setDescription(description, null);
    }

    @Override
    public IProject getProject() { return project; }
    @Override
    public void setProject(IProject project) { this.project = project; }
}
