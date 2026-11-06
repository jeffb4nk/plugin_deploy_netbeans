package org.totalnet.bootstrap.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.netbeans.api.project.libraries.Library;
import org.netbeans.api.project.libraries.LibraryManager;
import org.totalnet.bootstrap.model.EnvProfile;

/**
 * Escribe la configuración de máquina del proyecto (nbproject/private) y el
 * bloque gestionado de variables de entorno en el setenv de Tomcat.
 */
public final class EnvironmentConfigurator {

    private static final Logger LOG = Logger.getLogger(EnvironmentConfigurator.class.getName());

    private static final String[] NB_LIBRARIES = {
        "CopyLibs",
        "javaee-endorsed-api-7.0",
        "jsp-compilation",
        "jsp-compilation-syscp",
        "jsp-compiler"
    };

    private static final String MARK_BEGIN = "# >>> TOTALNET >>>";
    private static final String MARK_END = "# <<< TOTALNET <<<";
    private static final String MARK_BEGIN_BAT = "@REM >>> TOTALNET >>>";
    private static final String MARK_END_BAT = "@REM <<< TOTALNET <<<";

    private EnvironmentConfigurator() {
    }

    public static void apply(File projectDir, EnvProfile profile) throws IOException {
        if (profile.tomcatHome == null || profile.tomcatHome.trim().isEmpty()) {
            throw new IOException("Debes indicar la ruta de Tomcat.");
        }
        File tomcat = new File(profile.tomcatHome.trim());
        if (!tomcat.isDirectory()) {
            throw new IOException("La ruta de Tomcat no existe: " + tomcat);
        }

        File privateDir = new File(projectDir, "nbproject/private");
        Files.createDirectories(privateDir.toPath());

        writePrivateProperties(projectDir, privateDir, tomcat);
        writeBuildProperties(privateDir);
        writeTomcatProperties(privateDir, tomcat);
        profile.save(new File(privateDir, "totalnet-profile.properties"));
        updateSetenv(tomcat, profile);
    }

    private static void writePrivateProperties(File projectDir, File privateDir, File tomcat)
            throws IOException {
        String userProps = new File(privateDir, "build.properties").getAbsolutePath().replace('\\', '/');
        String deployProps = new File(privateDir, "tomcat90.properties").getAbsolutePath().replace('\\', '/');
        String tomcatPath = tomcat.getAbsolutePath().replace('\\', '/');

        StringBuilder sb = new StringBuilder();
        sb.append("deploy.ant.properties.file=").append(deployProps).append('\n');
        sb.append("j2ee.server.home=").append(tomcatPath).append('\n');
        sb.append("j2ee.server.instance=tomcat90:home=").append(tomcatPath).append('\n');
        sb.append("javac.debug=true\n");
        sb.append("selected.browser=default\n");
        sb.append("user.properties.file=").append(userProps).append('\n');
        writeString(new File(privateDir, "private.properties"), sb.toString());
    }

