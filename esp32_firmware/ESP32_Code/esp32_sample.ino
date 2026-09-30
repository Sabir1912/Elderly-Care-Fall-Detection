/*
  ESP32 Prototype Setup for Elderly Fall Detection System
  - Bluetooth Classic (SPP) for Fall Alerts & SpO2 streaming
  - WiFi WebServer for HTTP commands (trigger buzzer, get metrics)
*/

#include "BluetoothSerial.h"
#include <WiFi.h>
#include <WebServer.h>
#include <SPI.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET     -1
#define SCREEN_ADDRESS 0x3C
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

BluetoothSerial SerialBT;

// WiFi credentials (update for local network)
const char* ssid = "YOUR_WIFI_SSID";
const char* password = "YOUR_WIFI_PASSWORD";

WebServer server(80);

// Hardware Pins (Simulated)
const int BUZZER_PIN = 18;
const int FALL_SENSOR_PIN = 19; // Push button simulated fall

// Simulated Data
int spo2Value = 98;
unsigned long lastSpo2Read = 0;

void setup() {
  Serial.begin(115200);
  
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(FALL_SENSOR_PIN, INPUT_PULLUP); // LOW when pressed/fallen

  // Initialize Bluetooth
  SerialBT.begin("Elderly_ESP32_Device");
  Serial.println("Bluetooth Started. Ready to pair...");

  // Initialize WiFi
  WiFi.begin(ssid, password);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();
  Serial.print("WiFi Connected. IP Address: ");
  Serial.println(WiFi.localIP());

  // Setup Web Server Endpoints
  server.on("/api/spo2", HTTP_GET, handleSpO2);
  server.on("/api/buzzer", HTTP_POST, handleBuzzerPost);
  server.begin();
  
  // SSD1306_SWITCHCAPVCC = generate display voltage from 3.3V internally
  if(!display.begin(SSD1306_SWITCHCAPVCC, SCREEN_ADDRESS)) {
    Serial.println(F("SSD1306 allocation failed"));
  } else {
    display.clearDisplay();
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    display.setCursor(0, 0);
    display.println("Elderly Care");
    display.println("Waiting for BT...");
    display.display();
  }
}

void loop() {
  server.handleClient();

  // Handle BT Commands if received
  if (SerialBT.available()) {
    String cmd = SerialBT.readStringUntil('\n');
    cmd.trim();
    if (cmd == "BUZZER_OFF") {
      digitalWrite(BUZZER_PIN, LOW);
      SerialBT.println("ACK_BUZZER_OFF");
    } else if (cmd.startsWith("MED:")) {
      // Expected format: MED:Paracetamol:1-0-1
      String medInfo = cmd.substring(4);
      display.clearDisplay();
      display.setCursor(0,0);
      display.setTextSize(2);
      display.println("REMINDER:");
      display.setTextSize(1);
      display.println(medInfo);
      display.display();
      SerialBT.println("ACK_MED");
    } else if (cmd == "MED_CLEAR") {
      display.clearDisplay();
      display.setCursor(0,0);
      display.println("Elderly Care");
      display.display();
    }
  }

  // Simulate SpO2 reading every 5 seconds over BT
  if (millis() - lastSpo2Read > 5000) {
    lastSpo2Read = millis();
    // Varies between 95 and 99
    spo2Value = 95 + random(5);
    if(SerialBT.hasClient()) {
      SerialBT.print("SPO2:");
      SerialBT.println(spo2Value);
    }
  }

  // Detect Fall Event
  if (digitalRead(FALL_SENSOR_PIN) == LOW) {
    Serial.println("FALL DETECTED!");
    digitalWrite(BUZZER_PIN, HIGH); // Alarm locally
    if(SerialBT.hasClient()) {
      SerialBT.println("ALERT:FALL");
    }
    delay(2000); // debounce / restrict frequent sends
  }
}

// HTTP GET /api/spo2
void handleSpO2() {
  String json = "{\"spo2\": " + String(spo2Value) + "}";
  server.send(200, "application/json", json);
}

// HTTP POST /api/buzzer (expects {"action":"on"} or {"action":"off"})
void handleBuzzerPost() {
  if (server.hasArg("plain") == false) {
    server.send(400, "text/plain", "Body not received");
    return;
  }
  String body = server.arg("plain");
  if (body.indexOf("\"action\":\"on\"") > 0) {
    digitalWrite(BUZZER_PIN, HIGH);
    server.send(200, "application/json", "{\"status\":\"Buzzer ON\"}");
  } else if (body.indexOf("\"action\":\"off\"") > 0) {
    digitalWrite(BUZZER_PIN, LOW);
    server.send(200, "application/json", "{\"status\":\"Buzzer OFF\"}");
  } else {
    server.send(400, "application/json", "{\"error\":\"Invalid action\"}");
  }
}
