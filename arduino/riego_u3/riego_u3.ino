/*
  Estacion de Riego IoT - Unidad 3 (TI3042)
  Placa: Arduino UNO R4 WiFi | Sensor: FC-28 (A0) | Bomba: rele en D7
  Librerias: WiFiS3 (incluida en el core UNO R4) + ArduinoMqttClient (Gestor de librerias)

  Topicos (iguales a Protocolo.kt de la app):
    riego/estado  -> publica "humedad;bomba;umbral;duracion;auto" cada 2 s (retenido)
    riego/alerta  -> publica "SECO" cuando la humedad baja del umbral
    riego/online  -> "1" al conectar, "0" como ultima voluntad (LWT)
    riego/cmd     <- recibe "REGAR" o "CFG;umbral;duracion;auto"
  Los mismos comandos se pueden escribir en el Monitor Serie (115200, fin de linea "Nueva linea").
*/
#include <WiFiS3.h>
#include <ArduinoMqttClient.h>
#include "arduino_secrets.h"

// --- Hardware (calibrar con el sensor real) ---
const int PIN_SENSOR = A0;
const int PIN_BOMBA = 7;
const int ADC_SECO = 1023;     // lectura con el sensor al aire
const int ADC_MOJADO = 300;    // lectura con el sensor en agua
const bool RELE_ACTIVO_ALTO = true; // cambiar a false si el modulo rele es activo en bajo

// --- Tiempos ---
const unsigned long TICK_MS = 2000;       // lectura y publicacion
const unsigned long COOLDOWN_MS = 10000;  // espera entre riegos automaticos
const unsigned long BOMBA_MAX_MS = 30000; // corte de seguridad: la bomba nunca supera 30 s
const unsigned long REINTENTO_MS = 5000;  // reintento de conexion

const char T_ESTADO[] = "riego/estado";
const char T_ALERTA[] = "riego/alerta";
const char T_ONLINE[] = "riego/online";
const char T_CMD[] = "riego/cmd";
const int MAX_CMD = 32; // largo maximo aceptado de un comando

WiFiClient net;
MqttClient mqtt(net);

int humedad = 0;
int umbral = 30;
int duracion = 10;
bool modoAuto = true;
bool regando = false;
bool alertaEnviada = false;
unsigned long inicioRiego = 0, finRiego = 0, ultimoTick = 0, ultimoIntento = 0;

int leerHumedad() {
  int adc = analogRead(PIN_SENSOR);
  return constrain(map(adc, ADC_SECO, ADC_MOJADO, 0, 100), 0, 100);
}

void publicar(const char* topico, const char* msg, bool retenido) {
  if (!mqtt.connected()) return;
  mqtt.beginMessage(topico, retenido, 1);
  mqtt.print(msg);
  mqtt.endMessage();
}

void publicarEstado() {
  char msg[32];
  snprintf(msg, sizeof(msg), "%d;%d;%d;%d;%d", humedad, regando, umbral, duracion, modoAuto);
  publicar(T_ESTADO, msg, true);
}

void bomba(bool encender) {
  regando = encender;
  digitalWrite(PIN_BOMBA, encender == RELE_ACTIVO_ALTO ? HIGH : LOW);
  if (encender) inicioRiego = millis(); else finRiego = millis();
  Serial.println(encender ? "[BOMBA] ON" : "[BOMBA] OFF");
  publicarEstado();
}

// Lista blanca: solo acepta "REGAR" o "CFG;umbral;duracion;auto" con valores en rango.
bool procesarComando(const char* cmd, const char* origen) {
  Serial.print("[CMD ");
  Serial.print(origen);
  Serial.print("] ");
  Serial.println(cmd);

  if (strcmp(cmd, "REGAR") == 0) {
    if (!regando) bomba(true);
    return true;
  }
  int u, d, a;
  char sobra;
  if (sscanf(cmd, "CFG;%d;%d;%d%c", &u, &d, &a, &sobra) == 3 &&
      u >= 10 && u <= 90 && d >= 3 && d <= 30 && (a == 0 || a == 1)) {
    umbral = u;
    duracion = d;
    modoAuto = a;
    publicarEstado();
    return true;
  }
  Serial.println("[CMD] RECHAZADO");
  return false;
}

