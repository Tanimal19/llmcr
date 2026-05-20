package com.llmcr.util;

import java.nio.file.Path;

public class PathUtils {
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();

    public static String toRelativePath(String pathString) {
        Path absolute = Path.of(pathString).toAbsolutePath().normalize();
        return PROJECT_ROOT.relativize(absolute).toString();
    }

    public static String toAbsolutePath(String relativePathString) {
        Path relative = Path.of(relativePathString);
        return PROJECT_ROOT.resolve(relative).toAbsolutePath().normalize().toString();
    }
}
