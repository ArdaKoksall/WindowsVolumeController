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
 * Controls Windows system volume using the bundled svcl.exe (SoundVolumeView Command-Line).
 *
 * <p>This class extracts svcl.exe from the JAR resources to a temporary
 * location upon initialization and uses it to execute volume commands for a selected audio device.</p>
 *
 * <p>The target device can be changed using {@link #setTargetDevice(AudioDevice)}.</p>
 *
 * <p><b>Prerequisites:</b> Must be run on a Windows operating system.</p>
 */
public class WindowsVolumeControl {

    private static final Logger LOGGER = Logger.getLogger(WindowsVolumeControl.class.getName());
    private static final String TOOL_RESOURCE_NAME = "/svcl.exe";
    private static final String TOOL_NAME = "svcl";

    private static final String extractedToolPath;

    private static boolean isLoggingEnabled = false;

    /**
     * Represents the target audio devices for volume control.
     * Note: "Speakers" and "Headphones" assume devices with these exact names exist.
     * svcl.exe might require exact device names as shown in SoundVolumeView. Use "/GetPercent" without /Stdout manually to check device names.
     */
    public enum AudioDevice {
        DEFAULT_RENDER_DEVICE("DefaultRenderDevice"),
        SPEAKERS("Speakers"),
        HEADPHONES("Headphones");

        private final String deviceString;

        AudioDevice(String deviceString) {
            this.deviceString = deviceString;
        }

        /**
         * Gets the string representation of the device used in svcl.exe commands.
         * @return The device string.
         */
        public String getDeviceString() {
            return deviceString;
        }
    }

    private AudioDevice currentTargetDevice = AudioDevice.DEFAULT_RENDER_DEVICE;

    static {
        try {
            extractedToolPath = extractTool();
            if (isLoggingEnabled) {
                LOGGER.log(Level.INFO, "{0} extracted successfully to: {1}", new Object[]{TOOL_NAME.toUpperCase(), extractedToolPath});
            }


            Path tempPath = Path.of(extractedToolPath);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.deleteIfExists(tempPath);
                    if (isLoggingEnabled) {
                        LOGGER.log(Level.INFO, "Temporary {0} file deleted: {1}", new Object[]{TOOL_NAME.toUpperCase(), tempPath});
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to delete temporary " + TOOL_NAME.toUpperCase() + " file: " + tempPath, e);
                }
            }));

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "FATAL: Failed to extract " + TOOL_RESOURCE_NAME + " from resources. Volume control will not work.", e);
            throw new IllegalStateException("Could not initialize WindowsVolumeControl: Failed to extract " + TOOL_NAME + ".exe", e);
        } catch (SecurityException e) {
            LOGGER.log(Level.SEVERE, "FATAL: Security restrictions prevent extracting or executing " + TOOL_NAME + ".exe.", e);
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
     * Sets the target audio device for subsequent volume control actions.
     *
     * @param device The audio device to target.
     * @throws NullPointerException if device is null.
     */
    public void setTargetDevice(AudioDevice device) {
        if (device == null) {
            throw new NullPointerException("Target AudioDevice cannot be null.");
        }
        this.currentTargetDevice = device;
        if (isLoggingEnabled) {
            LOGGER.log(Level.INFO, "Target audio device set to: {0}", device.getDeviceString());
        }
    }

    /**
     * Gets the currently set target audio device.
     *
     * @return The current target AudioDevice.
     */
    public AudioDevice getTargetDevice() {
        return this.currentTargetDevice;
    }

    /**
     * Extracts the command-line tool (svcl.exe) from the classpath resources to a temporary file.
     *
     * @return The absolute path to the extracted executable file.
     * @throws IOException If the resource cannot be found or copied.
     * @throws SecurityException If file operations are restricted.
     */
    private static String extractTool() throws IOException, SecurityException {
        InputStream toolStream = WindowsVolumeControl.class.getResourceAsStream(TOOL_RESOURCE_NAME);
        if (toolStream == null) {
            throw new IOException("Cannot find '" + TOOL_RESOURCE_NAME + "' in classpath resources.");
        }

        Path tempFile = Files.createTempFile(TOOL_NAME + "-", ".exe");

        if (isLoggingEnabled) {
            LOGGER.log(Level.FINE, "Extracting {0} resource to temporary file: {1}", new Object[]{TOOL_NAME.toUpperCase(), tempFile.toAbsolutePath()});
        }

        try (OutputStream out = Files.newOutputStream(tempFile);
             InputStream in = toolStream) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            try { Files.deleteIfExists(tempFile); } catch (IOException ignore) {}
            throw e;
        }

        return tempFile.toAbsolutePath().toString();
    }

    /**
     * Sets the volume to a specific percentage (0-100) for the current target device.
     * Uses the command: svcl.exe /SetVolume "[DeviceName]" [percentage]
     * If an error occurs during execution, it is logged as SEVERE.
     *
     * @param percentage The desired volume level (0 to 100).
     * @throws IllegalArgumentException If percentage is outside the 0-100 range.
     * @throws IllegalStateException if the command-line tool could not be initialized.
     */
    public void setVolume(int percentage) {
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("Percentage must be between 0 and 100.");
        }
        try {
            executeTool(false, "/SetVolume", currentTargetDevice.getDeviceString(), String.valueOf(percentage));
            if (isLoggingEnabled) {
                LOGGER.log(Level.INFO, "Volume set to {0}% for {1}", new Object[]{percentage, currentTargetDevice.getDeviceString()});
            }
        } catch (IOException | InterruptedException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to set volume to " + percentage + "% for " + currentTargetDevice.getDeviceString() + " using " + TOOL_NAME, e);
        }
    }

    /**
     * Sets the volume to the maximum level (100%) for the current target device.
     * Uses the command: svcl.exe /SetVolume "[DeviceName]" 100
     * If an error occurs during execution, it is logged as SEVERE.
     *
     * @throws IllegalStateException if the command-line tool could not be initialized.
     */
    public void volumeMax() {
        try {
            executeTool(false, "/SetVolume", currentTargetDevice.getDeviceString(), "100");
            if (isLoggingEnabled) {
                LOGGER.log(Level.INFO, "Volume set to {0}% for {1}", new Object[]{100, currentTargetDevice.getDeviceString()});
            }
        } catch (IOException | InterruptedException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to set volume to " + 100 + "% for " + currentTargetDevice.getDeviceString() + " using " + TOOL_NAME, e);
        }
    }

    /**
     * Sets the volume to the minimum level (0%) for the current target device.
     * Uses the command: svcl.exe /SetVolume "[DeviceName]" 0
     * If an error occurs during execution, it is logged as SEVERE.
     *
     * @throws IllegalStateException if the command-line tool could not be initialized.
     */
    public void volumeMin() {
        try {
            executeTool(false, "/SetVolume", currentTargetDevice.getDeviceString(), "0");
            if (isLoggingEnabled) {
                LOGGER.log(Level.INFO, "Volume set to {0}% for {1}", new Object[]{0, currentTargetDevice.getDeviceString()});
            }
        } catch (IOException | InterruptedException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to set volume to " + 0 + "% for " + currentTargetDevice.getDeviceString() + " using " + TOOL_NAME, e);
        }
    }

    /**
     * Increases the volume by a specific percentage step for the current target device.
     * Uses the command: svcl.exe /ChangeVolume "[DeviceName]" +[percentageStep]
     * If an error occurs during execution, it is logged as SEVERE.
     *
     * @param percentageStep The percentage points (positive) to increase the volume by.
     * @throws IllegalArgumentException If percentageStep is negative.
     * @throws IllegalStateException if the command-line tool could not be initialized.
     */
    public void increaseVolume(int percentageStep) {
        if (percentageStep < 0) {
            throw new IllegalArgumentException("Percentage step must be non-negative for increase.");
        }
        try {
            executeTool(false, "/ChangeVolume", currentTargetDevice.getDeviceString(), "+" + percentageStep);
            if (isLoggingEnabled) {
                LOGGER.log(Level.INFO, "Volume increased by {0}% for {1}", new Object[]{percentageStep, currentTargetDevice.getDeviceString()});
            }
        } catch (IOException | InterruptedException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to increase volume by " + percentageStep + "% for " + currentTargetDevice.getDeviceString() + " using " + TOOL_NAME, e);
        }
    }

    /**
     * Decreases the volume by a specific percentage step for the current target device.
     * Uses the command: svcl.exe /ChangeVolume "[DeviceName]" -[percentageStep]
     * If an error occurs during execution, it is logged as SEVERE.
     *
     * @param percentageStep The percentage points (positive) to decrease the volume by.
     * @throws IllegalArgumentException If percentageStep is negative.
     * @throws IllegalStateException if the command-line tool could not be initialized.
     */
    public void decreaseVolume(int percentageStep) {
        if (percentageStep < 0) {
            throw new IllegalArgumentException("Percentage step must be non-negative for decrease.");
        }
        try {
            executeTool(false, "/ChangeVolume", currentTargetDevice.getDeviceString(), "-" + percentageStep);
            if (isLoggingEnabled) {
                LOGGER.log(Level.INFO, "Volume decreased by {0}% for {1}", new Object[]{percentageStep, currentTargetDevice.getDeviceString()});
            }
        } catch (IOException | InterruptedException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to decrease volume by " + percentageStep + "% for " + currentTargetDevice.getDeviceString() + " using " + TOOL_NAME, e);
        }
    }

    /**
     * Mutes the volume for the current target device.
     * Uses the command: svcl.exe /Mute "[DeviceName]"
     * If an error occurs during execution, it is logged as SEVERE.
     *
     * @throws IllegalStateException if the command-line tool could not be initialized.
     */
    public void mute() {
        try {
            executeTool(false, "/Mute", currentTargetDevice.getDeviceString());
            if (isLoggingEnabled) {
                LOGGER.log(Level.INFO, "Volume muted for {0}", new Object[]{currentTargetDevice.getDeviceString()});
            }
        } catch (IOException | InterruptedException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to mute volume for " + currentTargetDevice.getDeviceString() + " using " + TOOL_NAME, e);
        }
    }

    /**
     * Unmutes the volume for the current target device.
     * Uses the command: svcl.exe /Unmute "[DeviceName]"
     * If an error occurs during execution, it is logged as SEVERE.
     *
     * @throws IllegalStateException if the command-line tool could not be initialized.
     */
    public void unmute() {
        try {
            executeTool(false, "/Unmute", currentTargetDevice.getDeviceString());
            if (isLoggingEnabled) {
                LOGGER.log(Level.INFO, "Volume unmuted for {0}", new Object[]{currentTargetDevice.getDeviceString()});
            }
        } catch (IOException | InterruptedException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to unmute volume for " + currentTargetDevice.getDeviceString() + " using " + TOOL_NAME, e);
        }
    }

    /**
     * Toggles the mute state of the volume for the current target device.
     * Uses the command: svcl.exe /Switch "[DeviceName]"
     * If an error occurs during execution, it is logged as SEVERE.
     *
     * @throws IllegalStateException if the command-line tool could not be initialized.
     */
    public void toggleMute() {
        try {
            executeTool(false, "/Switch", currentTargetDevice.getDeviceString());
            if (isLoggingEnabled) {
                LOGGER.log(Level.INFO, "Volume mute toggled for {0}", new Object[]{currentTargetDevice.getDeviceString()});
            }
        } catch (IOException | InterruptedException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to toggle mute state for " + currentTargetDevice.getDeviceString() + " using " + TOOL_NAME, e);
        }
    }

    /**
     * Gets the current volume percentage (0-100) for the current target device.
     * Uses the command: svcl.exe /Stdout /GetPercent "[DeviceName]"
     * If an error occurs during execution or parsing, it is logged as SEVERE and -1.0 is returned.
     *
     * @return The current volume percentage (0.0-100.0), or -1.0 if the volume could not be retrieved or parsed.
     * @throws IllegalStateException if the command-line tool could not be initialized.
     */
    public double getVolume() {
        try {
            String rawOutput = executeTool(true, "/Stdout", "/GetPercent", currentTargetDevice.getDeviceString());

            if (rawOutput == null || rawOutput.trim().isEmpty()) {
                LOGGER.log(Level.WARNING, "{0} /Stdout /GetPercent returned empty or null output for {1}.", new Object[]{TOOL_NAME.toUpperCase(), currentTargetDevice.getDeviceString()});
                return -1.0;
            }

            double percentage = Double.parseDouble(rawOutput.trim());

            if (percentage < 0 || percentage > 100) {
                LOGGER.log(Level.WARNING, "{0} /GetPercent returned value outside expected range [0, 100] for {1}: {2}", new Object[]{TOOL_NAME.toUpperCase(), currentTargetDevice.getDeviceString(), percentage});
                percentage = Math.max(0.0, Math.min(100.0, percentage));
            }

            if (isLoggingEnabled) {
                LOGGER.log(Level.INFO, "Current volume retrieved via {0} for {1}: {2}%", new Object[]{TOOL_NAME.toUpperCase(), currentTargetDevice.getDeviceString(), percentage});
            }
            return percentage;

        } catch (IOException | InterruptedException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to get current volume for " + currentTargetDevice.getDeviceString() + " using " + TOOL_NAME, e);
            return -1.0;
        } catch (NumberFormatException e) {
            LOGGER.log(Level.SEVERE, "Failed to parse volume percentage output from " + TOOL_NAME + " for " + currentTargetDevice.getDeviceString() + ". Output: " + e.getMessage(), e);
            return -1.0;
        }
    }

    /**
     * Checks if the current target device is muted.
     * Uses the command: svcl.exe /Stdout /GetMute "[DeviceName]"
     * If an error occurs during execution or parsing, it is logged as SEVERE and false is returned.
     *
     * @return true if the device is muted, false otherwise or on error.
     * @throws IllegalStateException if the command-line tool could not be initialized.
     */
    public boolean isMuted() {
        try {
            String rawOutput = executeTool(true, "/Stdout", "/GetMute", currentTargetDevice.getDeviceString());

            if (rawOutput == null || rawOutput.trim().isEmpty()) {
                LOGGER.log(Level.WARNING, "{0} /Stdout /GetMute returned empty or null output for {1}.", new Object[]{TOOL_NAME.toUpperCase(), currentTargetDevice.getDeviceString()});
                return false;
            }

            boolean muted = "1".equals(rawOutput.trim());

            if (isLoggingEnabled) {
                LOGGER.log(Level.INFO, "Mute status retrieved via {0} for {1}: {2}", new Object[]{TOOL_NAME.toUpperCase(), currentTargetDevice.getDeviceString(), muted});
            }
            return muted;

        } catch (IOException | InterruptedException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Failed to get mute status for " + currentTargetDevice.getDeviceString() + " using " + TOOL_NAME, e);
            return false;
        }
    }

    /**
     * Checks if the command-line tool executable path was successfully determined during initialization.
     * @throws IllegalStateException if path is null (initialization failed).
     */
    private void checkToolReady() throws IllegalStateException {
        if (extractedToolPath == null) {
            throw new IllegalStateException(TOOL_NAME.toUpperCase() + " executable path is not available. Initialization likely failed. Check logs.");
        }
    }

    /**
     * Executes the command-line tool, optionally capturing the first line of its standard output.
     * Standard error is consumed and logged.
     *
     * @param captureOutput If true, captures and returns the first line of stdout. If false, consumes stdout via StreamGobbler and returns null.
     * @param args Command and arguments for the tool (e.g., "/SetVolume", "DefaultRenderDevice", "50").
     * @return The first line of standard output if captureOutput is true, otherwise null.
     * @throws IOException If an I/O error occurs during process execution or the tool returns non-zero exit code.
     * @throws InterruptedException If the current thread is interrupted while waiting.
     * @throws IllegalStateException if the tool path is null.
     */
    private String executeTool(boolean captureOutput, String... args) throws IOException, InterruptedException, IllegalStateException {
        checkToolReady();

        List<String> commandList = new ArrayList<>();
        commandList.add(extractedToolPath);
        commandList.addAll(Arrays.asList(args));

        ProcessBuilder processBuilder = new ProcessBuilder(commandList);
        String commandString = String.join(" ", commandList);

        if (isLoggingEnabled) {
            LOGGER.log(Level.FINE, "Executing {0}{1}: {2}", new Object[]{
                    TOOL_NAME.toUpperCase(),
                    (captureOutput ? " (Capture)" : ""),
                    commandString
            });
        }

        Process process = null;
        String outputLine = null;

        try {
            process = processBuilder.start();

            StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), "ERROR", isLoggingEnabled);
            new Thread(errorGobbler).start();

            if (captureOutput) {
                try (InputStream stdout = process.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(stdout))) {

                    int exitCode = process.waitFor();

                    if (exitCode != 0) {
                        LOGGER.log(Level.WARNING, "{0} command finished with non-zero exit code: {1}. Command: {2}",
                                new Object[]{TOOL_NAME.toUpperCase(), exitCode, commandString});
                        throw new IOException(TOOL_NAME.toUpperCase() + " command failed with exit code " + exitCode + " for command: " + commandString);
                    } else {
                        if (isLoggingEnabled) {
                            LOGGER.log(Level.FINE, "{0} command finished successfully (exit code 0).", TOOL_NAME.toUpperCase());
                        }
                        outputLine = reader.readLine();
                        if (isLoggingEnabled) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                LOGGER.log(Level.FINEST, "{0}_OUTPUT> (extra) {1}", new Object[]{TOOL_NAME.toUpperCase(), line});
                            }
                        }
                    }
                }
            } else {
                StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), "OUTPUT", isLoggingEnabled);
                new Thread(outputGobbler).start();

                int exitCode = process.waitFor();

                if (exitCode != 0) {
                    LOGGER.log(Level.WARNING, "{0} command finished with non-zero exit code: {1}. Command: {2}",
                            new Object[]{TOOL_NAME.toUpperCase(), exitCode, commandString});
                    throw new IOException(TOOL_NAME.toUpperCase() + " command failed with exit code " + exitCode);
                } else {
                    if (isLoggingEnabled) {
                        LOGGER.log(Level.FINE, "{0} command finished successfully (exit code 0).", TOOL_NAME.toUpperCase());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error executing " + TOOL_NAME.toUpperCase() + " command: " + commandString, e);
            throw e;
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, TOOL_NAME.toUpperCase() + " execution interrupted: " + commandString, e);
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }

        if (captureOutput && isLoggingEnabled) {
            LOGGER.log(Level.FINEST, "{0} raw output captured: {1}", new Object[]{TOOL_NAME.toUpperCase(), outputLine});
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