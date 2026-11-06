package org.totalnet.bootstrap.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ejecuta el build Ant del proyecto TotalNet usando el Ant incluido en
 * NetBeans. No usa el despliegue del IDE: solo compila (clean dist).
 */
public final class AntRunner {

    private AntRunner() {
    }

    public static int build(File projectDir, Consumer<String> log)
            throws IOException, InterruptedException {
        File ant = locateAnt();
        if (ant == null) {
            throw new IOException("No se encontró el ejecutable de Ant de NetBeans.");
        }

        ProcessBuilder pb = new ProcessBuilder(
                ant.getAbsolutePath(), "-f", "build.xml", "clean", "dist");
        pb.directory(projectDir);
        pb.redirectErrorStream(true);

        Map<String, String> env = pb.environment();
        env.remove("netbeans.home");
        File javaHome = new File(System.getProperty("java.home", ""));
        if ("jre".equals(javaHome.getName())) {
            javaHome = javaHome.getParentFile();
        }
        if (javaHome != null && javaHome.isDirectory()) {
            env.put("JAVA_HOME", javaHome.getAbsolutePath());
            String path = env.get("PATH");
            String bin = new File(javaHome, "bin").getAbsolutePath();
            env.put("PATH", bin + File.pathSeparator + (path == null ? "" : path));
        }

        log.accept("Ejecutando: " + ant.getAbsolutePath() + " clean dist");
        log.accept("Directorio: " + projectDir.getAbsolutePath());

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.accept(line);
            }
        }
        int exit = process.waitFor();
        log.accept("Build finalizado con código " + exit);
        return exit;
    }

    private static File locateAnt() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String exe = windows ? "ant.bat" : "ant";

        String netbeansDirs = System.getProperty("netbeans.dirs");
        if (netbeansDirs != null) {
            for (String dir : netbeansDirs.split(File.pathSeparator)) {
                File candidate = new File(dir, "ant/bin/" + exe);
                if (candidate.isFile()) {
                    return candidate;
                }
            }
        }

        String netbeansHome = System.getProperty("netbeans.home");
        if (netbeansHome != null) {
            File home = new File(netbeansHome);
            File[] roots = {home, home.getParentFile(), home.getParentFile() == null ? null : home.getParentFile().getParentFile()};
            for (File root : roots) {
                if (root == null) {
                    continue;
                }
                File candidate = new File(root, "extide/ant/bin/" + exe);
                if (candidate.isFile()) {
                    return candidate;
                }
                candidate = new File(root, "java/ant/bin/" + exe);
                if (candidate.isFile()) {
                    return candidate;
                }
            }
        }

        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                File candidate = new File(dir, exe);
                if (candidate.isFile()) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
