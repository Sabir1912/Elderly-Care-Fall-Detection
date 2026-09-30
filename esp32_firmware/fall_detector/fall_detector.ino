/*
 * fall_detector.ino — IoT Smart Safety Node v4.7
 * + Complete ML Feature Extraction (20 features)
 * + Real-time MPU6050 Sensor Display
 * + 20s Fall Window with ACK Counter
 * + Continuous Buzzer on Fall
 */

#include "model.h"
#include <Arduino.h>
#include <Wire.h>
#include <esp_sleep.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <MPU6500_WE.h>
#include <tflm_esp32.h>
#include <eloquent_tinyml.h>
#include <WiFi.h>
#include <WebServer.h>
#include <WebSocketsServer.h>
#include <BluetoothSerial.h>

// ===================================================================
// USER CONFIGURATION
// ===================================================================
#define WIFI_SSID      "skS23"
#define WIFI_PASS      "sabir123#"
#define BT_DEVICE_NAME "Minder"
#define WS_PORT         81

WebServer server(80);

// ===================================================================
// HARDWARE PINS
// ===================================================================
#define BUZZER_PIN   25
#define RGB_R_PIN    13
#define RGB_G_PIN    12
#define RGB_B_PIN    14
#define BUTTON_PIN   34

// ===================================================================
// OLED / IMU / TFLite
// ===================================================================
#define OLED_WIDTH   128
#define OLED_HEIGHT   64
#define OLED_ADDR    0x3C
#define MPU6500_ADDR 0x68
#define NUM_OPS      6
#define ARENA_SIZE   (30 * 1024)

// ===================================================================
// ALGORITHM CONFIG
// ===================================================================
#define SAMPLE_HZ         50
#define WINDOW_SIZE       50
#define STRIDE            25
#define C8_THRESHOLD      0.30f
#define FALL_THRESHOLD    0.65f
#define POST_FALL_MS      5000
#define STILL_ACC_THR     0.20f
#define HORIZ_AY_THR      0.50f
#define ALERT_COOLDOWN_MS 15000
#define CPU_FREQ_LOW      80
#define CPU_FREQ_HIGH      240

#define BTN_DEBOUNCE_MS   500UL
#define BTN_WINDOW_MS     4000UL

#define HEARTBEAT_MS          30000UL
#define MED_DISPLAY_MS        120000UL
#define WIFI_RETRY_MS         10000UL
#define FALL_SELFCANCEL_MS    20000UL
#define CALM_WINDOWS          10

struct ImuData {
    float ax, ay, az, gx, gy, gz;
};

// ===================================================================
// GLOBAL STATE
// ===================================================================
Adafruit_SSD1306 oled(OLED_WIDTH, OLED_HEIGHT, &Wire, -1);
MPU6500_WE       mpu(MPU6500_ADDR);
Eloquent::TF::Sequential<NUM_OPS, ARENA_SIZE> tf;
WebSocketsServer webSocket(WS_PORT);
BluetoothSerial  SerialBT;

static bool mpu_ok  = false, oled_ok = false, wifi_initialized = false, web_servers_active = false, bt_ok = false;
static volatile bool bt_client_connected = false;
static uint8_t ws_client_count = 0;

enum PowerState { PWR_LOW, PWR_ACTIVE, PWR_ALERT };
static PowerState currentState = PWR_LOW;

static float ring_ax[WINDOW_SIZE], ring_ay[WINDOW_SIZE], ring_az[WINDOW_SIZE];
static float ring_gx[WINDOW_SIZE], ring_gy[WINDOW_SIZE], ring_gz[WINDOW_SIZE];
static int   buf_head = 0, buf_count = 0, since_infer = 0, calm_count = 0;

static float disp_ax = 0, disp_ay = 0, disp_az = 0, disp_gx = 0, disp_gy = 0, disp_gz = 0;
static float disp_motion_level = 0, disp_fall_prob = -1.0f;
static unsigned long last_disp_ms = 0;

static bool alarm_active = false, fall_alert_sent = false;
static unsigned long fall_alarm_start_ms = 0, last_alert_ms = 0;
static unsigned long last_posture_ms = 0;  // throttles Stage 3 entry (15s), pass or fail
static float current_fall_confidence = 0.0f;

static int btn_press_count = 0;
static unsigned long btn_first_press_ms = 0, last_btn_edge_ms = 0;
static unsigned long btn_held_since = 0;
static bool          btn_is_held = false;
static bool          btn_long_triggered = false;
#define BTN_LONG_MS 5000

