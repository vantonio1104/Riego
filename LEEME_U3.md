# Unidad 3 — App Android ↔ Arduino (Wi-Fi + MQTT)

## Qué cambió respecto a la U2
- `MqttManager.kt`: se conecta a un broker Mosquitto real (cliente HiveMQ) **con TLS** (puerto 8883) y **se reconecta sola** cada 3 s si se cae la red. La simulación de la U2 sigue disponible con `SIMULADO=true`.
- `Protocolo.kt` (nuevo): formato de mensajes y validación (integridad).
- `ActivadorActivity.kt`: avisa si el comando no se pudo enviar.
- `arduino/riego_u3/`: sketch del Arduino UNO R4 WiFi.
- `broker/`: Mosquitto con TLS para la app, usuarios separados y ACL. `generar_certificados.sh` crea la CA propia.
- `app/src/main/res/raw/ca.crt`: certificado de la CA; la app solo confía en él.

## Tópicos
| Tópico | Sentido | Mensaje |
|---|---|---|
| `riego/estado` | Arduino → app | `humedad;bomba;umbral;duracion;auto` (ej. `45;0;30;10;1`) |
| `riego/alerta` | Arduino → app | `SECO` |
| `riego/online` | Arduino → app | `1` conectado, `0` caído (LWT) |
| `riego/cmd` | app → Arduino | `REGAR` o `CFG;30;10;1` |

## 1. Broker (PC)
1. Instalar Mosquitto (mosquitto.org/download).
2. Certificados: ya vienen generados en `broker/certs/`. Para regenerarlos: `bash generar_certificados.sh` en Git Bash y recompilar la app.
3. En la carpeta `broker/` crear los usuarios (personas por TLS, Arduino aparte):
   ```
   mosquitto_passwd -c -b passwd admin riego2026
   mosquitto_passwd -b passwd observador riego2026
   mosquitto_passwd -c -b passwd_estacion estacion CLAVE_ESTACION
   ```
4. Iniciar: `mosquitto -c mosquitto.conf -v`
5. Permitir los puertos 8883 (app) y 1883 (Arduino) en el firewall de Windows.

## 2. App
En `local.properties` (no se sube a git):
```
MQTT_HOST=10.0.2.2        # emulador; en celular real, la IP del PC
SIMULADO=false            # true = demo sin broker ni Arduino
```
Usuarios: `admin` / `riego2026` (controla) y `observador` / `riego2026` (solo mira).

## 3. Arduino
1. Gestor de librerías: instalar **ArduinoMqttClient**.
2. Completar `arduino_secrets.h` (red Wi-Fi, IP del PC, clave de `estacion`).
3. Calibrar `ADC_SECO`, `ADC_MOJADO` y `RELE_ACTIVO_ALTO` con el hardware real.
4. Monitor Serie a 115200: muestra IP, comandos y rechazos. También acepta `REGAR` y `CFG;...` escritos a mano.

## 4. Pruebas sin Arduino (el PC hace de Arduino)
```
# Ver los comandos que manda la app
mosquitto_sub -h localhost -u estacion -P CLAVE_ESTACION -t riego/cmd -v

# Simular la estación
mosquitto_pub -h localhost -u estacion -P CLAVE_ESTACION -t riego/online -m 1 -r
mosquitto_pub -h localhost -u estacion -P CLAVE_ESTACION -t riego/estado -m "45;0;30;10;1" -r
mosquitto_pub -h localhost -u estacion -P CLAVE_ESTACION -t riego/alerta -m SECO
```
| Prueba | Resultado esperado |
|---|---|
| Login con clave mala | "Usuario o contraseña incorrectos"; bloqueo tras 5 intentos |
| Publicar estado `45;0;30;10;1` | Monitoreo muestra 45 %, bomba detenida, "En línea" |
| Publicar `999;0;30;10;1` | La app lo ignora (integridad) |
| Publicar alerta `SECO` | Snackbar + notificación "Planta seca" |
| Admin: "Regar ahora" | `mosquitto_sub` muestra `riego/cmd REGAR` |
| Admin: "Guardar configuración" | `mosquitto_sub` muestra `riego/cmd CFG;30;10;1` |
| Observador publica en `riego/cmd` | El broker lo rechaza (log: "Denied PUBLISH") |
| `riego/online` = `0` | La app muestra "Fuera de línea" |
| Detener y reiniciar Mosquitto | La app pasa a "Sin conexión" y vuelve sola a "Wi-Fi (MQTT)" |
| `admin` intenta entrar por el 1883 | El broker lo rechaza: las personas solo entran por TLS |

## Seguridad (ISO 27002:2022)
| Control | Cómo se aplica |
|---|---|
| 8.5 Autenticación segura | Usuario y clave validados por el broker; bloqueo de login tras 5 intentos |
| 8.2 / 8.3 Privilegios y acceso | ACL: el observador no puede enviar comandos |
| 8.12 Fuga de datos | Credenciales en `arduino_secrets.h` y `local.properties`; claves privadas y `passwd` fuera de git (`broker/.gitignore`) |
| 8.24 Criptografía | TLS 1.2+ entre app y broker; la app solo confía en la CA del proyecto (usuario, clave y datos van cifrados) |
| 8.20 Seguridad de redes | Solo red local WPA2, sin acceso anónimo; las personas solo por el puerto TLS |
| 8.28 Codificación segura | Lista blanca de comandos, rangos y largo máximo en Arduino y app |
| 8.15 Registro | Monitor Serie (Arduino), log `-v` (broker), historial de acciones (app) |
| 8.14 Disponibilidad | App con reconexión automática; Arduino reintenta cada 5 s y avisa caídas con LWT |
| Seguridad física | La bomba se corta a los 30 s o si se pierde la conexión |

**Riesgo conocido:** el tramo Arduino ↔ broker (puerto 1883) sigue sin cifrar dentro de la red local; solo viajan lecturas y comandos de riego, sin datos personales. Mejora futura: cargar `ca.crt` en el R4 y usar `WiFiSSLClient` en el 8883.

**Atajo documentado:** la app no verifica el nombre del host del certificado, para no atarlo a la IP del PC. La CA privada igual impide suplantar el broker. Con IP fija se puede activar.
