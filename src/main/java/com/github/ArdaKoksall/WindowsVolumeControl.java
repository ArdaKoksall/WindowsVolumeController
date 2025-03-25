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
 */
public class WindowsVolumeControl {

    private static final Logger LOGGER = Logger.getLogger(WindowsVolumeControl.class.getName());
    private static final String NIRCMD_RESOURCE_NAME = "/nircmd.exe";
    private static final int NIRCMD_MAX_VOLUME = 65535;
    private static final String extractedNirCmdPath;
    private static boolean isLoggingEnabled = true;

    static {
        try {
            extractedNirCmdPath = extractNirCmd();
            LOGGER.log(Level.INFO, "NirCmd extracted successfully to: {0}", extractedNirCmdPath);

            Path tempPath = Path.of(extractedNirCmdPath);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.deleteIfExists(tempPath);
                    if (isLoggingEnabled) {
                        LOGGER.log(Level.INFO, "Temporary NirCmd file deleted: {0}", tempPath);
                    }
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
     * Enables informational logging for volume control operations.
     * Critical errors and warnings are always logged.
     */
    public void enableLogging() {
        isLoggingEnabled = true;
        LOGGER.log(Level.INFO, "WindowsVolumeControl logging enabled.");
    }

    /**
     * Disables informational logging for volume control operations.
     * Critical errors and warnings are always logged.
     */
    public void disableLogging() {
        if (isLoggingEnabled) {
            LOGGER.log(Level.INFO, "WindowsVolumeControl logging disabled.");
        }
        isLoggingEnabled = false;
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

        if (isLoggingEnabled) {
            LOGGER.log(Level.FINE, "Extracting NirCmd resource to temporary file: {0}", tempFile.toAbsolutePath());
        }

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
     * If an error occurs during execution (e.g., NirCmd fails), it is logged as SEVERE.
     *
     * @param percentage The desired volume level (0 to 100).
     * @throws IllegalArgumentException If percentage is outside the 0-100 range.
     * @throws IllegalStateException if NirCmd could not be initialized (during class loading).
     */
    public void setVolume(int percentage) {
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("Percentage must be between 0 and 100.");
        }
        try {
            checkNirCmdReady();
            int nircmdVolume = (int) Math.round((percentage / 100.0) * NIRCMD_MAX_VOLUME);
            executeNirCmd("setsysvolume", String.valueOf(nircmdVolume));
            if (isLoggingEnabled) {
                LOGGER.log(Level.INFO, "System volume set to {0}% (NirCmd value: {1})", new Object[]{percentage, nircmdVolume});
            }
        } catch (IOException | InterruptedException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to set volume to " + percentage + "%", e);
        }
    }

    /**
     * Increases the system volume by a specific percentage step.
     * If an error occurs during execution (e.g., NirCmd fails), it is logged as SEVERE.
     *
     * @param percentageStep The percentage points to increase the volume by. Must be non-negative.
     * @throws IllegalArgumentException If percentageStep is negative.
     * @throws IllegalStateException if NirCmd could not be initialized (during class loading).
     */
    public void increaseVolume(int percentageStep) {
        if (percentageStep < 0) {
            throw new IllegalArgumentException("Percentage step must be non-negative for increase.");
        }
        try {
            checkNirCmdReady();
            int nircmdChange = (int) Math.round((percentageStep / 100.0) * NIRCMD_MAX_VOLUME);
            executeNirCmd("changesysvolume", String.valueOf(nircmdChange));
            if (isLoggingEnabled) {
                LOGGER.log(Level.INFO, "System volume increased by {0}% (NirCmd change: {1})", new Object[]{percentageStep, nircmdChange});
            }
        } catch (IOException | InterruptedException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to increase volume by " + percentageStep + "%", e);
        }
    }

    /**
     * Decreases the system volume by a specific percentage step.
     * If an error occurs during execution (e.g., NirCmd fails), it is logged as SEVERE.
     *
     * @param percentageStep The percentage points to decrease the volume by. Must be non-negative.
     * @throws IllegalArgumentException If percentageStep is negative.
     * @throws IllegalStateException if NirCmd could not be initialized (during class loading).
     */
    public void decreaseVolume(int percentageStep) {
        if (percentageStep < 0) {
            throw new IllegalArgumentException("Percentage step must be non-negative for decrease.");
        }
        try {
            checkNirCmdReady();
            int nircmdChange = (int) Math.round((percentageStep / 100.0) * NIRCMD_MAX_VOLUME);
            executeNirCmd("changesysvolume", String.valueOf(-nircmdChange));
            if (isLoggingEnabled) {
                LOGGER.log(Level.INFO, "System volume decreased by {0}% (NirCmd change: {1})", new Object[]{percentageStep, -nircmdChange});
            }
        } catch (IOException | InterruptedException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to decrease volume by " + percentageStep + "%", e);
        }
    }

    /**
     * Mutes the system volume.
     * If an error occurs during execution (e.g., NirCmd fails), it is logged as SEVERE.
     *
     * @throws IllegalStateException if NirCmd could not be initialized (during class loading).
     */
    public void mute() {
        try {
            checkNirCmdReady();
            executeNirCmd("mutesysvolume", "1");
            if (isLoggingEnabled) {
                LOGGER.info("System volume muted.");
            }
        } catch (IOException | InterruptedException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to mute volume", e);
        }
    }

    /**
     * Unmutes the system volume.
     * If an error occurs during execution (e.g., NirCmd fails), it is logged as SEVERE.
     *
     * @throws IllegalStateException if NirCmd could not be initialized (during class loading).
     */
    public void unmute() {
        try {
            checkNirCmdReady();
            executeNirCmd("mutesysvolume", "0");
            if (isLoggingEnabled) {
                LOGGER.info("System volume unmuted.");
            }
        } catch (IOException | InterruptedException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to unmute volume", e);
        }
    }

    /**
     * Toggles the system volume mute state.
     * If an error occurs during execution (e.g., NirCmd fails), it is logged as SEVERE.
     *
     * @throws IllegalStateException if NirCmd could not be initialized (during class loading).
     */
    public void toggleMute() {
        try {
            checkNirCmdReady();
            executeNirCmd("mutesysvolume", "2");
            if (isLoggingEnabled) {
                LOGGER.info("System volume mute toggled.");
            }
        } catch (IOException | InterruptedException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to toggle mute state", e);
        }
    }

    /**
     * Gets the current system volume percentage (0-100).
     * Uses the `getsysvolume` NirCmd command. Parses the first number returned if multiple are present (e.g., L/R channels).
     * If an error occurs during execution or parsing, it is logged as SEVERE and -1 is returned.
     *
     * @return The current volume percentage (0-100), or -1 if the volume could not be retrieved or parsed.
     * @throws IllegalStateException if NirCmd could not be initialized (during class loading).
     */
    public int getVolume() {
        try {
            checkNirCmdReady();
            String rawOutput = executeNirCmdAndCaptureOutput("getsysvolume");

            if (rawOutput == null || rawOutput.trim().isEmpty()) {
                LOGGER.log(Level.WARNING, "NirCmd getsysvolume returned empty or null output.");
                return -1;
            }

            String[] parts = rawOutput.trim().split("\\s+");
            if (parts.length == 0) {
                LOGGER.log(Level.WARNING, "NirCmd getsysvolume returned unexpected format (no numbers): {0}", rawOutput);
                return -1;
            }

            int nircmdVolume = Integer.parseInt(parts[0]);
            if (nircmdVolume < 0 || nircmdVolume > NIRCMD_MAX_VOLUME) {
                LOGGER.log(Level.WARNING, "NirCmd getsysvolume returned value outside expected range [0, {0}]: {1}", new Object[]{NIRCMD_MAX_VOLUME, nircmdVolume});
                nircmdVolume = Math.max(0, Math.min(NIRCMD_MAX_VOLUME, nircmdVolume));
            }

            int percentage = (int) Math.round((nircmdVolume / (double) NIRCMD_MAX_VOLUME) * 100.0);

            if (isLoggingEnabled) {
                LOGGER.log(Level.INFO, "Current system volume retrieved: {0}% (NirCmd value: {1})", new Object[]{percentage, nircmdVolume});
            }
            return percentage;

        } catch (IOException | InterruptedException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to get current volume", e);
            return -1;
        } catch (NumberFormatException e) {
            LOGGER.log(Level.SEVERE, "Failed to parse volume output from NirCmd", e);
            return -1;
        }
    }

    /**
     * Checks if the NirCmd executable path was successfully determined during initialization.
     * @throws IllegalStateException if NirCmd path is null (initialization failed).
     */
    private void checkNirCmdReady() throws IllegalStateException {
        if (extractedNirCmdPath == null) {
            throw new IllegalStateException("NirCmd executable path is not available. Initialization likely failed. Check logs.");
        }
    }

    /**
     * Executes a NirCmd command without capturing its standard output (primarily for actions).
     * Standard error is consumed and logged.
     *
     * @param command The NirCmd command (e.g., "setsysvolume").
     * @param args    Variable arguments for the NirCmd command.
     * @throws IOException If an I/O error occurs during process execution or if NirCmd path isn't initialized.
     * @throws InterruptedException If the current thread is interrupted while waiting.
     * @throws IllegalStateException if NirCmd could not be initialized (during class loading or check).
     */
    private void executeNirCmd(String command, String... args) throws IOException, InterruptedException, IllegalStateException {
        checkNirCmdReady();

        List<String> commandList = new ArrayList<>();
        commandList.add(extractedNirCmdPath);
        commandList.add(command);
        commandList.addAll(Arrays.asList(args));

        ProcessBuilder processBuilder = new ProcessBuilder(commandList);

        if (isLoggingEnabled) {
            LOGGER.log(Level.FINE, "Executing: {0}", String.join(" ", commandList));
        }

        Process process = null;
        try {
            process = processBuilder.start();

            StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), "OUTPUT", isLoggingEnabled);
            StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), "ERROR", isLoggingEnabled);
            new Thread(outputGobbler).start();
            new Thread(errorGobbler).start();

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                LOGGER.log(Level.WARNING, "NirCmd command finished with non-zero exit code: {0}. Command: {1}",
                        new Object[]{exitCode, String.join(" ", commandList)});
            } else {
                if (isLoggingEnabled) {
                    LOGGER.log(Level.FINE, "NirCmd command finished successfully (exit code 0).");
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error executing NirCmd command: " + String.join(" ", commandList), e);
            throw e;
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "NirCmd execution interrupted: " + String.join(" ", commandList), e);
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * Executes a NirCmd command and captures the first line of its standard output.
     * Standard error is consumed and logged.
     *
     * @param command The NirCmd command (e.g., "getsysvolume").
     * @param args    Variable arguments for the NirCmd command.
     * @return The first line of the standard output from the command, or null if no output was produced.
     * @throws IOException If an I/O error occurs, the command returns a non-zero exit code, or if NirCmd path isn't initialized.
     * @throws InterruptedException If the current thread is interrupted while waiting.
     * @throws IllegalStateException if NirCmd could not be initialized (during class loading or check).
     */
    private String executeNirCmdAndCaptureOutput(String command, String... args) throws IOException, InterruptedException, IllegalStateException {
        checkNirCmdReady();

        List<String> commandList = new ArrayList<>();
        commandList.add(extractedNirCmdPath);
        commandList.add(command);
        commandList.addAll(Arrays.asList(args));

        ProcessBuilder processBuilder = new ProcessBuilder(commandList);

        if (isLoggingEnabled) {
            LOGGER.log(Level.FINE, "Executing for output capture: {0}", String.join(" ", commandList));
        }

        Process process = null;
        String outputLine;

        try {
            process = processBuilder.start();

            StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), "ERROR", isLoggingEnabled);
            new Thread(errorGobbler).start();

            try (InputStream stdout = process.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(stdout))) {

                int exitCode = process.waitFor();

                if (exitCode != 0) {
                    LOGGER.log(Level.WARNING, "NirCmd command finished with non-zero exit code: {0}. Command: {1}",
                            new Object[]{exitCode, String.join(" ", commandList)});
                    throw new IOException("NirCmd command failed with exit code " + exitCode + " for command: " + String.join(" ", commandList));
                } else {
                    if (isLoggingEnabled) {
                        LOGGER.log(Level.FINE, "NirCmd command finished successfully (exit code 0).");
                    }
                    outputLine = reader.readLine();
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error executing or reading output from NirCmd command: " + String.join(" ", commandList), e);
            throw e;
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "NirCmd execution interrupted: " + String.join(" ", commandList), e);
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return outputLine;
    }


    /**
     * Helper record to consume and optionally log input/error streams from a Process.
     *
     * @param inputStream The stream to read from (stdout or stderr).
     * @param type A label for the stream type ("OUTPUT" or "ERROR") used in logging.
     * @param loggingEnabled Flag indicating whether to log the stream content (at FINEST level).
     */
    private record StreamGobbler(InputStream inputStream, String type, boolean loggingEnabled) implements Runnable {
        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (loggingEnabled) {
                        LOGGER.log(Level.FINEST, "{0}> {1}", new Object[]{type.toUpperCase(), line});
                    }
                }
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    LOGGER.log(Level.WARNING, "Error reading process " + type.toUpperCase() + " stream", e);
                }
            }
        }
    }

}