package lu.kbra.glij.eclipse.build;

import java.util.Arrays;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

import lu.kbra.glij.eclipse.GLiJPlugin;

/** Project context-menu action enabling the GLiJ builder nature. */
public final class EnableGLiJAction implements IObjectActionDelegate {
    private IProject project;

    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) { }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        project = selection instanceof IStructuredSelection s && s.getFirstElement() instanceof IProject p ? p : null;
        action.setEnabled(project != null && project.isAccessible());
    }

    @Override
    public void run(IAction action) {
        if (project == null) return;
        try {
            IProjectDescription description = project.getDescription();
            if (Arrays.asList(description.getNatureIds()).contains(GLiJPlugin.NATURE)) return;
            String[] current = description.getNatureIds();
            String[] newNatures = Arrays.copyOf(current, current.length + 1);
            newNatures[current.length] = GLiJPlugin.NATURE;
            description.setNatureIds(newNatures);
            project.setDescription(description, null);
        } catch (CoreException failure) {
            GLiJPlugin.getDefault().getLog().log(failure.getStatus());
        }
    }
}