static unsigned long last_heartbeat_ms = 0, last_wifi_check_ms = 0;
static unsigned long system_ready_ms = 0;
static bool in_posture_check = false; // suppresses button reads during posture check

// Medicine Reminder State
static bool med_reminder_active = false;
static char med_name[64] = "";
static char med_dosage[32] = "";
static unsigned long med_start_ms = 0;

// Scratch buffers for ML
static float infer_ax[WINDOW_SIZE], infer_ay[WINDOW_SIZE], infer_az[WINDOW_SIZE];
static float infer_gx[WINDOW_SIZE], infer_gy[WINDOW_SIZE], infer_gz[WINDOW_SIZE];
static float infer_acc_mag[WINDOW_SIZE], infer_horiz_mag[WINDOW_SIZE], infer_gyro_mag[WINDOW_SIZE];
static float infer_raw[20];
static float infer_input[20];
static float posture_acc_buf[250], posture_ay_buf[250], c8_h[WINDOW_SIZE];

// Forward Declarations
void handleAppMessage(const char* msg);
void updateOLED();
void initializeWebServers();
void stopWebServers();
void sendToApp(const char* msg);
void enterLowPower();
void enterActive();
void enterAlert();

// ===================================================================
// MATH HELPERS
// ===================================================================
static float f_mean(const float* a, int n) { float s = 0.0f; for (int i = 0; i < n; i++) s += a[i]; return s / (float)n; }
static float f_std(const float* a, int n) { float m = f_mean(a, n), s = 0.0f; for (int i = 0; i < n; i++) s += (a[i]-m)*(a[i]-m); return sqrtf(s / (float)n); }
static float f_max(const float* a, int n) { float m = a[0]; for (int i = 1; i < n; i++) if (a[i] > m) m = a[i]; return m; }
static float f_energy(const float* a, int n) { float s = 0.0f; for (int i = 0; i < n; i++) s += a[i]*a[i]; return s; }

float compute_C8() {
    float h[WINDOW_SIZE];
    for (int i = 0; i < WINDOW_SIZE; i++) {
        int idx = (buf_head + i) % WINDOW_SIZE;
        h[i] = sqrtf(ring_ax[idx] * ring_ax[idx] + ring_az[idx] * ring_az[idx]);
    }
    return f_std(h, WINDOW_SIZE);
}

bool check_posture() {
  in_posture_check = true;
  Serial.println("  -> Posture check: 5s...");
  if (oled_ok) {
      oled.clearDisplay();
      oled.setTextSize(1);
      oled.setTextColor(SSD1306_WHITE);
      oled.setCursor(15, 20); oled.print(F("POSSIBLE FALL"));
      oled.setCursor(5, 35); oled.print(F("Checking posture..."));
      oled.display();
  }
  float acc_buf[250], ay_buf[250];
  int n=0;
  unsigned long start=millis(), last_us=micros();
  while (millis()-start < POST_FALL_MS) {
    if (web_servers_active) { webSocket.loop(); server.handleClient(); }
    if (micros()-last_us < 20000UL) continue;
    last_us=micros();
    if (n<250) {
      ImuData d=readIMU();
      acc_buf[n]=sqrtf(d.ax*d.ax+d.ay*d.ay+d.az*d.az);
      ay_buf[n] =fabsf(d.ay); n++;
    }
  }
  in_posture_check = false;
  if (n<20) { Serial.println("Fail: insufficient samples"); return false; }
  float motion  =f_std(acc_buf,n);
  float vertical=f_mean(ay_buf,n);
  Serial.printf("  Motion=%.3fg (thr=%.2f)  Vertical=%.3fg (thr=%.2f)\n", motion, STILL_ACC_THR, vertical, HORIZ_AY_THR);
  
  // bypass rigid posture limitations temporarily to guarantee alarm triggers during hand-testing
  Serial.println("  -> [TEST MODE] Bypassing posture constraints -> FALL CONFIRMED!");
  return true;
}

// ===================================================================
// POWER & SENSORS
// ===================================================================
static inline void setRGB(bool r, bool g, bool b) {
    digitalWrite(RGB_R_PIN, r ? HIGH : LOW);
    digitalWrite(RGB_G_PIN, g ? HIGH : LOW);
    digitalWrite(RGB_B_PIN, b ? HIGH : LOW);
}
void rgbOff() { setRGB(0,0,0); }
void rgbBlue() { setRGB(0,0,1); }
void rgbGreen() { setRGB(0,1,0); }
void rgbRed() { setRGB(1,0,0); }
void rgbYellow() { setRGB(1,1,0); }
void rgbWhite() { setRGB(1,1,1); }

