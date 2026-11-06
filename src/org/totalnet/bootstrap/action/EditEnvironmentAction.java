package org.totalnet.bootstrap.action;

import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import org.totalnet.bootstrap.model.EnvProfile;
import org.totalnet.bootstrap.ui.ProfileManagerDialog;

/**
 * Acción de menú: Tools > TotalNet > Gestionar perfiles de entorno.
 */
public final class EditEnvironmentAction extends AbstractAction {

    public EditEnvironmentAction() {
        super("Gestionar perfiles y aplicar TotalNet");
        putValue(SHORT_DESCRIPTION,
                "Crea o edita perfiles de Tomcat/BD y los aplica al proyecto seleccionado.");
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        SwingUtilities.invokeLater(() -> {
            File projectDir = ProjectSupport.currentProjectDir();
            String selectProfile = null;
            if (projectDir != null) {
                EnvProfile profile = EnvProfile.load(
                        new File(projectDir, "nbproject/private/totalnet-profile.properties"));
                selectProfile = profile.name;
            }
            new ProfileManagerDialog(null, selectProfile).setVisible(true);
        });
    }
}
