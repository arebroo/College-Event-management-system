package util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * FILE HANDLING: reads and writes text files.
 * We write to a temporary file first and then rename it, so a crash in the
 * middle of saving can never leave a half-written (corrupt) data file.
 */
public class FileStore {

    public static boolean exists(String path) {
        return new File(path).exists();
    }

    public static String read(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    public static void write(String path, String content) throws IOException {
        File target = new File(path);
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        File temp = new File(path + ".tmp");
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8))) {
            bw.write(content);
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
