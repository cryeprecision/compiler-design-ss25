package edu.kit.kastel.vads.compiler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;

import org.fusesource.jansi.Ansi.Color;
import static org.fusesource.jansi.Ansi.*;

public class GccRunner {
    private enum StreamName {
        STDOUT("stdout"),
        STDERR("stderr");

        public final String name;

        StreamName(String name) {
            this.name = name;
        }
    }

    private static void consumeStream(InputStream stream, StreamName streamName) throws IOException {
        Color color = streamName == StreamName.STDOUT ? Color.GREEN : Color.RED;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            System.err.println(ansi().fg(color).a("┌─── BEGIN GCC ─── " + streamName.name + " ───").reset());
            while ((line = reader.readLine()) != null) {
                System.err.print(ansi().fg(color).a("│ ").reset());
                System.err.println(line);
            }
            System.err.println(ansi().fg(color).a("└──── END GCC ──── " + streamName.name + " ───").reset());
        }
    }

    public static void invoke(Path assemblyFile, Path outputFile) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "gcc", "-g",
                    "-o", outputFile.toString(),
                    assemblyFile.toString());

            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                try (InputStream stdout = process.getInputStream()) {
                    consumeStream(stdout, StreamName.STDOUT);
                }
                try (InputStream stderr = process.getErrorStream()) {
                    consumeStream(stderr, StreamName.STDERR);
                }
                throw new RuntimeException("Error invoking GCC");
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error invoking GCC: " + e.getMessage());
        }
    }
}
