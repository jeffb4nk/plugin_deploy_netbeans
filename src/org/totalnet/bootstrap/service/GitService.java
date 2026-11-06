package org.totalnet.bootstrap.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Envoltorio simple sobre el comando git.
 */
public final class GitService {

    private GitService() {
    }

    public static final class Result {

        public final int exit;
        public final String output;

        Result(int exit, String output) {
            this.exit = exit;
            this.output = output;
        }
    }

    public static boolean isAvailable() {
        try {
            return run(null, "git", "--version").exit == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    public static Result run(File dir, String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (dir != null) {
            pb.directory(dir);
        }
        Map<String, String> env = pb.environment();
        env.put("GIT_TERMINAL_PROMPT", "0");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
        }
        int exit = process.waitFor();
        return new Result(exit, out.toString());
    }

    private static Result runChecked(File dir, String... command)
            throws IOException, InterruptedException, GitException {
        Result result = run(dir, command);
        if (result.exit != 0) {
            throw new GitException("git " + String.join(" ", command) + " falló:\n" + result.output);
        }
        return result;
    }

    public static void cloneRepository(String url, File destination)
            throws IOException, InterruptedException, GitException {
        if (destination.exists()) {
            if (!destination.isDirectory()) {
                throw new GitException("La ruta destino no es una carpeta: " + destination);
            }
            File[] children = destination.listFiles();
            if (children != null && children.length > 0) {
                throw new GitException("La carpeta destino no está vacía: " + destination);
            }
        }
        if (destination.getParentFile() != null) {
            destination.getParentFile().mkdirs();
        }
        runChecked(destination.getParentFile(), "git", "clone", url, destination.getAbsolutePath());
    }

    public static void fetch(File repository)
            throws IOException, InterruptedException, GitException {
        runChecked(repository, "git", "fetch", "--all", "--prune");
    }

    public static boolean isGitRepository(File dir) {
        return new File(dir, ".git").isDirectory();
    }

    public static List<String> listRemoteBranches(String url)
            throws IOException, InterruptedException, GitException {
        Result result = runChecked(null, "git", "ls-remote", "--heads", url);
        List<String> branches = new ArrayList<>();
        for (String line : result.output.split("\\R")) {
            int idx = line.indexOf("refs/heads/");
            if (idx >= 0) {
                String name = line.substring(idx + "refs/heads/".length()).trim();
                if (!name.isEmpty()) {
                    branches.add(name);
                }
            }
        }
        Collections.sort(branches);
        return branches;
    }

    /**
     * Deja el repositorio en master sin crear ninguna rama nueva.
     */
    public static void checkoutMaster(File repository)
            throws IOException, InterruptedException, GitException {
        Result local = run(repository, "git", "rev-parse", "--verify", "refs/heads/master");
        if (local.exit != 0) {
            runChecked(repository, "git", "checkout", "-b", "master", "origin/master");
            return;
        }
        runChecked(repository, "git", "checkout", "master");
        Result pull = run(repository, "git", "pull", "--ff-only", "origin", "master");
        if (pull.exit != 0) {
            throw new GitException("No se pudo actualizar master (posiblemente hay cambios locales):\n"
                    + pull.output);
        }
    }

    public static void createBranch(File repository, String branchName)
            throws IOException, InterruptedException, GitException {
        Result base = run(repository, "git", "rev-parse", "--verify", "origin/master");
        if (base.exit == 0) {
            runChecked(repository, "git", "checkout", "-b", branchName, "origin/master");
        } else {
            runChecked(repository, "git", "checkout", "-b", branchName, "master");
        }
    }

    public static void checkoutExisting(File repository, String branchName)
            throws IOException, InterruptedException, GitException {
        runChecked(repository, "git", "fetch", "origin", "--prune");
        Result local = run(repository, "git", "rev-parse", "--verify", "refs/heads/" + branchName);
        if (local.exit == 0) {
            runChecked(repository, "git", "checkout", branchName);
            Result pull = run(repository, "git", "pull", "--ff-only", "origin", branchName);
            if (pull.exit != 0) {
                throw new GitException("No se pudo actualizar la rama " + branchName
                        + " (posiblemente hay cambios locales):\n" + pull.output);
            }
        } else {
            runChecked(repository, "git", "checkout", "-b", branchName, "origin/" + branchName);
        }
    }

    public static boolean isTracked(File repository, String relativePath)
            throws IOException, InterruptedException {
        return run(repository, "git", "ls-files", "--error-unmatch", "--", relativePath).exit == 0;
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static final class GitException extends Exception {

        public GitException(String message) {
            super(message);
        }
    }
}
