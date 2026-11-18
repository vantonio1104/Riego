# Estación de Riego

App Android para monitorear y controlar una estación de riego, con **Firebase Authentication** (correo y contraseña) y **Cloud Firestore**.
Proyecto de *Aplicaciones Móviles para IoT* (INACAP), hecho en Kotlin + XML + ViewBinding.

## Qué hace

| Rol | Pantalla | Puede |
|---|---|---|
| Administrador | Monitoreo + Configuración y Control | Ver la estación en vivo, enviar comandos ("Regar ahora", configuración), ver el historial y las solicitudes |
| Usuario | Solicitud de riego | Solo enviar solicitudes (no lee datos) |
| Observador | Monitoreo (solo lectura) | Solo ver el monitoreo |

- Login con validación, bloqueo tras 5 intentos y rol leído desde el servidor.
- Reglas de Firestore por rol (`firestore.rules`), con 19 pruebas automáticas en `rules-test/`.
- Funciona sin conexión: guarda en cola y sincroniza al reconectar, sin duplicar.
- Fondo propio día/noche, logo propio y fuente Nunito; modo claro y oscuro.

## Documentación

- **[LEEME_U3.md](LEEME_U3.md):** configuración de Firebase, cuentas de prueba, modelo de datos, reglas, seguridad (ISO 27002), pruebas y guion de demo.
- **[GUIA_DEFENSA.md](GUIA_DEFENSA.md):** cada indicador de la rúbrica con el archivo y la línea donde está implementado.

## Ejecutar

1. Copiar `google-services.json` del proyecto Firebase en `app/` (no se sube al repositorio).
2. Abrir el proyecto en Android Studio y ejecutar en un emulador o celular (minSdk 29).

```
./gradlew assembleDebug   # APK en app/build/outputs/apk/debug/
./gradlew test            # pruebas locales
```
