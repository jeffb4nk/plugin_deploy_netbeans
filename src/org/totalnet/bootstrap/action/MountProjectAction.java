package org.totalnet.bootstrap.action;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import org.totalnet.bootstrap.ui.MountProjectDialog;

/**
 * Acción de menú: Tools > TotalNet > Montar proyecto.
 */
public final class MountProjectAction extends AbstractAction {

    public MountProjectAction() {
        super("Montar proyecto TotalNet...");
        putValue(SHORT_DESCRIPTION, "Clona una rama, configura el entorno y compila TotalNet.");
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        SwingUtilities.invokeLater(MountProjectDialog::showDialog);
    }
}
