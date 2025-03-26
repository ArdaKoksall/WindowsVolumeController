# Java Windows Volume Control 🔊

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Windows-blue.svg)]


A Java library to control the system volume on **Windows** operating systems. It leverages the command-line utility [SoundVolumeView Command-Line (svcl.exe)](https://www.nirsoft.net/utils/sound_volume_view.html#command_line) by NirSoft.

---

## ✨ Features

*   **Set Volume:** Set the volume to a specific percentage (0-100).
*   **Adjust Volume:** Increase or decrease the volume by a percentage step.
*   **Mute Control:** Mute, unmute, or toggle the mute state.
*   **Get Volume:** Retrieve the current volume percentage.
*   **Check Mute Status:** Check if the device is currently muted.
*   **Device Selection:** Target specific audio devices (`DefaultRenderDevice`, `Speakers`, `Headphones`).
*   **Logging:** Built-in optional logging using `java.util.logging`.
*   **Self-Contained (with svcl.exe):** Bundles `svcl.exe` within your application JAR and automatically extracts it to a temporary location for use.
*   **Automatic Cleanup:** The extracted `svcl.exe` is automatically deleted when the Java Virtual Machine exits.

---

## ⚠️ Prerequisites

*   **Operating System:** Microsoft Windows (`svcl.exe` is a Windows utility).
*   **Java Runtime Environment (JRE):** Version 17 or higher recommended (due to usage of `record`).

---

## ⚙️ Installation / Setup

**Add Dependency:** Include this library in your project (replace with your actual Maven/Gradle coordinates if different).

*Maven:*
```xml
<dependency>
    <groupId>com.github.ArdaKoksall</groupId> 
    <artifactId>wvc</artifactId>             
    <version>1.0.0</version>                 
</dependency>
```

## 🚀 Usage

```java
package com.github.ArdaKoksall;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Example{

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
            // Initialization happens automatically here, extracting svcl
            WindowsVolumeControl volumeControl = new WindowsVolumeControl();
            System.out.println("WindowsVolumeControl initialized.");


            // --- Example Operations ---

            // Optional: Set target device defaults to WindowsVolumeControl.AudioDevice.DEFAULT_RENDER_DEVICE
            //volumeControl.setTargetDevice(WindowsVolumeControl.AudioDevice.SPEAKERS); // Optional: Set target device

            // Get the target device
            System.out.println("Target device: " + volumeControl.getTargetDevice());

            // Disable logging for this class
            volumeControl.disableLogging();
            System.out.println("Logging disabled.");

            // Enable logging for this class
            volumeControl.enableLogging();
            System.out.println("Logging enabled.");

            // Get current volume
            double currentVolume = volumeControl.getVolume();
            System.out.println("Current volume: " + currentVolume + "%");

            // Set volume to 100%
            System.out.println("Setting volume to 100%");
            volumeControl.volumeMax();
            Thread.sleep(2000);

            // Set volume to 0%
            System.out.println("Setting volume to 0%");
            volumeControl.volumeMin();
            Thread.sleep(2000);

            // Set volume to 50%
            System.out.println("Setting volume to 50%");
            volumeControl.setVolume(50);
            Thread.sleep(2000);

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

            // Get the mute status
            boolean isMuted = volumeControl.isMuted();
            System.out.println("Is muted: " + isMuted);
            Thread.sleep(1000);

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

        } catch (InterruptedException e) {
            System.err.println("Operation interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (IllegalStateException e) {
            System.err.println("Initialization Error: " + e.getMessage());
            System.err.println("Ensure svcl.exe exists in the JAR.");
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid argument: " + e.getMessage());
        }
    }
}