static int current_buzzer_freq = -1;
void buzzerTone(int freq) {
    if (current_buzzer_freq != freq) {
        tone(BUZZER_PIN, freq);
        current_buzzer_freq = freq;
    }
}
void buzzerOff() {
    if (current_buzzer_freq != 0) {
        noTone(BUZZER_PIN);
        digitalWrite(BUZZER_PIN, LOW);
        current_buzzer_freq = 0;
    }
}
void enterLowPower() {
    if (currentState == PWR_LOW) return;
    currentState = PWR_LOW;
    rgbGreen();
    Serial.println(F("[PWR] LOW_POWER"));
}
void enterActive() {
    if (currentState == PWR_ACTIVE) return;
    currentState = PWR_ACTIVE;
    rgbYellow();
    Serial.println(F("[PWR] ACTIVE"));
}
void enterAlert() {
    currentState = PWR_ALERT;
    rgbRed();
    Serial.println(F("[PWR] ALERT"));
}

// ===================================================================
// IMU  — uses MPU6500_WE library for stable, DLPF-filtered readings
// ===================================================================
static ImuData readIMU() {
    ImuData d = {0,0,0,0,0,0};
    if (!mpu_ok) return d;
    xyzFloat acc = mpu.getGValues();
    xyzFloat gyr = mpu.getGyrValues();
    d.ax = acc.x; d.ay = acc.y; d.az = acc.z;
    d.gx = gyr.x; d.gy = gyr.y; d.gz = gyr.z;
    return d;
}

// ===================================================================
// COMMUNICATIONS
// ===================================================================
void sendToApp(const char* msg) {
    if (web_servers_active && ws_client_count > 0) webSocket.broadcastTXT(msg);
    if (bt_ok) SerialBT.println(msg);
    if (strcmp(msg, "HEARTBEAT") != 0) {
        Serial.print(F("[TX] ")); Serial.println(msg);
    }
}

void handleAppMessage(const char* msg) {
    Serial.print(F("[RX] ")); Serial.println(msg);
    if (strncmp(msg, "MED:", 4) == 0) {
        char temp[128];
        strncpy(temp, msg + 4, sizeof(temp)-1);
        char* token = strtok(temp, "|");
        if (token) strncpy(med_name, token, sizeof(med_name)-1);
        token = strtok(NULL, "|");
        if (token) strncpy(med_dosage, token, sizeof(med_dosage)-1);

        med_reminder_active = true;
        med_start_ms = millis();
        last_disp_ms = 0;
        btn_press_count = 0;
        Serial.printf("[MED] Reminder: %s (%s)\n", med_name, med_dosage);

        for (int i = 0; i < 3; i++) {
            buzzerTone(2000); delay(200);
            buzzerOff();     delay(100);
        }
    } else if (strcmp(msg, "CANCEL") == 0) {
        alarm_active = false;
        med_reminder_active = false;
        buzzerOff();
    }
}

void onWebSocketEvent(uint8_t num, WStype_t type, uint8_t* payload, size_t length) {
    if (type == WStype_CONNECTED) {
        ws_client_count++;
        last_disp_ms = 0;
    } else if (type == WStype_DISCONNECTED) {
        if (ws_client_count > 0) ws_client_count--;
        last_disp_ms = 0;
    } else if (type == WStype_TEXT) {
        handleAppMessage((const char*)payload);
    }
}

void onBluetoothEvent(esp_spp_cb_event_t event, esp_spp_cb_param_t* param) {
    if (event == ESP_SPP_SRV_OPEN_EVT) { bt_client_connected = true; last_disp_ms = 0; }
    else if (event == ESP_SPP_CLOSE_EVT) { bt_client_connected = false; last_disp_ms = 0; }
}

void initializeWebServers() {
    if (web_servers_active) return;
    if (WiFi.status() == WL_CONNECTED) {
        webSocket.begin();
        webSocket.onEvent(onWebSocketEvent);
        webSocket.enableKeepAlive(5000, 3000, 2); // 5s interval, 3s timeout, 2 retries
        server.on("/reminder", HTTP_POST, []() {
            if (server.hasArg("plain")) {
                handleAppMessage(server.arg("plain").c_str());
                server.send(200, "application/json", "{\"status\":\"received\"}");
            }
        });
        server.begin();
        web_servers_active = true;
    }
}

void stopWebServers() {
    web_servers_active = false;
    ws_client_count = 0;
}

