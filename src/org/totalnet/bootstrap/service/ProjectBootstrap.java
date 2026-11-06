package org.totalnet.bootstrap.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Copia la configuración del proyecto NetBeans (nbproject/build.xml) que no
 * está versionada en el repositorio y limpia el boilerplate generado por el
 * asistente de proyectos web.
 */
public final class ProjectBootstrap {

    private static final String RES = "/org/totalnet/bootstrap/resources/";

    private static final String[] NBPROJECT_FILES = {
        "project.xml",
        "project.properties",
        "build-impl.xml",
        "ant-deploy.xml"
    };

    private ProjectBootstrap() {
    }

    public static void bootstrap(File projectDir) throws IOException, InterruptedException {
        bootstrap(projectDir, "TotalNetEnlacesBancos");
    }

    public static void bootstrap(File projectDir, String projectName)
            throws IOException, InterruptedException {
        File nbproject = new File(projectDir, "nbproject");
        Files.createDirectories(nbproject.toPath());

        boolean fresh = !new File(nbproject, "project.xml").isFile();
        if (fresh) {
            for (String name : NBPROJECT_FILES) {
                copyResource(RES + "nbproject/" + name, new File(nbproject, name), true);
            }
        } else {
            for (String name : NBPROJECT_FILES) {
                copyResource(RES + "nbproject/" + name, new File(nbproject, name), false);
            }
        }
        copyResource(RES + "build.xml", new File(projectDir, "build.xml"), false);

        applyProjectName(projectDir, projectName);
        cleanupBoilerplate(projectDir);
    }

    private static void applyProjectName(File projectDir, String projectName) throws IOException {
        String name = projectName == null ? "" : projectName.trim();
        if (name.isEmpty() || "TotalNetEnlacesBancos".equals(name)) {
            return;
        }
        replaceInFile(new File(projectDir, "nbproject/project.xml"), name);
        replaceInFile(new File(projectDir, "nbproject/project.properties"), name);
        replaceInFile(new File(projectDir, "nbproject/build-impl.xml"), name);
        replaceInFile(new File(projectDir, "build.xml"), name);
    }

    private static void replaceInFile(File file, String projectName) throws IOException {
        if (!file.isFile()) {
            return;
        }
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        String updated = content.replace("TotalNetEnlacesBancos", projectName);
        if (!updated.equals(content)) {
            Files.writeString(file.toPath(), updated, StandardCharsets.UTF_8);
        }
    }

    private static void copyResource(String resource, File target, boolean overwrite) throws IOException {
        if (target.isFile() && !overwrite) {
            return;
        }
        if (target.getParentFile() != null) {
            Files.createDirectories(target.getParentFile().toPath());
        }
        try (InputStream in = ProjectBootstrap.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Recurso no encontrado en el plugin: " + resource);
            }
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Elimina solo archivos generados por el asistente que no estén versionados.
     * Los archivos reales del repositorio (web.xml, context.xml) se respetan.
     */
    public static void cleanupBoilerplate(File projectDir) throws IOException, InterruptedException {
        removeIfUntracked(projectDir, "web/index.html");
        removeIfUntracked(projectDir, "web/META-INF/MANIFEST.MF");
        removeIfUntracked(projectDir, "web/META-INF/context.xml");
    }

    private static void removeIfUntracked(File projectDir, String relativePath)
            throws IOException, InterruptedException {
        File file = new File(projectDir, relativePath);
        if (!file.isFile()) {
            return;
        }
        boolean tracked = GitService.isTracked(projectDir, relativePath);
        if (!tracked) {
            Files.deleteIfExists(file.toPath());
        }
    }
}
