package org.totalnet.bootstrap.action;

import java.io.File;
import javax.swing.JOptionPane;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.filesystems.FileUtil;

/**
 * Utilidades para obtener el proyecto actualmente abierto en NetBeans.
 */
public final class ProjectSupport {

    private ProjectSupport() {
    }

    public static File currentProjectDir() {
        Project[] projects = OpenProjects.getDefault().getOpenProjects();
        if (projects.length == 1) {
            return FileUtil.toFile(projects[0].getProjectDirectory());
        }
        if (projects.length > 1) {
            Object selected = JOptionPane.showInputDialog(null,
                    "Selecciona el proyecto:", "Proyectos abiertos",
                    JOptionPane.QUESTION_MESSAGE, null, projects, projects[0]);
            if (selected instanceof Project) {
                return FileUtil.toFile(((Project) selected).getProjectDirectory());
            }
        }
        return null;
    }
}