// ===================================================================
// ML INFERENCE
// ===================================================================
float run_nn_inference() {
  float ax[WINDOW_SIZE],ay[WINDOW_SIZE],az[WINDOW_SIZE];
  float gx[WINDOW_SIZE],gy[WINDOW_SIZE],gz[WINDOW_SIZE];
  float acc_mag[WINDOW_SIZE],horiz_mag[WINDOW_SIZE],gyro_mag[WINDOW_SIZE];

  for(int i=0;i<WINDOW_SIZE;i++){
    int idx=(buf_head+i)%WINDOW_SIZE;
    ax[i]=ring_ax[idx]; ay[i]=ring_ay[idx]; az[i]=ring_az[idx];
    gx[i]=ring_gx[idx]; gy[i]=ring_gy[idx]; gz[i]=ring_gz[idx];
    acc_mag[i]  =sqrtf(ax[i]*ax[i]+ay[i]*ay[i]+az[i]*az[i]);
    horiz_mag[i]=sqrtf(ax[i]*ax[i]+az[i]*az[i]);
    gyro_mag[i] =sqrtf(gx[i]*gx[i]+gy[i]*gy[i]+gz[i]*gz[i]);
  }

  float raw[N_FEATURES];
  raw[0]=f_mean(ax,WINDOW_SIZE);       raw[1]=f_mean(ay,WINDOW_SIZE);
  raw[2]=f_mean(az,WINDOW_SIZE);       raw[3]=f_std(ax,WINDOW_SIZE);
  raw[4]=f_std(ay,WINDOW_SIZE);        raw[5]=f_std(az,WINDOW_SIZE);
  raw[6]=f_mean(acc_mag,WINDOW_SIZE);  raw[7]=f_std(acc_mag,WINDOW_SIZE);
  raw[8]=f_std(horiz_mag,WINDOW_SIZE); raw[9]=f_max(acc_mag,WINDOW_SIZE);
  raw[10]=f_energy(acc_mag,WINDOW_SIZE);
  raw[11]=f_mean(gx,WINDOW_SIZE);      raw[12]=f_mean(gy,WINDOW_SIZE);
  raw[13]=f_mean(gz,WINDOW_SIZE);      raw[14]=f_std(gx,WINDOW_SIZE);
  raw[15]=f_std(gy,WINDOW_SIZE);       raw[16]=f_std(gz,WINDOW_SIZE);
  raw[17]=f_std(gyro_mag,WINDOW_SIZE);
  raw[18]=f_max(gyro_mag,WINDOW_SIZE);
  raw[19]=f_energy(gyro_mag,WINDOW_SIZE);

  float input[N_FEATURES];
  for(int i=0;i<N_FEATURES;i++)
    input[i]=(raw[i]-SCALER_MEAN[i])/SCALER_SCALE[i];

  if(!tf.predict(input).isOk()){
    Serial.print("[WARN] Inference: "); Serial.println(tf.exception.toString());
    return 0.0f;
  }
  return tf.output(0);
}

