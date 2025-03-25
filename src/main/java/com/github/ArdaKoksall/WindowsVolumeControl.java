package com.github.ArdaKoksall;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controls Windows system volume using the bundled NirCmd utility.
 *
 * <p>This class extracts nircmd.exe from the JAR resources to a temporary
 * location upon initialization and uses it to execute volume commands.</p>
 *
 * <p><b>Prerequisites:</b> Must be run on a Windows operating system.</p>
 * <p><b>Bundled Dependency:</b> Requires nircmd.exe (from NirSoft) to be present
 * in the classpath resources (typically src/main/resources/nircmd.exe in a Maven/Gradle project).</p>
 * <p><b>License Note:</b> Ensure compliance with the NirCmd license agreement when distributing this library.</p>
 */
public class WindowsVolumeControl {

    private static final Logger LOGGER = Logger.getLogger(WindowsVolumeControl.class.getName());
    private static final String NIRCMD_RESOURCE_NAME = "/nircmd.exe";
    private static final int NIRCMD_MAX_VOLUME = 65535;
    private static String extractedNirCmdPath = null;

    static {
        try {
            extractedNirCmdPath = extractNirCmd();
            LOGGER.log(Level.INFO, "NirCmd extracted successfully to: {0}", extractedNirCmdPath);

            Path tempPath = Path.of(extractedNirCmdPath);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.deleteIfExists(tempPath);
                    LOGGER.log(Level.INFO, "Temporary NirCmd file deleted: {0}", tempPath);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to delete temporary NirCmd file: " + tempPath, e);
                }
            }));

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "FATAL: Failed to extract nircmd.exe from resources. Volume control will not work.", e);
            throw new IllegalStateException("Could not initialize WindowsVolumeControl: Failed to extract nircmd.exe", e);
        } catch (SecurityException e) {
            LOGGER.log(Level.SEVERE, "FATAL: Security restrictions prevent extracting or executing NirCmd.", e);
            throw new IllegalStateException("Could not initialize WindowsVolumeControl due to security restrictions", e);
        }
    }

    /**
     * Extracts nircmd.exe from the classpath resources to a temporary file.
     *
     * @return The absolute path to the extracted executable file.
     * @throws IOException If the resource cannot be found or copied.
     * @throws SecurityException If file operations are restricted.
     */
    private static String extractNirCmd() throws IOException, SecurityException {
        InputStream nirCmdStream = WindowsVolumeControl.class.getResourceAsStream(NIRCMD_RESOURCE_NAME);
        if (nirCmdStream == null) {
            throw new IOException("Cannot find '" + NIRCMD_RESOURCE_NAME + "' in classpath resources.");
        }

        Path tempFile = Files.createTempFile("nircmd-", ".exe");

        LOGGER.log(Level.FINE, "Extracting NirCmd resource to temporary file: {0}", tempFile.toAbsolutePath());

        try (OutputStream out = Files.newOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = nirCmdStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

        } finally {
            try {
                nirCmdStream.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing NirCmd resource stream", e);
            }
        }

        return tempFile.toAbsolutePath().toString();
    }

    /**
     * Sets the system volume to a specific percentage (0-100).
     *
     * @param percentage The desired volume level (0 to 100).
     * @throws IOException If an error occurs executing NirCmd.
     * @throws InterruptedException If the process execution is interrupted.
     * @throws IllegalArgumentException If percentage is outside the 0-100 range.
     * @throws IllegalStateException if NirCmd could not be initialized.
     */
    public void setVolume(int percentage) throws IOException, InterruptedException {
        checkNirCmdReady();
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("Percentage must be between 0 and 100.");
        }
        int nircmdVolume = (int) Math.round((percentage / 100.0) * NIRCMD_MAX_VOLUME);
        executeNirCmd("setsysvolume", String.valueOf(nircmdVolume));
        LOGGER.log(Level.INFO, "System volume set to {0}% (NirCmd value: {1})", new Object[]{percentage, nircmdVolume});
    }

    /**
     * Increases the system volume by a specific percentage step.
     *
     * @param percentageStep The percentage points to increase the volume by.
     * @throws IOException If an error occurs executing NirCmd.
     * @throws InterruptedException If the process execution is interrupted.
     * @throws IllegalArgumentException If percentageStep is negative.
     * @throws IllegalStateException if NirCmd could not be initialized.
     */
    public void increaseVolume(int percentageStep) throws IOException, InterruptedException {
        checkNirCmdReady();
        if (percentageStep < 0) {
            throw new IllegalArgumentException("Percentage step must be non-negative for increase.");
        }
        int nircmdChange = (int) Math.round((percentageStep / 100.0) * NIRCMD_MAX_VOLUME);
        executeNirCmd("changesysvolume", String.valueOf(nircmdChange));
        LOGGER.log(Level.INFO, "System volume increased by {0}% (NirCmd change: {1})", new Object[]{percentageStep, nircmdChange});
    }

    /**
     * Decreases the system volume by a specific percentage step.
     *
     * @param percentageStep The percentage points to decrease the volume by.
     * @throws IOException If an error occurs executing NirCmd.
     * @throws InterruptedException If the process execution is interrupted.
     * @throws IllegalArgumentException If percentageStep is negative.
     * @throws IllegalStateException if NirCmd could not be initialized.
     */
    public void decreaseVolume(int percentageStep) throws IOException, InterruptedException {
        checkNirCmdReady();
        if (percentageStep < 0) {
            throw new IllegalArgumentException("Percentage step must be non-negative for decrease.");
        }
        int nircmdChange = (int) Math.round((percentageStep / 100.0) * NIRCMD_MAX_VOLUME);
        executeNirCmd("changesysvolume", String.valueOf(-nircmdChange));
        LOGGER.log(Level.INFO, "System volume decreased by {0}% (NirCmd change: {1})", new Object[]{percentageStep, -nircmdChange});
    }

    /**
     * Mutes the system volume.
     *
     * @throws IOException If an error occurs executing NirCmd.
     * @throws InterruptedException If the process execution is interrupted.
     * @throws IllegalStateException if NirCmd could not be initialized.
     */
    public void mute() throws IOException, InterruptedException {
        checkNirCmdReady();
        executeNirCmd("mutesysvolume", "1");
        LOGGER.info("System volume muted.");
    }

    /**
     * Unmutes the system volume.
     *
     * @throws IOException If an error occurs executing NirCmd.
     * @throws InterruptedException If the process execution is interrupted.
     * @throws IllegalStateException if NirCmd could not be initialized.
     */
    public void unmute() throws IOException, InterruptedException {
        checkNirCmdReady();
        executeNirCmd("mutesysvolume", "0");
        LOGGER.info("System volume unmuted.");
    }

    /**
     * Toggles the system volume mute state.
     *
     * @throws IOException If an error occurs executing NirCmd.
     * @throws InterruptedException If the process execution is interrupted.
     * @throws IllegalStateException if NirCmd could not be initialized.
     */
    public void toggleMute() throws IOException, InterruptedException {
        checkNirCmdReady();
        executeNirCmd("mutesysvolume", "2");
        LOGGER.info("System volume mute toggled.");
    }

    /**
     * Checks if the NirCmd executable path was successfully determined during initialization.
     * @throws IllegalStateException if NirCmd path is null (initialization failed).
     */
    private void checkNirCmdReady() {
        if (extractedNirCmdPath == null) {
            throw new IllegalStateException("NirCmd executable path is not available. Initialization likely failed. Check logs.");
        }
    }

    /**
     * Executes a NirCmd command using the extracted executable.
     *
     * @param command The NirCmd command (e.g., "setsysvolume").
     * @param args    Variable arguments for the NirCmd command.
     * @throws IOException If an I/O error occurs during process execution.
     * @throws InterruptedException If the current thread is interrupted while waiting.
     */
    private void executeNirCmd(String command, String... args) throws IOException, InterruptedException {
        if (extractedNirCmdPath == null) {
            throw new IOException("NirCmd path not initialized.");
        }

        List<String> commandList = new ArrayList<>();
        commandList.add(extractedNirCmdPath);
        commandList.add(command);
        commandList.addAll(Arrays.asList(args));

        ProcessBuilder processBuilder = new ProcessBuilder(commandList);

        LOGGER.log(Level.FINE, "Executing: {0}", String.join(" ", commandList));

        Process process = null;
        try {
            process = processBuilder.start();

            StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), "OUTPUT");
            StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), "ERROR");
            new Thread(outputGobbler).start();
            new Thread(errorGobbler).start();

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                LOGGER.log(Level.WARNING, "NirCmd command finished with non-zero exit code: {0}. Command: {1}",
                        new Object[]{exitCode, String.join(" ", commandList)});
            } else {
                LOGGER.log(Level.FINE, "NirCmd command finished successfully (exit code 0).");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error executing NirCmd command: " + String.join(" ", commandList), e);
            throw e;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private record StreamGobbler(InputStream inputStream, String type) implements Runnable {
        private StreamGobbler(InputStream inputStream, String type) {
            this.inputStream = inputStream;
            this.type = type.toUpperCase();
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.log(Level.FINEST, "{0}> {1}", new Object[]{type, line});
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error reading process " + type + " stream", e);
            }
        }
    }
}