    private static void writeBuildProperties(File privateDir) throws IOException {
        Map<String, String> classpaths = new LinkedHashMap<>();

        LibraryManager manager = LibraryManager.getDefault();
        for (String name : NB_LIBRARIES) {
            Library library = manager.getLibrary(name);
            if (library == null) {
                continue;
            }
            String cp = joinLibraryContent(library);
            if (!cp.isEmpty()) {
                classpaths.put(name, cp);
            }
        }

        for (File userBuild : userdirBuildProperties()) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(userBuild.toPath())) {
                props.load(in);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "No se pudo leer " + userBuild, ex);
                continue;
            }
            for (String name : NB_LIBRARIES) {
                if (hasClasspath(classpaths, name)) {
                    continue;
                }
                String value = props.getProperty("libs." + name + ".classpath");
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }
                String normalized = value.trim().replace('\\', '/');
                if (allEntriesExist(normalized)) {
                    classpaths.put(name, normalized);
                }
            }
        }

        if (!hasClasspath(classpaths, "CopyLibs")) {
            File jar = findInNetBeans(
                    "java/ant/extra/org-netbeans-modules-java-j2seproject-copylibstask.jar");
            if (jar != null) {
                classpaths.put("CopyLibs", jar.getAbsolutePath().replace('\\', '/'));
            }
        }
        if (!hasClasspath(classpaths, "jsp-compiler")) {
            File jar = findInNetBeans("enterprise/ant/extra/jspcompile.jar");
            if (jar != null) {
                classpaths.put("jsp-compiler", jar.getAbsolutePath().replace('\\', '/'));
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("default.javac.source=17\n");
        sb.append("default.javac.target=17\n");
        for (Map.Entry<String, String> entry : classpaths.entrySet()) {
            sb.append("libs.").append(entry.getKey()).append(".classpath=")
                    .append(entry.getValue()).append('\n');
        }
        for (String name : NB_LIBRARIES) {
            if (!hasClasspath(classpaths, name)) {
                LOG.log(Level.WARNING, "Librería de NetBeans no encontrada: {0}", name);
            }
        }
        writeString(new File(privateDir, "build.properties"), sb.toString());
    }

    private static boolean hasClasspath(Map<String, String> classpaths, String name) {
        String value = classpaths.get(name);
        return value != null && !value.trim().isEmpty();
    }

    private static String joinLibraryContent(Library library) {
        List<URL> content = library.getContent("classpath");
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder cp = new StringBuilder();
        for (URL url : content) {
            try {
                File file = new File(url.toURI());
                if (cp.length() > 0) {
                    cp.append(File.pathSeparator);
                }
                cp.append(file.getAbsolutePath().replace('\\', '/'));
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "No se pudo resolver " + url, ex);
            }
        }
        return cp.toString();
    }

    private static boolean allEntriesExist(String classpath) {
        for (String entry : classpath.split(Pattern.quote(File.pathSeparator))) {
            if (!entry.trim().isEmpty() && !new File(entry.trim()).exists()) {
                return false;
            }
        }
        return true;
    }

    private static List<File> userdirBuildProperties() {
        List<File> result = new ArrayList<>();
        String[] properties = {"netbeans.user", "netbeans.default_userdir_root"};
        for (String property : properties) {
            String userdir = System.getProperty(property);
            if (userdir == null) {
                continue;
            }
            File candidate = new File(userdir, "build.properties");
            if (candidate.isFile() && !result.contains(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static File findInNetBeans(String relativePath) {
        List<File> roots = new ArrayList<>();
        String dirs = System.getProperty("netbeans.dirs");
        if (dirs != null) {
            for (String dir : dirs.split(File.pathSeparator)) {
                File cluster = new File(dir);
                roots.add(cluster);
                if (cluster.getParentFile() != null) {
                    roots.add(cluster.getParentFile());
                }
            }
        }
        String home = System.getProperty("netbeans.home");
        if (home != null) {
            File homeDir = new File(home);
            roots.add(homeDir);
            if (homeDir.getParentFile() != null) {
                roots.add(homeDir.getParentFile());
            }
        }
        for (File root : roots) {
            File direct = new File(root, relativePath);
            if (direct.isFile()) {
                return direct;
            }
            int slash = relativePath.indexOf('/');
            if (slash > 0) {
                File clusterRelative = new File(root, relativePath.substring(slash + 1));
                if (clusterRelative.isFile()) {
                    return clusterRelative;
                }
            }
        }
        return null;
    }

    private static void writeTomcatProperties(File privateDir, File tomcat) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("tomcat.home=").append(tomcat.getAbsolutePath().replace('\\', '/')).append('\n');
        sb.append("tomcat.url=http://localhost:8080\n");
        sb.append("tomcat.username=admin\n");
        writeString(new File(privateDir, "tomcat90.properties"), sb.toString());
    }

    private static void updateSetenv(File tomcat, EnvProfile profile) throws IOException {
        File bin = new File(tomcat, "bin");
        Files.createDirectories(bin.toPath());

        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        File file = new File(bin, windows ? "setenv.bat" : "setenv.sh");

        List<String> lines = new ArrayList<>();
        if (file.isFile()) {
            lines.addAll(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        }

        String oldPassword = extractOldPassword(lines, windows);
        String password = profile.dbPassword;
        if (password == null || password.isEmpty()) {
            password = oldPassword;
        }

        String begin = windows ? MARK_BEGIN_BAT : MARK_BEGIN;
        String end = windows ? MARK_END_BAT : MARK_END;

        Set<String> managedNames = new HashSet<>();
        List<String> block = new ArrayList<>();
        block.add(begin);
        addVariable(block, windows, "TOTALNET_DB_IP", profile.dbIp);
        managedNames.add("TOTALNET_DB_IP");
        addVariable(block, windows, "TOTALNET_DB_NAME", profile.dbName);
        managedNames.add("TOTALNET_DB_NAME");
        addVariable(block, windows, "TOTALNET_DB_USER", profile.dbUser);
        managedNames.add("TOTALNET_DB_USER");
        addVariable(block, windows, "TOTALNET_DB_PASSWORD", password);
        managedNames.add("TOTALNET_DB_PASSWORD");
        addVariable(block, windows, "TOTALNET_DB_DRIVER", profile.dbDriver);
        managedNames.add("TOTALNET_DB_DRIVER");

        Set<String> basicNames = new HashSet<>(Arrays.asList(
                "TOTALNET_DB_IP", "TOTALNET_DB_NAME", "TOTALNET_DB_USER",
                "TOTALNET_DB_PASSWORD", "TOTALNET_DB_DRIVER"));
        for (Map.Entry<String, String> entry : profile.extraVariables.entrySet()) {
            String name = entry.getKey() == null ? "" : entry.getKey().trim();
            if (name.isEmpty() || basicNames.contains(name)) {
                continue;
            }
            addVariable(block, windows, name, entry.getValue());
            managedNames.add(name);
        }
        block.add(end);

        List<String> kept = removeManagedBlock(lines, begin, end);
        kept.removeIf(line -> definesAny(line, managedNames, windows));
        kept.addAll(block);

        if (file.isFile()) {
            String stamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            File backup = new File(bin, file.getName() + ".bak." + stamp);
            Files.copy(file.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        StringBuilder out = new StringBuilder();
        for (String line : kept) {
            out.append(line).append('\n');
        }
        writeString(file, out.toString());
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(file.toPath(), perms);
        } catch (UnsupportedOperationException | IOException ex) {
            // Windows o sistema de archivos sin POSIX: se ignora.
        }
    }

    private static boolean definesAny(String line, Set<String> names, boolean windows) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("@REM")) {
            return false;
        }
        for (String name : names) {
            String quoted = Pattern.quote(name);
            if (windows) {
                if (trimmed.matches("(?i)^(set\\s+\"?)?\\s*" + quoted + "\\s*=.*")) {
                    return true;
                }
            } else if (trimmed.matches("^(export\\s+)?" + quoted + "\\s*=.*")) {
                return true;
            }
        }
        return false;
    }

    private static List<String> removeManagedBlock(List<String> lines, String begin, String end) {
        List<String> result = new ArrayList<>();
        boolean inside = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!inside && trimmed.equals(begin)) {
                inside = true;
                continue;
            }
            if (inside) {
                if (trimmed.equals(end)) {
                    inside = false;
                }
                continue;
            }
            result.add(line);
        }
        return result;
    }

    private static String extractOldPassword(List<String> lines, boolean windows) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.contains("TOTALNET_DB_PASSWORD")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String value = trimmed.substring(eq + 1).trim();
            if (windows) {
                value = stripQuotes(value);
            } else {
                value = stripQuotes(value.replace("export", "").trim());
            }
            return value;
        }
        return "";
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static void addVariable(List<String> block, boolean windows, String name, String value) {
        String safe = value == null ? "" : value;
        if (windows) {
            block.add("set \"" + name + "=" + safe.replace("%", "%%") + "\"");
        } else {
            block.add("export " + name + "=\"" + escapeShell(safe) + "\"");
        }
    }

    private static String escapeShell(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "\\$")
                .replace("`", "\\`");
    }

    private static void writeString(File file, String content) throws IOException {
        if (file.getParentFile() != null) {
            Files.createDirectories(file.getParentFile().toPath());
        }
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
