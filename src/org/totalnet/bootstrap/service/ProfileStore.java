package org.totalnet.bootstrap.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.totalnet.bootstrap.model.EnvProfile;

/**
 * Almacén de perfiles de entorno (Tomcat + variables de BD) reutilizables.
 * Vive en ~/.totalnet/profiles y no se versiona.
 */
public final class ProfileStore {

    private static final String DIR = ".totalnet/profiles";

    private ProfileStore() {
    }

    public static File profilesDir() {
        File dir = new File(System.getProperty("user.home", "."), DIR);
        try {
            Files.createDirectories(dir.toPath());
            try {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwx------");
                Files.setPosixFilePermissions(dir.toPath(), perms);
            } catch (Exception ignored) {
                // Windows o sistema sin POSIX.
            }
        } catch (IOException ignored) {
            // Se reportará al guardar.
        }
        return dir;
    }

    public static List<String> list() {
        List<String> result = new ArrayList<>();
        File[] files = profilesDir().listFiles((dir, name) -> name.endsWith(".properties"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                result.add(name.substring(0, name.length() - ".properties".length()));
            }
        }
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public static EnvProfile load(String name) {
        EnvProfile profile = EnvProfile.load(fileFor(name));
        if (profile.name == null || profile.name.trim().isEmpty()) {
            profile.name = name;
        }
        return profile;
    }

    public static void save(EnvProfile profile) throws IOException {
        String name = profile.name == null ? "" : profile.name.trim();
        if (name.isEmpty()) {
            throw new IOException("El perfil debe tener un nombre.");
        }
        profile.name = name;
        profile.save(fileFor(name));
    }

    public static void delete(String name) throws IOException {
        Files.deleteIfExists(fileFor(name).toPath());
    }

    private static File fileFor(String name) {
        return new File(profilesDir(), sanitize(name) + ".properties");
    }

    private static String sanitize(String name) {
        String safe = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9 ._-]", "_");
        return safe.isEmpty() ? "perfil" : safe;
    }
}