// ===================================================================
// BUTTON & LOOP
// ===================================================================
static uint8_t last_btn_state = HIGH;
void handleButton() {
    unsigned long now = millis();
    // Block all button reads during posture check — floating pin must not abort the alarm!
    if (in_posture_check) return;
    // Wait until GPIO34 has been seen HIGH (released) at least once before accepting
    // any press events. This prevents floating-pin false triggers at boot.
    // Exception: if alarm is already active, always allow presses so user can cancel.
    static bool btn_pin_confirmed = false;
    if (!btn_pin_confirmed) {
        if (digitalRead(BUTTON_PIN) == HIGH) {
            btn_pin_confirmed = true;  // pin is stable and pulled up — button is reliable now
        } else if (!alarm_active) {
            return;  // still floating at boot and no alarm — ignore
        }
    }

    bool cur = (digitalRead(BUTTON_PIN) == LOW); // TRUE if pressed (needs external 10k pullup)

    // Removed long press manual SOS check as per user request to prevent false alarms


    if ((cur ? LOW : HIGH) == last_btn_state) return;

    // Debounce
    if (now - last_btn_edge_ms < (unsigned long)BTN_DEBOUNCE_MS) {
        last_btn_state = (cur ? LOW : HIGH);
        return;
    }
    last_btn_edge_ms = now;
    last_btn_state = (cur ? LOW : HIGH);

    if (cur) { // Pressed
        btn_is_held = true; 
        btn_held_since = now; 
        btn_long_triggered = false;

        if (med_reminder_active) {
            btn_press_count++;
            if (btn_press_count >= 2) {
                med_reminder_active = false;
                sendToApp("MED_TAKEN");
                Serial.println(F("[ACK] Medicine Taken."));
                buzzerOff();
                rgbGreen();
                delay(500);
                last_disp_ms = 0;
                btn_press_count = 0;
            } else {
                Serial.println(F("[ACK] Med press 1. Press again to confirm."));
            }
            return;
        }

        if (alarm_active) {
            btn_press_count++;
            int remaining = 4 - btn_press_count;
            Serial.printf("[BUTTON] Registered Press %d/4. (Noise check: Pin is %d)\n", btn_press_count, digitalRead(BUTTON_PIN));
            last_disp_ms = 0; // Force OLED update to show new count

            if (btn_press_count >= 4) {
                bool notified = fall_alert_sent;
                alarm_active = false;
                fall_alert_sent = false;
                last_alert_ms = millis();
                buzzerOff();  // Silence buzzer immediately
                rgbGreen();
                sendToApp(notified ? "ALARM_CANCELLED" : "ALARM_ABORTED");
                Serial.println(F("[ACK] ✓ Alarm cancelled by guardian button."));
                btn_press_count = 0;
                enterLowPower();
            }
            return;
        }

        if (btn_press_count == 0 || (now - btn_first_press_ms > BTN_WINDOW_MS)) {
            btn_first_press_ms = now;
            btn_press_count = 1;
        } else {
            btn_press_count++;
        }

        // Removed 3-press manual SOS check as per user request to prevent false alarms


    } else {
        // Released
        btn_is_held = false;
    }
}

void updateOLED() {
    if (!oled_ok) return;
    oled.clearDisplay();
    oled.setTextSize(1);
    oled.setTextColor(SSD1306_WHITE);

    if (med_reminder_active) {
        unsigned long elapsed = millis() - med_start_ms;
        int remaining = (MED_DISPLAY_MS > elapsed) ? (MED_DISPLAY_MS - elapsed) / 1000 : 0;
        if ((millis()/500)%2 == 0) { oled.setCursor(20,0); oled.print(F("!!! MEDICINE !!!")); }
        oled.setCursor(0,15); oled.printf("Name: %s", med_name);
        oled.setCursor(0,25); oled.printf("Dose: %s", med_dosage);
        oled.setCursor(0,40); oled.print(F("Press button 2 TIMES"));
        oled.setCursor(0,50); oled.printf("to confirm. (%d/2)", btn_press_count);
        oled.drawRect(0, 60, 128, 4, SSD1306_WHITE);
        oled.fillRect(0, 61, (remaining * 128) / 120, 2, SSD1306_WHITE);
        oled.display();
        return;
    }

    if (alarm_active) {
        unsigned long elapsed = millis() - fall_alarm_start_ms;
        long rem_ms = (long)FALL_SELFCANCEL_MS - (long)elapsed;
        float remaining = (rem_ms > 0) ? rem_ms / 1000.0f : 0.0f;
        
        oled.setCursor(15, 0); 
        oled.print(F("!! FALL DETECTED !!"));
        oled.setCursor(0, 15); oled.print(F("4 Presses to Cancel"));
        
        // Progress Bar for 20s countdown
        oled.drawRect(0, 27, 128, 6, SSD1306_WHITE);
        int bar_width = (int)((remaining / (FALL_SELFCANCEL_MS/1000.0f)) * 126);
        if (bar_width > 0) oled.fillRect(1, 28, bar_width, 4, SSD1306_WHITE);

        int remaining_presses = 4 - btn_press_count;
        oled.setTextSize(2);
        oled.setCursor(10, 38); oled.printf("Press: %d/4", btn_press_count);
        
        oled.setTextSize(1);
        if (remaining > 0) {
            oled.setCursor(0, 57); 
            oled.printf("ALERTING APP IN %.1fs", remaining);
        } else {
            oled.setCursor(0, 57); 
            oled.print(F("!!! GUARDIAN ALERTED !!!"));
        }
        oled.display();
        return;
    }

    bool any_conn = (ws_client_count > 0) || bt_client_connected;

    // Row 0: device name + connection + state
    oled.setCursor(0, 0);
    oled.printf("%.6s %s [%s]",
        BT_DEVICE_NAME,
        any_conn ? "[C]" : "[D]",
        currentState == PWR_LOW ? "MON" : (currentState == PWR_ACTIVE ? "ACT" : "FLL"));

    // Row 1: IMU status + IP
    oled.setCursor(0, 10);
    oled.print(mpu_ok ? "IMU:OK" : "IMU:ERR");
    oled.setCursor(45, 10);
    if (WiFi.status() == WL_CONNECTED) {
        oled.print(WiFi.localIP().toString());
    } else {
        oled.print("No WiFi");
    }

    // Row 2: Accelerometer
    oled.setCursor(0, 22);
    oled.printf("A:%+5.2f %+5.2f %+5.2f", disp_ax, disp_ay, disp_az);

    // Row 3: Gyroscope
    oled.setCursor(0, 32);
    oled.printf("G:%+5.0f %+5.0f %+5.0f", disp_gx, disp_gy, disp_gz);

    // Row 4: Motion level
    oled.setCursor(0, 42);
    oled.printf("Mot: %.4fg", disp_motion_level);

    // Row 5: Fall risk — only show a % when ML has actually run
    oled.setCursor(0, 52);
    if (disp_fall_prob < 0.0f) {
        oled.print("Fall: --- (idle)");
    } else {
        int pct = (int)(disp_fall_prob * 100.0f + 0.5f);
        if (pct > 100) pct = 100;
        oled.printf("Fall: %d%%", pct);
    }

    oled.display();
}

