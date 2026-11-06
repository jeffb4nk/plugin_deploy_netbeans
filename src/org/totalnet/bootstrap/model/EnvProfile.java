package org.totalnet.bootstrap.model;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Perfil de entorno (base de datos y Tomcat) asociado a un proyecto TotalNet.
 */
public final class EnvProfile {

    public String name = "";
    public String dbIp = "";
    public String dbName = "";
    public String dbUser = "";
    public String dbPassword = "";
    public String dbDriver = "org.postgresql.Driver";
    public String dbUrl = "";
    public String codEntorno = "";
    public String archivosPath = "";
    public String dbIpCal = "";
    public String dbNameCal = "";
    public String tomcatHome = "";
    public final Map<String, String> extraVariables = new LinkedHashMap<>();

    private static final String P_NAME = "totalnet.profile.name";
    private static final String P_DB_IP = "totalnet.db.ip";
    private static final String P_DB_NAME = "totalnet.db.name";
    private static final String P_DB_USER = "totalnet.db.user";
    private static final String P_DB_PASSWORD = "totalnet.db.password";
    private static final String P_DB_DRIVER = "totalnet.db.driver";
    private static final String P_DB_URL = "totalnet.db.url";
    private static final String P_COD_ENTORNO = "totalnet.cod.entorno";
    private static final String P_ARCHIVOS_PATH = "totalnet.archivos.path";
    private static final String P_DB_IP_CAL = "totalnet.db.ip.cal";
    private static final String P_DB_NAME_CAL = "totalnet.db.name.cal";
    private static final String P_TOMCAT_HOME = "totalnet.tomcat.home";
    private static final String P_EXTRA = "totalnet.extra.";

    public static EnvProfile load(File file) {
        EnvProfile p = new EnvProfile();
        if (file == null || !file.isFile()) {
            return p;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file.toPath())) {
            props.load(in);
        } catch (IOException ex) {
            return p;
        }
        p.name = props.getProperty(P_NAME, "");
        p.dbIp = props.getProperty(P_DB_IP, "");
        p.dbName = props.getProperty(P_DB_NAME, "");
        p.dbUser = props.getProperty(P_DB_USER, "");
        p.dbPassword = props.getProperty(P_DB_PASSWORD, "");
        p.dbDriver = props.getProperty(P_DB_DRIVER, "org.postgresql.Driver");
        p.dbUrl = props.getProperty(P_DB_URL, "");
        p.codEntorno = props.getProperty(P_COD_ENTORNO, "");
        p.archivosPath = props.getProperty(P_ARCHIVOS_PATH, "");
        p.dbIpCal = props.getProperty(P_DB_IP_CAL, "");
        p.dbNameCal = props.getProperty(P_DB_NAME_CAL, "");
        p.tomcatHome = props.getProperty(P_TOMCAT_HOME, "");

        for (String key : props.stringPropertyNames()) {
            if (key.startsWith(P_EXTRA)) {
                String name = key.substring(P_EXTRA.length()).trim();
                if (!name.isEmpty()) {
                    p.extraVariables.put(name, props.getProperty(key, ""));
                }
            }
        }
        addIfMissing(p, "TOTALNET_DB_URL", p.dbUrl);
        addIfMissing(p, "TOTALNET_COD_ENTORNO", p.codEntorno);
        addIfMissing(p, "TOTALNET_ARCHIVOS_PATH", p.archivosPath);
        addIfMissing(p, "TOTALNET_DB_IP_CAL", p.dbIpCal);
        addIfMissing(p, "TOTALNET_DB_NAME_CAL", p.dbNameCal);
        return p;
    }

    private static void addIfMissing(EnvProfile profile, String name, String value) {
        if (value != null && !value.trim().isEmpty() && !profile.extraVariables.containsKey(name)) {
            profile.extraVariables.put(name, value.trim());
        }
    }

    public void save(File file) throws IOException {
        if (file.getParentFile() != null) {
            Files.createDirectories(file.getParentFile().toPath());
        }
        Properties props = new Properties();
        props.setProperty(P_NAME, name == null ? "" : name);
        props.setProperty(P_DB_IP, dbIp);
        props.setProperty(P_DB_NAME, dbName);
        props.setProperty(P_DB_USER, dbUser);
        props.setProperty(P_DB_PASSWORD, dbPassword);
        props.setProperty(P_DB_DRIVER, dbDriver);
        props.setProperty(P_DB_URL, dbUrl);
        props.setProperty(P_COD_ENTORNO, codEntorno);
        props.setProperty(P_ARCHIVOS_PATH, archivosPath);
        props.setProperty(P_DB_IP_CAL, dbIpCal);
        props.setProperty(P_DB_NAME_CAL, dbNameCal);
        props.setProperty(P_TOMCAT_HOME, tomcatHome);
        for (Map.Entry<String, String> entry : extraVariables.entrySet()) {
            String name = entry.getKey() == null ? "" : entry.getKey().trim();
            if (!name.isEmpty()) {
                props.setProperty(P_EXTRA + name, entry.getValue() == null ? "" : entry.getValue());
            }
        }

        Path tmp = file.toPath().resolveSibling(file.getName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            props.store(out, "Perfil TotalNet (no versionar)");
        }
        try {
            Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(file.toPath(), perms);
        } catch (UnsupportedOperationException | IOException ex) {
            // Windows o sistema de archivos sin POSIX: se ignora.
        }
    }
}
