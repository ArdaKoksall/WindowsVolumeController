# Java Windows Volume Control 🔊

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) 
[![Platform](https://img.shields.io/badge/Platform-Windows-blue.svg)]

A simple Java library to control the master system volume on **Windows** operating systems. It leverages the powerful command-line utility [NirCmd](https://www.nirsoft.net/utils/nircmd.html) by NirSoft.

---

## ✨ Features

*   **Set Volume:** Set the system volume to a specific percentage (0-100).
*   **Adjust Volume:** Increase or decrease the volume by a percentage step.
*   **Mute Control:** Mute, unmute, or toggle the system mute state.
*  **Logging:** Built-in logging for debugging and monitoring.
*   **Self-Contained (with NirCmd):** Bundles `nircmd.exe` within your application JAR and automatically extracts it to a temporary location for use.
*   **Automatic Cleanup:** The extracted `nircmd.exe` is automatically deleted when the Java Virtual Machine exits.

---

## ⚠️ Prerequisites

*   **Operating System:** Microsoft Windows (NirCmd is a Windows utility).
*   **Java Runtime Environment (JRE):** Version 8 or higher recommended.


---

## ⚙️ Installation / Setup
 
**Add Dependency:** Include this library in your project.

    <dependency>
        <groupId>com.github.ArdaKoksall</groupId>
        <artifactId>wvc</artifactId>
        <version>3.0.0</version>
    </dependency>

---

## 🚀 Usage

```java
import com.github.ArdaKoksall.WindowsVolumeControl;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VolumeDemo {

    // Optional: Configure logging to see debug/info messages
    static {
        // Example: Set logging level to INFO to see basic operations
        Logger rootLogger = Logger.getLogger(""); // Get root logger
        rootLogger.setLevel(Level.INFO);
        // You might want to add a handler if none exists, e.g., ConsoleHandler
        // java.util.logging.ConsoleHandler handler = new java.util.logging.ConsoleHandler();
        // handler.setLevel(Level.ALL);
        // rootLogger.addHandler(handler);

        // Specifically for this library if needed
        Logger volumeControlLogger = Logger.getLogger(WindowsVolumeControl.class.getName());
        volumeControlLogger.setLevel(Level.INFO); // Or FINE, FINEST for more detail
    }

    public static void main(String[] args) {
        try {
            // Initialization happens automatically here, extracting NirCmd
            WindowsVolumeControl volumeControl = new WindowsVolumeControl();
            System.out.println("WindowsVolumeControl initialized.");

            // --- Example Operations ---
            
            // Disable logging for this class
            volumeControl.disableLogging();
            
            // Enable logging for this class
            volumeControl.enableLogging();
            
            // Get current volume
            int currentVolume = volumeControl.getVolume();
            
            // Set volume to 50%
            System.out.println("Setting volume to 50%");
            volumeControl.setVolume(50);
            Thread.sleep(2000); // Pause to observe

            // Increase volume by 10%
            System.out.println("Increasing volume by 10%");
            volumeControl.increaseVolume(10);
            Thread.sleep(2000);

            // Decrease volume by 20%
            System.out.println("Decreasing volume by 20%");
            volumeControl.decreaseVolume(20);
            Thread.sleep(2000);

            // Mute the volume
            System.out.println("Muting volume");
            volumeControl.mute();
            Thread.sleep(2000);

            // Unmute the volume
            System.out.println("Unmuting volume");
            volumeControl.unmute();
            Thread.sleep(2000);

            // Toggle mute (will mute again)
            System.out.println("Toggling mute (will mute)");
            volumeControl.toggleMute();
            Thread.sleep(2000);

            // Toggle mute (will unmute)
            System.out.println("Toggling mute (will unmute)");
            volumeControl.toggleMute();
            Thread.sleep(1000);

            System.out.println("Volume control demonstration finished.");

        } catch (IOException e) {
            System.err.println("Error executing volume command: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.err.println("Operation interrupted: " + e.getMessage());
            Thread.currentThread().interrupt(); // Restore interrupt status
        } catch (IllegalStateException e) {
            System.err.println("Initialization Error: " + e.getMessage());
            System.err.println("Ensure nircmd.exe is in src/main/resources and you are on Windows.");
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid argument: " + e.getMessage());
        }
    }
}