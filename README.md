# Elderly Care Fall Detection System

This repository contains the source code for an IoT-based Elderly Care Fall Detection System. It consists of an ESP32 hardware node that detects falls using TinyML and an Android companion app for guardians to receive alerts and manage the device.

## Project Structure

- **`/app`** & root Gradle files: The Android Companion Application (Java)
- **`/esp32_firmware/fall_detector`**: The ESP32 Arduino firmware for fall detection, ML inference, and alerts.
- **`/esp32_firmware/ESP32_Code`**: Legacy/sample ESP32 code.

## ESP32 Fall Detector
The ESP32 node uses an MPU6050/6500 IMU and a TinyML model to detect falls in real-time.

### Hardware Setup
- **ESP32 Microcontroller**
- **MPU6050 / MPU6500 IMU** (I2C: SDA=22, SCL=21)
- **SSD1306 OLED Display** (I2C)
- **Passive Buzzer** (GPIO 25)
- **RGB LED** (Red=13, Green=12, Blue=14)
- **Push Button** (GPIO 34, **Requires 10k external pull-up resistor**)

### Features
- Edge ML inference (20-feature extraction)
- Local audible alarm with OLED countdown
- Wi-Fi and WebSocket integration for app alerts
- Automatic reconnection watchdog

## Android Guardian App
The Android app allows a guardian to monitor the ESP32 node in real-time, receive fall alerts, and send medicine reminders.

### Features
- Real-time WebSocket connection to ESP32
- Firebase Cloud Firestore integration for alert management
- Batch alert dismissal ("Clear All Alerts")
- Connection stability monitoring and health checks