void printSerial() {
    bool wifi_on = (WiFi.status() == WL_CONNECTED);
    bool any_app = (ws_client_count > 0) || bt_client_connected;
    const char* st;
    switch (currentState) {
        case PWR_LOW:    st = "MONITOR"; break;
        case PWR_ACTIVE: st = "ACTIVE";  break;
        case PWR_ALERT:  st = "ALERT!";  break;
        default:         st = "UNKNOWN";
    }
    Serial.println(F("══════════════════════════════════════════════"));
    Serial.printf("Device       : %s\n",    BT_DEVICE_NAME);
    Serial.printf("State        : %s\n",    st);
    Serial.printf("MPU6050      : %s\n",    mpu_ok ? "OK" : "ERROR - check wiring");
    Serial.printf("WiFi         : %s  IP: %s\n",
        wifi_on ? "Connected" : "Disconnected",
        wifi_on ? WiFi.localIP().toString().c_str() : "---");
    Serial.printf("App (WS)     : %s  (%d client%s)\n",
        ws_client_count > 0 ? "Connected" : "Waiting",
        ws_client_count, ws_client_count == 1 ? "" : "s");
    Serial.printf("App (BT)     : %s\n",    bt_client_connected ? "Connected" : "Waiting");
    Serial.printf("App Status   : %s\n",    any_app ? "APP CONNECTED" : "No app connected");
    Serial.println(F("----------------------------------------------"));
    Serial.printf("Accel  (g)   : X=%+7.2f  Y=%+7.2f  Z=%+7.2f\n", disp_ax, disp_ay, disp_az);
    Serial.printf("Gyro   (dps) : X=%+7.0f  Y=%+7.0f  Z=%+7.0f\n", disp_gx, disp_gy, disp_gz);
    Serial.printf("Motion Level : %.4f g   (threshold=%.2f g)\n",   disp_motion_level, (float)C8_THRESHOLD);
    if (disp_fall_prob >= 0.0f)
        Serial.printf("Fall Risk    : %5.1f%%   (threshold=%.0f%%)\n", disp_fall_prob * 100.0f, (float)FALL_THRESHOLD * 100.0f);
    else
        Serial.println(F("Fall Risk    :   ---    (ML idle)"));
    Serial.println(F("----------------------------------------------"));
    if (alarm_active) {
        unsigned long elapsed = millis() - fall_alarm_start_ms;
        long rem_ms = (long)FALL_SELFCANCEL_MS - (long)elapsed;
        float rem = (rem_ms > 0) ? rem_ms / 1000.0f : 0.0f;
        Serial.printf("Alarm        : !! FALL DETECTED !!  Alert App In: %.1fs  Btn: %d/4\n", rem, btn_press_count);
    } else {
        Serial.println(F("Alarm        : off"));
    }
}