void alRecibirMqtt(int largo) {
  char buf[MAX_CMD + 1];
  int n = 0;
  while (mqtt.available()) {
    int c = mqtt.read();
    if (n < MAX_CMD) buf[n++] = (char)c; // descarta el exceso sin desbordar
  }
  buf[n] = '\0';
  if (largo > MAX_CMD || mqtt.messageTopic() != T_CMD) {
    Serial.println("[CMD] RECHAZADO (largo o topico)");
    return;
  }
  procesarComando(buf, "MQTT");
}

void conectar() {
  if (WiFi.status() == WL_CONNECTED && mqtt.connected()) return;
  if (millis() - ultimoIntento < REINTENTO_MS && ultimoIntento != 0) return;
  ultimoIntento = millis();
  if (regando) bomba(false); // seguridad: sin conexion no se deja la bomba encendida

  if (WiFi.status() != WL_CONNECTED) {
    Serial.print("[WIFI] Conectando a ");
    Serial.println(SECRET_SSID);
    if (WiFi.begin(SECRET_SSID, SECRET_PASS) != WL_CONNECTED) return;
    Serial.print("[WIFI] IP: ");
    Serial.println(WiFi.localIP());
  }

  mqtt.setId("estacion-riego");
  mqtt.setUsernamePassword(SECRET_MQTT_USER, SECRET_MQTT_PASS);
  mqtt.setKeepAliveInterval(10 * 1000L);
  mqtt.beginWill(T_ONLINE, true, 1); // si se corta, el broker avisa "0"
  mqtt.print("0");
  mqtt.endWill();
  if (!mqtt.connect(SECRET_BROKER, 1883)) {
    Serial.print("[MQTT] Error de conexion: ");
    Serial.println(mqtt.connectError()); // 4 o 5 = usuario/clave rechazados
    return;
  }
  mqtt.onMessage(alRecibirMqtt);
  mqtt.subscribe(T_CMD, 1);
  publicar(T_ONLINE, "1", true);
  Serial.println("[MQTT] Conectado");
  publicarEstado();
}

void setup() {
  Serial.begin(115200);
  Serial.setTimeout(50);
  pinMode(PIN_BOMBA, OUTPUT);
  digitalWrite(PIN_BOMBA, RELE_ACTIVO_ALTO ? LOW : HIGH); // bomba apagada al partir
  humedad = leerHumedad();
}

void loop() {
  conectar();
  mqtt.poll();

  // Comandos por Monitor Serie (misma validacion que MQTT)
  if (Serial.available()) {
    String linea = Serial.readStringUntil('\n');
    linea.trim();
    if (linea.length() > 0 && linea.length() <= MAX_CMD) procesarComando(linea.c_str(), "SERIAL");
  }

  unsigned long ahora = millis();

  // Fin del riego: cumplio la duracion, corte de seguridad o suelo ya humedo
  if (regando && (ahora - inicioRiego >= (unsigned long)duracion * 1000UL ||
                  ahora - inicioRiego >= BOMBA_MAX_MS || humedad >= umbral + 10)) {
    bomba(false);
  }

  if (ahora - ultimoTick >= TICK_MS) {
    ultimoTick = ahora;
    humedad = leerHumedad();
    if (humedad < umbral) {
      if (!alertaEnviada) {
        publicar(T_ALERTA, "SECO", false);
        alertaEnviada = true;
      }
      if (modoAuto && !regando && mqtt.connected() && ahora - finRiego > COOLDOWN_MS) bomba(true);
    } else {
      alertaEnviada = false;
    }
    publicarEstado();
  }
}
