package org.totalnet.bootstrap.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.totalnet.bootstrap.action.ProjectSupport;
import org.totalnet.bootstrap.model.EnvProfile;
import org.totalnet.bootstrap.service.EnvironmentConfigurator;
import org.totalnet.bootstrap.service.ProfileStore;

/**
 * Ventana de gestión de perfiles de entorno (Tomcat + variables de BD).
 */
public final class ProfileManagerDialog extends JDialog {

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> profileList = new JList<>(listModel);

    private final PlaceholderTextField nameField = new PlaceholderTextField(28);
    private final PlaceholderTextField tomcatField = new PlaceholderTextField(38);
    private final PlaceholderTextField dbIpField = new PlaceholderTextField(26);
    private final PlaceholderTextField dbNameField = new PlaceholderTextField(26);
    private final PlaceholderTextField dbUserField = new PlaceholderTextField(26);
    private final PlaceholderPasswordField dbPasswordField = new PlaceholderPasswordField(26);
    private final PlaceholderTextField dbDriverField = new PlaceholderTextField(26);
    private final javax.swing.table.DefaultTableModel extraModel
            = new javax.swing.table.DefaultTableModel(new Object[]{"Variable", "Valor"}, 0);
    private final javax.swing.JTable extraTable = new javax.swing.JTable(extraModel);
    private final JButton addVariableButton = new JButton("Agregar variable");
    private final JButton removeVariableButton = new JButton("Quitar seleccionada");

    private final JCheckBox showPassword = new JCheckBox("Mostrar contraseña en la vista previa");
    private final JLabel tomcatStatus = new JLabel(" ");
    private final JLabel tomcatInfo = new JLabel();
    private final JTextArea preview = new JTextArea(14, 60);

    private final JButton saveButton = new JButton("Guardar perfil");
    private final JButton applyButton = new JButton("Aplicar al proyecto abierto");
    private final JButton newButton = new JButton("Nuevo");
    private final JButton duplicateButton = new JButton("Duplicar");
    private final JButton deleteButton = new JButton("Eliminar");
    private final JButton closeButton = new JButton("Cerrar");