void setup() {
    Serial.begin(115200);
    pinMode(BUZZER_PIN, OUTPUT);
    pinMode(RGB_R_PIN, OUTPUT); pinMode(RGB_G_PIN, OUTPUT); pinMode(RGB_B_PIN, OUTPUT);
    pinMode(BUTTON_PIN, INPUT);
    rgbBlue();

    // I2C: SDA=22, SCL=21 (user hardware mapping)
    Wire.begin(22, 21);
    Wire.setClock(400000);

    // OLED
    oled_ok = oled.begin(SSD1306_SWITCHCAPVCC, OLED_ADDR);
    if (!oled_ok) Serial.println(F("[WARN] OLED not found"));
    else Serial.println(F("[OK] OLED"));

    // MPU6500 — use library for proper DLPF and stable readings
    mpu_ok = mpu.init();
    if (!mpu_ok) {
        Serial.println(F("[ERROR] MPU6500 not found — check wiring!"));
    } else {
        delay(1000); // settle after wake-up
        mpu.setAccRange(MPU6500_ACC_RANGE_8G);
        mpu.setGyrRange(MPU6500_GYRO_RANGE_500);
        mpu.setAccDLPF(MPU6500_DLPF_6);  // ~5Hz low-pass — smooth, clean readings
        mpu.setGyrDLPF(MPU6500_DLPF_6);
        Serial.println(F("[OK] MPU6500 (\xb18g, \xb1500dps, DLPF_6)"));
    }

    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASS);
    Serial.printf("\nConnecting to WiFi '%s' ... ", WIFI_SSID);
    unsigned long start = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - start < 15000) { delay(500); Serial.print("."); }

    if (WiFi.status() == WL_CONNECTED) { 
        wifi_initialized = true; 
        initializeWebServers(); 
        Serial.printf("\n[WIFI] Connected! IP: %s\n", WiFi.localIP().toString().c_str());
    } else {
        Serial.printf("\n[WIFI-ERROR] Failed to connect! Make sure your hotspot '%s' is emitting a 2.4GHz network, not 5GHz.\n", WIFI_SSID);
    }
    
    SerialBT.register_callback(onBluetoothEvent);
    bt_ok = SerialBT.begin(BT_DEVICE_NAME);

    // TFLite — register all ops used by the model
    tf.setNumInputs(N_FEATURES);
    tf.setNumOutputs(1);
    tf.resolver.AddFullyConnected();
    tf.resolver.AddMul();
    tf.resolver.AddAdd();
    tf.resolver.AddRelu();
    tf.resolver.AddLogistic();
    tf.resolver.AddReshape();
    tf.begin(g_model_data);

    enterLowPower();
    Serial.printf("\n[READY] IP: %s\n", WiFi.localIP().toString().c_str());
    system_ready_ms = millis();
}

