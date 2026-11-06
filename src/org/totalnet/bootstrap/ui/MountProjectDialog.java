package org.totalnet.bootstrap.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.totalnet.bootstrap.model.EnvProfile;
import org.totalnet.bootstrap.service.AntRunner;
import org.totalnet.bootstrap.service.EnvironmentConfigurator;
import org.totalnet.bootstrap.service.GitService;
import org.totalnet.bootstrap.service.ProfileStore;
import org.totalnet.bootstrap.service.ProjectBootstrap;

/**
 * Asistente para clonar/montar el proyecto TotalNet y dejarlo compilado.
 */
public final class MountProjectDialog extends JDialog {

    private static final String PREF_NODE = "org/totalnet/bootstrap";

    private final JTextField repoField = new JTextField(42);
    private final PlaceholderTextField destField = new PlaceholderTextField(42);
    private final PlaceholderTextField projectNameField = new PlaceholderTextField(30);
    private final JLabel finalPathLabel = new JLabel();
    private final JRadioButton masterOnlyRadio = new JRadioButton("Solo master (sin rama nueva)");
    private final JRadioButton newBranchRadio = new JRadioButton("Rama nueva desde master");
    private final JRadioButton existingBranchRadio = new JRadioButton("Rama existente del repositorio", true);
    private final PlaceholderTextField newBranchField = new PlaceholderTextField(24);
    private final JComboBox<String> existingBranchCombo = new JComboBox<>();
    private final JButton loadBranchesButton = new JButton("Cargar ramas");
    private final JComboBox<String> profileCombo = new JComboBox<>();
    private final JButton manageProfilesButton = new JButton("Gestionar perfiles...");
    private final JButton refreshProfilesButton = new JButton("Refrescar");
    private final JLabel envHint = new JLabel(" ");

    private final JTextArea logArea = new JTextArea(10, 80);
    private final JButton mountButton = new JButton("Montar y compilar");
    private final JButton cancelButton = new JButton("Cancelar");
    private String lastLoadedUrl = "";