    public ProfileManagerDialog(java.awt.Frame owner, String selectProfile) {
        super(owner, "Perfiles de entorno TotalNet", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUi();
        refreshList(selectProfile);
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        profileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelected();
            }
        });

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setBorder(BorderFactory.createTitledBorder("Perfiles"));
        JScrollPane listScroll = new JScrollPane(profileList);
        listScroll.setPreferredSize(new Dimension(190, 380));
        left.add(listScroll, BorderLayout.CENTER);
        JPanel leftButtons = new JPanel(new GridBagLayout());
        GridBagConstraints lbc = new GridBagConstraints();
        lbc.gridx = 0;
        lbc.gridy = 0;
        lbc.fill = GridBagConstraints.HORIZONTAL;
        lbc.insets = new Insets(2, 2, 2, 2);
        leftButtons.add(newButton, lbc);
        lbc.gridy = 1;
        leftButtons.add(duplicateButton, lbc);
        lbc.gridy = 2;
        leftButtons.add(deleteButton, lbc);
        left.add(leftButtons, BorderLayout.SOUTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Datos del perfil"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;
        row = addSection(form, gbc, row, "Básicas (obligatorias)");
        row = addRow(form, gbc, row, "Nombre del perfil:", nameField, null);
        row = addRow(form, gbc, row, "Tomcat (home):", tomcatField, tomcatBrowseButton());
        row = addRow(form, gbc, row, "", tomcatStatus, tomcatInfo);
        row = addRow(form, gbc, row, "DB IP / host:puerto:", dbIpField, null);
        row = addRow(form, gbc, row, "DB nombre:", dbNameField, null);
        row = addRow(form, gbc, row, "DB usuario:", dbUserField, null);
        row = addRow(form, gbc, row, "DB contraseña:", dbPasswordField, null);
        row = addRow(form, gbc, row, "DB driver:", dbDriverField, null);

        row = addSection(form, gbc, row, "Otras variables (opcional)");
        javax.swing.JScrollPane extraScroll = new javax.swing.JScrollPane(extraTable);
        extraScroll.setPreferredSize(new Dimension(650, 140));
        JPanel extraPanel = new JPanel(new BorderLayout(4, 0));
        extraPanel.add(extraScroll, BorderLayout.CENTER);
        JPanel extraButtons = new JPanel(new GridBagLayout());
        GridBagConstraints ebc = new GridBagConstraints();
        ebc.gridx = 0;
        ebc.gridy = 0;
        ebc.fill = GridBagConstraints.HORIZONTAL;
        ebc.insets = new Insets(2, 2, 2, 2);
        extraButtons.add(addVariableButton, ebc);
        ebc.gridy = 1;
        extraButtons.add(removeVariableButton, ebc);
        extraPanel.add(extraButtons, BorderLayout.EAST);
        row = addComponentRow(form, gbc, row, extraPanel);
        row = addNote(form, gbc, row,
                "Las básicas son necesarias para conectar. Agrega aquí cualquier otra variable "
                + "(por ejemplo TOTALNET_DB_URL, TOTALNET_COD_ENTORNO, TOTALNET_ARCHIVOS_PATH, "
                + "TOTALNET_DB_IP_CAL, TOTALNET_DB_NAME_CAL).");

        nameField.setPlaceholder("ej. Desarrollo local");
        tomcatField.setPlaceholder("/ruta/a/apache-tomcat-9.0");
        dbIpField.setPlaceholder("192.168.1.10:5432/");
        dbNameField.setPlaceholder("totalnet");
        dbUserField.setPlaceholder("postgres");
        dbPasswordField.setPlaceholder("contraseña de la BD");
        dbDriverField.setPlaceholder("org.postgresql.Driver");

        nameField.setToolTipText("Obligatorio. Nombre del perfil.");
        tomcatField.setToolTipText("Obligatorio. Carpeta de instalación de Tomcat.");
        dbIpField.setToolTipText("Básica. Formato host:puerto/ con / final.");
        dbNameField.setToolTipText("Básica. Nombre de la base de datos.");
        dbUserField.setToolTipText("Básica. Usuario de la base de datos.");
        dbPasswordField.setToolTipText("Básica. Si se deja vacía se conserva la contraseña actual del setenv.");
        dbDriverField.setToolTipText("Básica. Driver JDBC. PostgreSQL: org.postgresql.Driver");

        tomcatInfo.setIcon(UIManager.getIcon("OptionPane.informationIcon"));
        tomcatInfo.setToolTipText("¿No tienes Tomcat? Haz clic para ver qué hacer.");
        tomcatInfo.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        tomcatInfo.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                showTomcatHelp();
            }
        });

        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updatePreviewAndStatus();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updatePreviewAndStatus();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updatePreviewAndStatus();
            }
        };
        for (PlaceholderTextField field : new PlaceholderTextField[]{
            nameField, tomcatField, dbIpField, dbNameField, dbUserField, dbDriverField}) {
            field.getDocument().addDocumentListener(listener);
        }
        dbPasswordField.getDocument().addDocumentListener(listener);
        extraModel.addTableModelListener(e -> updatePreviewAndStatus());
        showPassword.addActionListener(e -> updatePreviewAndStatus());

        preview.setEditable(false);
        preview.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        JScrollPane previewScroll = new JScrollPane(preview);
        previewScroll.setBorder(BorderFactory.createTitledBorder(
                "Vista previa: variables que se escribirán en setenv"));
        JPanel previewPanel = new JPanel(new BorderLayout(4, 4));
        previewPanel.add(showPassword, BorderLayout.NORTH);
        previewPanel.add(previewScroll, BorderLayout.CENTER);

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setBorder(null);
        formScroll.setPreferredSize(new Dimension(700, 430));
        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(formScroll, BorderLayout.NORTH);
        right.add(previewPanel, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.0);

        saveButton.addActionListener(e -> saveCurrent());
        applyButton.addActionListener(e -> applyToProject());
        addVariableButton.addActionListener(e -> {
            extraModel.addRow(new Object[]{"", ""});
            int last = extraModel.getRowCount() - 1;
            extraTable.setRowSelectionInterval(last, last);
            extraTable.editCellAt(last, 0);
        });
        removeVariableButton.addActionListener(e -> {
            int selected = extraTable.getSelectedRow();
            if (selected >= 0) {
                extraModel.removeRow(selected);
            }
        });
        newButton.addActionListener(e -> newProfile());
        duplicateButton.addActionListener(e -> duplicateProfile());
        deleteButton.addActionListener(e -> deleteProfile());
        closeButton.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(applyButton);
        buttons.add(saveButton);
        buttons.add(closeButton);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        content.add(split, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        updatePreviewAndStatus();
    }

    private int addRow(JPanel panel, GridBagConstraints gbc, int row, String label,
            java.awt.Component component, java.awt.Component extra) {
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

    private int addSection(JPanel panel, GridBagConstraints gbc, int row, String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
        label.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
        return addFullRow(panel, gbc, row, label);
    }

    private int addNote(JPanel panel, GridBagConstraints gbc, int row, String text) {
        JLabel label = new JLabel("<html><i>" + text + "</i></html>");
        label.setForeground(new java.awt.Color(110, 110, 110));
        return addFullRow(panel, gbc, row, label);
    }

    private int addFullRow(JPanel panel, GridBagConstraints gbc, int row, JLabel label) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.weightx = 1;
        panel.add(label, gbc);
        gbc.gridwidth = 1;
        return row + 1;
    }

    private int addComponentRow(JPanel panel, GridBagConstraints gbc, int row,
            java.awt.Component component) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.weightx = 1;
        panel.add(component, gbc);
        gbc.gridwidth = 1;
        return row + 1;
    }

    private void applyToProject() {
        EnvProfile profile = readFields();
        if (profile.name.isEmpty() || !profile.name.matches("[A-Za-z0-9 ._-]+")) {
            JOptionPane.showMessageDialog(this,
                    "Indica un nombre de perfil válido.",
                    "Perfil inválido", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (profile.tomcatHome.isEmpty() || !new File(profile.tomcatHome).isDirectory()) {
            JOptionPane.showMessageDialog(this,
                    "Indica una ruta de Tomcat válida antes de aplicar el perfil.",
                    "Tomcat inválido", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!validateExtraTable()) {
            return;
        }
        File projectDir = ProjectSupport.currentProjectDir();
        if (projectDir == null) {
            JOptionPane.showMessageDialog(this,
                    "No hay un proyecto abierto en NetBeans.\n"
                    + "Abre el proyecto en NetBeans o usa 'Montar proyecto TotalNet...'.",
                    "Sin proyecto abierto", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!new File(projectDir, "nbproject").isDirectory()) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "La carpeta no parece un proyecto NetBeans (no tiene nbproject).\n¿Aplicar de todos modos?",
                    "Aviso", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            ProfileStore.save(profile);
            EnvironmentConfigurator.apply(projectDir, profile);
            refreshList(profile.name);
            JOptionPane.showMessageDialog(this,
                    "Perfil '" + profile.name + "' aplicado al proyecto:\n"
                    + projectDir.getAbsolutePath()
                    + "\n\nSi Tomcat estaba en ejecución, reinícialo para que tome las variables.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo aplicar el perfil:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JButton tomcatBrowseButton() {
        JButton button = new JButton("...");
        button.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (!tomcatField.getText().trim().isEmpty()) {
                chooser.setSelectedFile(new File(tomcatField.getText().trim()));
            }
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                tomcatField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        return button;
    }

    private void refreshList(String select) {
        String previous = select;
        if (previous == null || previous.trim().isEmpty()) {
            previous = profileList.getSelectedValue();
        }
        listModel.clear();
        for (String name : ProfileStore.list()) {
            listModel.addElement(name);
        }
        if (previous != null && listModel.contains(previous)) {
            profileList.setSelectedValue(previous, true);
        } else if (!listModel.isEmpty()) {
            profileList.setSelectedIndex(0);
        } else {
            clearFields();
        }
    }

    private void loadSelected() {
        String name = profileList.getSelectedValue();
        if (name == null) {
            return;
        }
        fillFields(ProfileStore.load(name));
    }

    private void fillFields(EnvProfile profile) {
        nameField.setText(profile.name == null ? "" : profile.name);
        tomcatField.setText(profile.tomcatHome);
        dbIpField.setText(profile.dbIp);
        dbNameField.setText(profile.dbName);
        dbUserField.setText(profile.dbUser);
        dbPasswordField.setText(profile.dbPassword);
        dbDriverField.setText(profile.dbDriver);
        fillExtraTable(profile);
        updatePreviewAndStatus();
    }

    private void clearFields() {
        nameField.setText("");
        tomcatField.setText("");
        dbIpField.setText("");
        dbNameField.setText("");
        dbUserField.setText("");
        dbPasswordField.setText("");
        dbDriverField.setText("org.postgresql.Driver");
        extraModel.setRowCount(0);
    }

    private void fillExtraTable(EnvProfile profile) {
        extraModel.setRowCount(0);
        for (java.util.Map.Entry<String, String> entry : profile.extraVariables.entrySet()) {
            extraModel.addRow(new Object[]{entry.getKey(), entry.getValue()});
        }
    }

    private EnvProfile readFields() {
        EnvProfile profile = new EnvProfile();
        profile.name = nameField.getText().trim();
        profile.tomcatHome = tomcatField.getText().trim();
        profile.dbIp = dbIpField.getText().trim();
        profile.dbName = dbNameField.getText().trim();
        profile.dbUser = dbUserField.getText().trim();
        profile.dbPassword = new String(dbPasswordField.getPassword());
        profile.dbDriver = dbDriverField.getText().trim();
        for (int i = 0; i < extraModel.getRowCount(); i++) {
            Object nameObj = extraModel.getValueAt(i, 0);
            String name = nameObj == null ? "" : nameObj.toString().trim();
            if (name.isEmpty()) {
                continue;
            }
            Object valueObj = extraModel.getValueAt(i, 1);
            String value = valueObj == null ? "" : valueObj.toString().trim();
            profile.extraVariables.put(name, value);
        }
        return profile;
    }

    private boolean validateExtraTable() {
        java.util.Set<String> basic = new java.util.HashSet<>(java.util.Arrays.asList(
                "TOTALNET_DB_IP", "TOTALNET_DB_NAME", "TOTALNET_DB_USER",
                "TOTALNET_DB_PASSWORD", "TOTALNET_DB_DRIVER"));
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < extraModel.getRowCount(); i++) {
            Object nameObj = extraModel.getValueAt(i, 0);
            String name = nameObj == null ? "" : nameObj.toString().trim();
            if (name.isEmpty()) {
                continue;
            }
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                JOptionPane.showMessageDialog(this,
                        "Nombre de variable inválido: " + name
                        + "\nUsa letras, números y guion bajo; debe iniciar con letra o _.",
                        "Variable inválida", JOptionPane.WARNING_MESSAGE);
                return false;
            }
            if (basic.contains(name)) {
                JOptionPane.showMessageDialog(this,
                        "La variable " + name + " es básica y ya se configura arriba.",
                        "Variable duplicada", JOptionPane.WARNING_MESSAGE);
                return false;
            }
            if (!seen.add(name)) {
                JOptionPane.showMessageDialog(this,
                        "La variable " + name + " está repetida en la tabla.",
                        "Variable duplicada", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }
        return true;
    }

    private void newProfile() {
        profileList.clearSelection();
        clearFields();
        nameField.setText("Nuevo perfil");
        nameField.requestFocusInWindow();
        updatePreviewAndStatus();
    }

    private void duplicateProfile() {
        EnvProfile profile = readFields();
        String base = profile.name == null || profile.name.trim().isEmpty() ? "perfil" : profile.name.trim();
        nameField.setText(base + " copia");
        profileList.clearSelection();
        updatePreviewAndStatus();
    }

    private void deleteProfile() {
        String name = profileList.getSelectedValue();
        if (name == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Eliminar el perfil '" + name + "'?", "Eliminar perfil",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            ProfileStore.delete(name);
            refreshList(null);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo eliminar: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveCurrent() {
        EnvProfile profile = readFields();
        if (profile.name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Indica el nombre del perfil.",
                    "Falta el nombre", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!profile.name.matches("[A-Za-z0-9 ._-]+")) {
            JOptionPane.showMessageDialog(this,
                    "El nombre del perfil solo puede tener letras, números, espacios, punto, guion y guion bajo.",
                    "Nombre inválido", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!validateExtraTable()) {
            return;
        }
        try {
            ProfileStore.save(profile);
            refreshList(profile.name);
            JOptionPane.showMessageDialog(this, "Perfil guardado: " + profile.name);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updatePreviewAndStatus() {
        String tomcat = tomcatField.getText().trim();
        if (tomcat.isEmpty()) {
            tomcatStatus.setText("Tomcat no configurado.");
            tomcatStatus.setForeground(new java.awt.Color(180, 90, 0));
        } else if (!new File(tomcat).isDirectory()) {
            tomcatStatus.setText("Tomcat no encontrado en esa ruta.");
            tomcatStatus.setForeground(java.awt.Color.RED);
        } else {
            tomcatStatus.setText("Tomcat OK.");
            tomcatStatus.setForeground(new java.awt.Color(0, 120, 0));
        }

        String setenv;
        if (tomcat.isEmpty()) {
            setenv = "<indica la ruta de Tomcat>/bin/" + (isWindows() ? "setenv.bat" : "setenv.sh");
        } else {
            setenv = new File(new File(tomcat, "bin"),
                    isWindows() ? "setenv.bat" : "setenv.sh").getAbsolutePath();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Perfil   : ").append(nameField.getText().trim()).append('\n');
        sb.append("Tomcat   : ").append(tomcat.isEmpty() ? "(vacío)" : tomcat).append('\n');
        sb.append("Archivo  : ").append(setenv).append("\n\n");
        append(sb, "TOTALNET_DB_IP", dbIpField.getText().trim());
        append(sb, "TOTALNET_DB_NAME", dbNameField.getText().trim());
        append(sb, "TOTALNET_DB_USER", dbUserField.getText().trim());
        String password = new String(dbPasswordField.getPassword());
        if (password.isEmpty()) {
            sb.append("TOTALNET_DB_PASSWORD = (se conserva la actual)\n");
        } else if (showPassword.isSelected()) {
            append(sb, "TOTALNET_DB_PASSWORD", password);
        } else {
            sb.append("TOTALNET_DB_PASSWORD = ********\n");
        }
        append(sb, "TOTALNET_DB_DRIVER", dbDriverField.getText().trim());
        for (int i = 0; i < extraModel.getRowCount(); i++) {
            Object nameObj = extraModel.getValueAt(i, 0);
            String name = nameObj == null ? "" : nameObj.toString().trim();
            if (name.isEmpty()) {
                continue;
            }
            Object valueObj = extraModel.getValueAt(i, 1);
            String value = valueObj == null ? "" : valueObj.toString();
            append(sb, name, value);
        }
        sb.append("\nNota: las variables se aplican al reiniciar Tomcat.");
        preview.setText(sb.toString());
        preview.setCaretPosition(0);
    }

    private static void append(StringBuilder sb, String name, String value) {
        sb.append(name).append(" = ").append(value.isEmpty() ? "(vacío)" : value).append('\n');
    }

    private void showTomcatHelp() {
        JOptionPane.showMessageDialog(this,
                "<html><b>Si esta computadora no tiene Tomcat:</b><br><br>"
                + "1. Descarga Apache Tomcat 9 desde:<br>"
                + "&nbsp;&nbsp;&nbsp;https://tomcat.apache.org/download-90.cgi<br>"
                + "2. Descomprímelo en una carpeta, por ejemplo:<br>"
                + "&nbsp;&nbsp;&nbsp;<code>~/apache-tomcat-9.0.x</code><br>"
                + "3. Vuelve aquí y selecciona esa carpeta en <b>Tomcat (home)</b>.<br><br>"
                + "No hace falta instalar nada más: Tomcat se usa desde esa carpeta.</html>",
                "Tomcat no encontrado", JOptionPane.INFORMATION_MESSAGE);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(Math.max(size.width, 900), Math.max(size.height, 640));
    }
}