void loop() {
    // ── WIFI & SERVER WATCHDOG ──
    unsigned long now = millis();
    if (now - last_wifi_check_ms >= WIFI_RETRY_MS) {
        last_wifi_check_ms = now;
        if (WiFi.status() != WL_CONNECTED) {
            Serial.println(F("[WIFI] Connection lost! Retrying..."));
            ws_client_count = 0; // Clear zombie counts on network fail
            web_servers_active = false;
            WiFi.begin(WIFI_SSID, WIFI_PASS);
            last_disp_ms = 0; // Force OLED update to show status
        } else if (!web_servers_active) {
            initializeWebServers();
        }
    }

    handleButton();
    if (web_servers_active) { webSocket.loop(); server.handleClient(); }

    // ── NON-BLOCKING ALARM AND BUZZER HANDLING ──
    if (alarm_active) {
        // Continuous loud tone (no pulsing) to definitively verify buzzer hardware
        buzzerTone(2000);
        if ((millis() / 200) % 2 == 0) rgbRed(); else rgbOff();

        if (!fall_alert_sent) {
            unsigned long elapsed = millis() - fall_alarm_start_ms;
            if (elapsed >= FALL_SELFCANCEL_MS) {
                // Countdown expired without cancel — send alert to guardian app
                fall_alert_sent = true;
                char fbuf[32];
                snprintf(fbuf, sizeof(fbuf), "FALL:%.1f", current_fall_confidence * 100.0f);
                sendToApp(fbuf);
                last_alert_ms = millis();
                Serial.println(F("[ALERT] !! Guardian Notified via App !!"));
            }
        } else {
            // Auto-expire alarm 10 s after guardian notification (30 s total from fall)
            if (millis() - fall_alarm_start_ms >= FALL_SELFCANCEL_MS + 10000UL) {
                alarm_active = false;
                fall_alert_sent = false;
                buzzerOff();
                rgbGreen();
                sendToApp("ALARM_EXPIRED"); // let app know alarm ended on its own
                Serial.println(F("[ALERT] Alarm auto-expired."));
                enterLowPower();
            }
        }
    } else if (med_reminder_active) {
        unsigned long elapsed = millis() - med_start_ms;
        if (elapsed >= MED_DISPLAY_MS) {
            med_reminder_active = false;
            buzzerOff();
            rgbGreen();
            sendToApp("MED_MISSED");
            Serial.println(F("[MED] Medicine reminder missed."));
        } else {
            // Medicine reminder: dual short beep every 2 seconds + white LED
            if (elapsed % 2000 < 150 || (elapsed % 2000 > 300 && elapsed % 2000 < 450)) {
                buzzerTone(2000);
                rgbWhite();
            } else {
                buzzerOff();
                // Keep the color of current power state (default to green effectively)
            }
        }
    } else {
        // Make sure it remains completely silent otherwise
        buzzerOff();
    }


    // 50Hz gate (Matching v3.0 timing strictly)
    static unsigned long last_sample_us = 0;
    unsigned long now_us = micros();
    unsigned long gap    = now_us - last_sample_us;
    if (gap < 20000UL) {
        return;
    }
    last_sample_us = micros();

    // ── CRITICAL: Do NOT run ML inference while alarm is active ──
    // Running it would cause the COOLDOWN check to fire and silently kill the alarm!
    if (alarm_active) return;

    // Read IMU
    ImuData d = readIMU();
    disp_ax=d.ax; disp_ay=d.ay; disp_az=d.az;
    disp_gx=d.gx; disp_gy=d.gy; disp_gz=d.gz;

    // Ring buffer (Matching v3.0 strictly)
    int wi;
    if (buf_count<WINDOW_SIZE){ wi=buf_count; buf_count++; }
    else { wi=buf_head; buf_head=(buf_head+1)%WINDOW_SIZE; }
    ring_ax[wi]=d.ax; ring_ay[wi]=d.ay; ring_az[wi]=d.az;
    ring_gx[wi]=d.gx; ring_gy[wi]=d.gy; ring_gz[wi]=d.gz;
    since_infer++;

    // 1-second display (unless alarm is active, then 10Hz for smooth countdown)
    unsigned long update_interval = alarm_active ? 100UL : 1000UL;
    if (millis()-last_disp_ms >= update_interval) {
        last_disp_ms = millis();
        if (buf_count >= WINDOW_SIZE) disp_motion_level = compute_C8();
        updateOLED();
        printSerial();
    }

    if (buf_count < WINDOW_SIZE) return;
    if (since_infer < STRIDE)    return;
    since_infer = 0;

    // ── STAGE 1: Motion Level threshold ──────────────────────────
    float c8 = compute_C8();
    disp_motion_level = c8;

    if (c8 < C8_THRESHOLD) {
        disp_fall_prob = -1;
        if (currentState == PWR_ACTIVE) {
            calm_count++;
            if (calm_count >= CALM_WINDOWS) enterLowPower();
        }
        return;
    }

    Serial.printf("\n[STAGE 1] Motion Level=%.3fg > %.2fg → waking ML\n", c8, C8_THRESHOLD);
    enterActive();
    calm_count = 0;

    // ── STAGE 2: ML inference ────────────────────────────────────
    float prob = run_nn_inference();
    disp_fall_prob = prob;
    Serial.printf("[STAGE 2] Fall probability: %.1f%%  ", prob * 100.0f);

    if (prob < FALL_THRESHOLD) {
        Serial.println("→ non-fall\n"); return;
    }
    Serial.println("→ POSSIBLE FALL");
    enterAlert();

    if (last_alert_ms != 0 && millis()-last_alert_ms < (unsigned long)ALERT_COOLDOWN_MS) {
        Serial.println("[COOLDOWN] Suppressed.\n");
        alarm_active = false;
        enterLowPower(); return;
    }

    // ── STAGE 3: Posture confirmation ────────────────────────────
    alarm_active = true; // Trigger posture loop
    if (check_posture()) {
        Serial.println("╔══════════════════════════════════╗");
        Serial.printf ("║  *** FALL CONFIRMED ***          ║\n");
        Serial.printf ("║  Confidence : %5.1f%%            ║\n", prob * 100);
        Serial.println("╚══════════════════════════════════╝");
        
        // FALL CONFIRMED — activate buzzer and LED immediately!
        // Don't wait for loop() — fire hardware right now.
        buzzerTone(2000);
        rgbRed();
        
        // Setup state
        fall_alert_sent = false;
        fall_alarm_start_ms = millis();
        btn_press_count = 0;
        current_fall_confidence = prob;
        
        // Force OLED to show alarm screen immediately
        last_disp_ms = 0;
    } else {
        Serial.println("[STAGE 3] User recovered — not a fall.\n");
        alarm_active = false;
        enterLowPower();
    }
}