    private MountProjectDialog(java.awt.Frame owner) {
        super(owner, "TotalNet - Montar proyecto", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUi();
        loadPreferences();
        pack();
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(() -> loadBranches(false));
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent event) {
                SwingUtilities.invokeLater(() -> promptForProfileIfNeeded());
            }
        });
    }

    public static void showDialog() {
        MountProjectDialog dialog = new MountProjectDialog(null);
        dialog.setVisible(true);
    }

    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        row = addRow(form, gbc, row, "Repositorio Git:", repoField, null);
        row = addRow(form, gbc, row, "Carpeta contenedora:", destField, browseButton(destField, true));
        row = addRow(form, gbc, row, "Nombre del proyecto:", projectNameField, null);
        row = addRow(form, gbc, row, "Se creará en:", finalPathLabel, null);

        JPanel branchMode = new JPanel();
        ButtonGroup group = new ButtonGroup();
        group.add(existingBranchRadio);
        group.add(masterOnlyRadio);
        group.add(newBranchRadio);
        branchMode.add(existingBranchRadio);
        branchMode.add(masterOnlyRadio);
        branchMode.add(newBranchRadio);
        row = addRow(form, gbc, row, "Rama:", branchMode, null);

        row = addRow(form, gbc, row, "Nombre rama nueva:", newBranchField, null);

        JPanel branchPanel = new JPanel(new BorderLayout(4, 0));
        branchPanel.add(existingBranchCombo, BorderLayout.CENTER);
        branchPanel.add(loadBranchesButton, BorderLayout.EAST);
        row = addRow(form, gbc, row, "Rama existente:", branchPanel, null);

        JPanel profilePanel = new JPanel(new BorderLayout(4, 0));
        profilePanel.add(profileCombo, BorderLayout.CENTER);
        JPanel profileButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        profileButtons.add(refreshProfilesButton);
        profileButtons.add(manageProfilesButton);
        profilePanel.add(profileButtons, BorderLayout.EAST);
        row = addRow(form, gbc, row, "Perfil de entorno:", profilePanel, null);
        row = addRow(form, gbc, row, "", envHint, null);

        initPlaceholders();

        profileCombo.addActionListener(e -> onProfileSelected());
        manageProfilesButton.addActionListener(e -> openProfileManager());
        refreshProfilesButton.addActionListener(e -> refreshProfiles());

        masterOnlyRadio.addActionListener(e -> updateBranchState());
        existingBranchRadio.addActionListener(e -> {
            updateBranchState();
            if (existingBranchCombo.getItemCount() <= 1) {
                loadBranches(false);
            }
        });
        newBranchRadio.addActionListener(e -> updateBranchState());
        existingBranchCombo.addItem("master");
        loadBranchesButton.addActionListener(e -> loadBranches(true));
        repoField.addActionListener(e -> loadBranches(false));
        repoField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent event) {
                loadBranches(false);
            }
        });
        javax.swing.event.DocumentListener pathListener = new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent event) {
                updateFinalPath();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent event) {
                updateFinalPath();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent event) {
                updateFinalPath();
            }
        };
        destField.getDocument().addDocumentListener(pathListener);
        projectNameField.getDocument().addDocumentListener(pathListener);
        mountButton.addActionListener(e -> startMount());
        cancelButton.addActionListener(e -> dispose());

        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Salida"));

        JPanel buttons = new JPanel();
        buttons.add(mountButton);
        buttons.add(cancelButton);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        content.add(form, BorderLayout.NORTH);
        content.add(logScroll, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        updateBranchState();
    }

    private void initPlaceholders() {
        destField.setPlaceholder("~/NetBeansProjects");
        projectNameField.setPlaceholder("ej. MiProyecto");
        newBranchField.setPlaceholder("feature/mi-cambio");

        destField.setToolTipText("Carpeta contenedora. Dentro se creará la carpeta con el nombre del proyecto.");
        projectNameField.setToolTipText("Nombre del proyecto, del WAR y de la carpeta que se creará dentro del destino.");
        newBranchField.setToolTipText("Nombre de la rama nueva (se crea desde master).");
    }

    private void updateFinalPath() {
        String base = destField.getText().trim();
        String name = projectNameField.getText().trim();
        if (base.isEmpty()) {
            finalPathLabel.setText("(elige una carpeta contenedora)");
        } else if (name.isEmpty()) {
            finalPathLabel.setText(path(base).getAbsolutePath());
        } else {
            finalPathLabel.setText(resolveDestination(path(base), name).getAbsolutePath());
        }
    }

    private static File resolveDestination(File base, String projectName) {
        if (projectName.equals(base.getName())) {
            return base;
        }
        return new File(base, projectName);
    }

    private static File path(String value) {
        String text = value == null ? "" : value.trim();
        String home = System.getProperty("user.home", "");
        if (text.equals("~")) {
            text = home;
        } else if (text.startsWith("~/") || text.startsWith("~\\")) {
            text = home + text.substring(1);
        }
        return new File(text);
    }

    private void refreshProfiles() {
        String selected = (String) profileCombo.getSelectedItem();
        profileCombo.removeAllItems();
        List<String> names = ProfileStore.list();
        for (String name : names) {
            profileCombo.addItem(name);
        }
        if (selected != null && names.contains(selected)) {
            profileCombo.setSelectedItem(selected);
        } else if (!names.isEmpty()) {
            profileCombo.setSelectedIndex(0);
        } else {
            envHint.setText("No hay perfiles. Crea uno con 'Gestionar perfiles...'.");
            envHint.setForeground(new java.awt.Color(180, 90, 0));
        }
    }

    private void promptForProfileIfNeeded() {
        if (profileCombo.getItemCount() > 0) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "No hay perfiles de entorno creados.\n\n"
                + "Antes de montar el proyecto debes crear un perfil (Tomcat + variables de BD).\n"
                + "¿Quieres crear uno ahora?",
                "Perfil de entorno requerido",
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            openProfileManager();
        }
    }

    private void openProfileManager() {
        new ProfileManagerDialog(null, (String) profileCombo.getSelectedItem()).setVisible(true);
        refreshProfiles();
    }

    private void onProfileSelected() {
        Object selected = profileCombo.getSelectedItem();
        if (selected == null) {
            envHint.setText("No hay perfiles. Crea uno con 'Gestionar perfiles...'.");
            envHint.setForeground(new java.awt.Color(180, 90, 0));
            return;
        }
        String name = selected.toString();
        EnvProfile profile = ProfileStore.load(name);
        boolean validTomcat = profile.tomcatHome != null
                && !profile.tomcatHome.trim().isEmpty()
                && new File(profile.tomcatHome.trim()).isDirectory();
        String tomcat = (profile.tomcatHome == null || profile.tomcatHome.trim().isEmpty())
                ? "(sin Tomcat)" : profile.tomcatHome.trim();
        String bd = (profile.dbIp == null ? "" : profile.dbIp)
                + (profile.dbName == null ? "" : profile.dbName);
        envHint.setText("Perfil: " + name
                + " | Tomcat: " + tomcat + (validTomcat ? "" : " (no encontrado)")
                + " | BD: " + (bd.isEmpty() ? "(sin configurar)" : bd)
                + " | Extras: " + profile.extraVariables.size());
        envHint.setForeground(validTomcat
                ? new java.awt.Color(0, 110, 0) : new java.awt.Color(180, 90, 0));
    }

    private int addRow(JPanel panel, GridBagConstraints gbc, int row,
            String label, java.awt.Component component, java.awt.Component extra) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(component, gbc);

        if (extra != null) {
            gbc.gridx = 2;
            gbc.weightx = 0;
            panel.add(extra, gbc);
        }
        return row + 1;
    }

    private JButton browseButton(JTextField target, boolean directory) {
        JButton button = new JButton("...");
        button.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(directory
                    ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.DIRECTORIES_ONLY);
            if (!target.getText().trim().isEmpty()) {
                chooser.setSelectedFile(new File(target.getText().trim()));
            }
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                target.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        return button;
    }

    private void updateBranchState() {
        boolean isNew = newBranchRadio.isSelected();
        boolean isExisting = existingBranchRadio.isSelected();
        newBranchField.setEnabled(isNew);
        existingBranchCombo.setEnabled(isExisting);
        loadBranchesButton.setEnabled(isExisting);
    }

    private void loadBranches(boolean showErrors) {
        final String url = repoField.getText().trim();
        if (url.isEmpty()) {
            if (showErrors) {
                JOptionPane.showMessageDialog(this, "Indica la URL del repositorio.");
            }
            return;
        }
        if (url.equals(lastLoadedUrl) && existingBranchCombo.getItemCount() > 1) {
            return;
        }
        lastLoadedUrl = url;
        loadBranchesButton.setEnabled(false);
        logArea.append("Consultando ramas reales en el repositorio...\n");
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return GitService.listRemoteBranches(url);
            }

            @Override
            protected void done() {
                try {
                    List<String> branches = get();
                    Object previous = existingBranchCombo.getSelectedItem();
                    existingBranchCombo.removeAllItems();
                    for (String branch : branches) {
                        existingBranchCombo.addItem(branch);
                    }
                    if (branches.isEmpty()) {
                        existingBranchCombo.addItem("master");
                    } else if (previous != null && branches.contains(previous.toString())) {
                        existingBranchCombo.setSelectedItem(previous);
                    } else {
                        existingBranchCombo.setSelectedItem(branches.contains("master") ? "master" : branches.get(0));
                    }
                    logArea.append("Ramas encontradas: " + branches.size() + "\n");
                } catch (InterruptedException | ExecutionException ex) {
                    lastLoadedUrl = "";
                    if (showErrors) {
                        showError("No se pudieron consultar las ramas", ex);
                    } else {
                        logArea.append("No se pudieron cargar las ramas automáticamente.\n");
                    }
                } finally {
                    loadBranchesButton.setEnabled(!newBranchRadio.isSelected());
                }
            }
        }.execute();
    }

    private void startMount() {
        final String url = repoField.getText().trim();
        final String destText = destField.getText().trim();
        final boolean masterOnly = masterOnlyRadio.isSelected();
        final boolean createNew = newBranchRadio.isSelected();
        final String branch = createNew
                ? newBranchField.getText().trim() : (String) existingBranchCombo.getSelectedItem();

        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Indica la URL del repositorio.");
            return;
        }
        if (destText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Indica la carpeta contenedora.");
            return;
        }
        final String projectName = projectNameField.getText().trim();
        if (projectName.isEmpty() || !projectName.matches("[A-Za-z0-9._-]+")) {
            JOptionPane.showMessageDialog(this,
                    "Nombre de proyecto inválido. Usa letras, números, punto, guion o guion bajo.");
            return;
        }
        if (!masterOnly && (branch == null || branch.isEmpty())) {
            JOptionPane.showMessageDialog(this, "Indica o selecciona una rama.");
            return;
        }
        Object selectedProfile = profileCombo.getSelectedItem();
        if (selectedProfile == null || selectedProfile.toString().trim().isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "No hay perfiles de entorno.\n"
                    + "Debes crear un perfil (Tomcat + variables de BD) antes de montar el proyecto.\n\n"
                    + "¿Crear un perfil ahora?",
                    "Perfil de entorno requerido",
                    JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                openProfileManager();
            }
            return;
        }
        final String profileName = selectedProfile.toString();
        final EnvProfile profile = ProfileStore.load(profileName);
        profile.name = profileName;
        if (profile.tomcatHome == null || profile.tomcatHome.trim().isEmpty()
                || !new File(profile.tomcatHome.trim()).isDirectory()) {
            JOptionPane.showMessageDialog(this,
                    "El perfil '" + profileName + "' no tiene una ruta de Tomcat válida.\n"
                    + "Edítalo en 'Gestionar perfiles...'.");
            return;
        }

        mountButton.setEnabled(false);
        cancelButton.setEnabled(false);
        logArea.setText("");

        new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                File base = path(destText);
                File dest = resolveDestination(base, projectName);
                if (dest.getParentFile() != null) {
                    dest.getParentFile().mkdirs();
                }
                boolean existingRepo = dest.isDirectory() && new File(dest, ".git").isDirectory();

                if (dest.exists() && dest.isDirectory()) {
                    File[] children = dest.listFiles();
                    if (!existingRepo && children != null && children.length > 0) {
                        throw new IllegalStateException("La carpeta destino no está vacía y no es un repositorio Git.");
                    }
                }

                if (!existingRepo) {
                    publish("Clonando repositorio desde " + url);
                    GitService.cloneRepository(url, dest);
                } else {
                    publish("Actualizando repositorio existente...");
                    GitService.fetch(dest);
                }

                if (masterOnly) {
                    publish("Actualizando master sin crear rama...");
                    GitService.checkoutMaster(dest);
                } else if (createNew) {
                    publish("Creando rama " + branch + " desde master...");
                    GitService.createBranch(dest, branch);
                } else {
                    publish("Cambiando a la rama " + branch + "...");
                    GitService.checkoutExisting(dest, branch);
                }

                publish("Aplicando configuración NetBeans...");
                ProjectBootstrap.bootstrap(dest, projectName);

                publish("Escribiendo entorno y setenv de Tomcat...");
                EnvironmentConfigurator.apply(dest, profile);

                publish("Compilando proyecto...");
                int exit = AntRunner.build(dest, line -> publish(line));

                publish("Abriendo proyecto en NetBeans...");
                openProject(dest);

                return exit;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    logArea.append(line + "\n");
                }
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                try {
                    int exit = get();
                    savePreferences();
                    if (exit == 0) {
                        JOptionPane.showMessageDialog(MountProjectDialog.this,
                                "Proyecto montado y compilado correctamente.\n\n"
                                + "Recuerda reiniciar Tomcat si estaba en ejecución para aplicar las variables.");
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(MountProjectDialog.this,
                                "El proyecto se montó, pero el build falló (código " + exit + ").\n"
                                + "Revisa la salida para más detalles.",
                                "Build con errores", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    showError("No se pudo montar el proyecto", ex);
                } finally {
                    mountButton.setEnabled(true);
                    cancelButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void openProject(File dest) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                FileObject fileObject = FileUtil.toFileObject(dest);
                Project project = ProjectManager.getDefault().findProject(fileObject);
                if (project != null) {
                    OpenProjects.getDefault().open(new Project[]{project}, false);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private void showError(String message, Exception ex) {
        Throwable cause = ex;
        if (ex instanceof ExecutionException && ex.getCause() != null) {
            cause = ex.getCause();
        }
        logArea.append("\nERROR: " + cause.getMessage() + "\n");
        JOptionPane.showMessageDialog(this,
                message + ":\n\n" + cause.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void loadPreferences() {
        Preferences prefs = Preferences.userRoot().node(PREF_NODE);
        repoField.setText(prefs.get("repoUrl", "https://gitlab.com/maikolgu/totalnetdesarrollo.git"));
        destField.setText(prefs.get("destDir", ""));
        projectNameField.setText(prefs.get("projectName", "TotalNetEnlacesBancos"));
        newBranchField.setText(prefs.get("branchPrefix", ""));
        updateFinalPath();
        refreshProfiles();
        String savedProfile = prefs.get("profileName", "");
        if (!savedProfile.isEmpty() && ProfileStore.list().contains(savedProfile)) {
            profileCombo.setSelectedItem(savedProfile);
        }
        onProfileSelected();
    }

    private void savePreferences() {
        Preferences prefs = Preferences.userRoot().node(PREF_NODE);
        prefs.put("repoUrl", repoField.getText().trim());
        prefs.put("destDir", destField.getText().trim());
        prefs.put("projectName", projectNameField.getText().trim());
        prefs.put("branchPrefix", newBranchField.getText().trim());
        Object selectedProfile = profileCombo.getSelectedItem();
        prefs.put("profileName", selectedProfile == null ? "" : selectedProfile.toString());
        try {
            prefs.flush();
        } catch (Exception ignored) {
            // Preferencias no críticas.
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(Math.max(size.width, 700), Math.max(size.height, 520));
    }
}
