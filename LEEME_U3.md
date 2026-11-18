# Estación de Riego — App Android con Firebase

App Android (Kotlin + XML + ViewBinding, minSdk 29, targetSdk 35), paquete `cl.inacap.riegoiot`.
Usa **Firebase Authentication** (correo y contraseña) y **Cloud Firestore**. El acceso se controla por rol.

## Qué cambió respecto a la versión MQTT
- Se eliminó MQTT: `MqttManager`, broker Mosquitto, certificados propios y cliente HiveMQ.
- El login usa Firebase Auth. El rol se lee desde `usuarios/{uid}.rol` en el servidor.
- Los datos viven en Firestore y las reglas de seguridad las aplica el servidor (`firestore.rules`).
- El historial de acciones pasó de `actions.txt` (local) a la colección `acciones`.
- Nueva pantalla de usuario: formulario "Solicitud de riego".
- Se mantienen `Protocolo.kt` (validación de rangos) y el bloqueo de login tras 5 intentos.

## 1. Configurar Firebase (consola)
1. Crear un proyecto en [console.firebase.google.com](https://console.firebase.google.com) y registrar la app Android `cl.inacap.riegoiot`.
2. Descargar `google-services.json` y copiarlo en `app/`. **No se sube a git** (está en `.gitignore`).
3. Authentication → Método de acceso → activar **Correo electrónico/contraseña**.
4. Crear las 3 cuentas de la tabla de abajo (Authentication → Usuarios → Agregar usuario).
5. Firestore Database → Crear base de datos.
6. Crear un documento `usuarios/{uid}` por cuenta, con el UID que muestra Authentication:
   `nombre` (texto), `correo` (texto), `rol` (`admin`, `usuario` o `estacion`).
7. Firestore → Reglas → pegar el contenido de `firestore.rules` y **Publicar**.

### Cuentas de prueba
| Correo | Rol | Qué puede hacer |
|---|---|---|
| `admin@riego.cl` | `admin` | Monitorea la estación, envía comandos, ve solicitudes e historial |
| `usuario@riego.cl` | `usuario` | Solo envía solicitudes de riego (no lee datos) |
| `estacion@riego.cl` | `estacion` | Cuenta del Arduino: escribe el estado y lee comandos (no entra a la app) |

Las contraseñas no se publican en el repositorio. Se entregan aparte.

## 2. Compilar y ejecutar
```
./gradlew assembleDebug     # APK en app/build/outputs/apk/debug/
./gradlew test              # tests locales: Protocolo, Solicitud y Conexion
```
Abrir el proyecto en Android Studio y ejecutar en un emulador o celular con internet.

## 3. Modelo de datos (Firestore)
| Colección | Escribe | Lee | Campos |
|---|---|---|---|
| `usuarios/{uid}` | nadie desde la app (consola) | el propio usuario, admin | `nombre, correo, rol` |
| `estacion/riego01` | estacion | admin | `humedad, bomba, umbral, duracion, auto, online, actualizado` |
| `comandos/{id}` | admin | estacion, admin | `tipo (REGAR/CFG), umbral, duracion, auto, creadoPor, fecha, estado` |
| `solicitudes/{id}` | usuario, admin | solo admin | `uid, nombre, sector, comentario, prioridad, fecha` |
| `acciones/{id}` | admin | admin | `uid, accion, fecha` |

## 4. Reglas de seguridad
Están en `firestore.rules`. En resumen:
- Sin sesión no se lee ni se escribe nada.
- El rol se toma de `usuarios/{uid}`, que solo se edita desde la consola: nadie puede subirse el rol desde la app.
- `usuario` solo puede **crear** solicitudes con su propio `uid` y un comentario de 200 caracteres o menos. No puede leer `solicitudes` ni `estacion`.
- `admin` solo puede crear comandos de tipo `REGAR` o `CFG`. Nadie puede borrar comandos, solicitudes ni acciones.
- Se probaron en el Rules Playground de la consola (fase 3): el usuario no lee `solicitudes` ni `estacion`, pero sí crea su solicitud y lee su propio documento.

## 5. Seguridad (ISO 27002:2022)
| Control | Cómo se aplica |
|---|---|
| 8.5 Autenticación segura | Firebase Auth con correo y contraseña (mínimo 6 caracteres). Bloqueo de 30 s tras 5 intentos fallidos, además de la protección propia de Firebase ("Demasiados intentos") |
| 8.2 / 8.3 Privilegios y acceso | Reglas de Firestore por rol, aplicadas en el servidor. La app lee el rol desde el servidor: sin rol confirmado (sin red, sin documento o rol desconocido) cierra la sesión y no da acceso. Cada pantalla protegida revisa el rol al abrirse |
| 8.12 Fuga de datos | `google-services.json`, `local.properties` y contraseñas fuera de git. El usuario no puede leer datos de otros |
| 8.24 Criptografía | Toda la comunicación con Firebase va por HTTPS/TLS. Las contraseñas las guarda Firebase con hash; la app no las almacena |
| 8.14 Disponibilidad | Caché offline de Firestore: sin red, las escrituras quedan en cola y se suben al reconectar, sin duplicarse (id de documento fijo) |
| 8.28 Codificación segura | Validación en la app (formato de correo, largo de campos, rangos de `Protocolo`) y de nuevo en las reglas (tipo de comando, largo del comentario, `uid` propio) |
| 8.15 Registro | Colección `acciones` con quién, qué y cuándo (hora del servidor) |
| 8.16 Monitoreo | Chip de conexión (Wi-Fi / Datos / Sin conexión) y estado en línea de la estación |

**Pendiente conocido:** la Fase 8 (Arduino con Firestore) es opcional y no está hecha. El sketch `arduino/riego_u3/` sigue usando MQTT, así que el monitoreo muestra "Sin datos" hasta que la estación escriba `estacion/riego01`.

## 6. Pruebas
Probado el 17-11-2026 en el emulador Android 15 (API 35), con el proyecto Firebase real. Tests locales: `./gradlew test` en verde.

| # | Prueba | Resultado esperado | Resultado |
|---|---|---|---|
| 1 | Login con correo sin formato y clave de 3 caracteres | "Ingresa un correo válido" y "La contraseña debe tener al menos 6 caracteres"; no se envía | ✅ |
| 2 | Login con clave incorrecta | "Correo o contraseña incorrectos" | ✅ |
| 3 | 5 intentos fallidos seguidos | Botón desactivado y "Bloqueado por seguridad. Reintente en N s" (30 s) | ✅ |
| 4 | Login de admin sin red | "Sin conexión. Revisa tu red e intenta de nuevo"; no da acceso | ✅ |
| 5 | Login de admin con red | Abre Monitoreo con chip Wi-Fi | ✅ |
| 6 | Monitoreo sin estación conectada | "Sin datos" en humedad, planta, bomba y actualización (sin valores falsos) | ✅ |
| 7 | Cerrar y volver a abrir la app con sesión activa | Entra directo a la pantalla de su rol | ✅ |
| 8 | Admin: "Regar ahora" (con confirmación) | "Riego manual solicitado"; aparece en el historial | ✅ |
| 9 | Admin: duración de 99 s | "La duración debe estar entre 3 y 30 segundos"; no se guarda | ✅ |
| 10 | Admin: perfil Tropical y 15 s | "Configuración guardada correctamente"; historial con Umbral 60 % y 15 s | ✅ |
| 11 | Admin: guardar configuración en modo avión | "Sin conexión: se enviará al reconectar"; al volver la red se sube una sola vez | ✅ |
| 12 | Cerrar sesión | Vuelve al login | ✅ |
| 13 | Login de usuario | Abre "Solicitud de riego" | ✅ |
| 14 | Usuario: enviar formulario vacío | "Este campo es obligatorio" en sector y comentario | ✅ |
| 15 | Usuario: enviar solicitud válida | "Solicitud enviada" y el formulario se limpia | ✅ |
| 16 | Usuario: enviar en modo avión | "Sin conexión: se enviará al reconectar"; al volver la red, "Solicitud enviada" | ✅ |
| 17 | Admin revisa solicitudes | Ve las 2 solicitudes, una vez cada una (sin duplicados) | ✅ |
| 18 | Usuario intenta leer `solicitudes` / `estacion` | Denegado por las reglas | ✅ (Rules Playground, fase 3) |
| 19 | Modo oscuro | Fondo nocturno, textos legibles en login y monitoreo | ✅ |

## 7. Guion de demo (5 min)
1. **Login admin** (`admin@riego.cl`) → Monitoreo: chip de conexión y "Sin datos" de la estación.
2. **Configuración y Control** → "Regar ahora" → confirmar. Cambiar perfil a Tropical y guardar. Mostrar el historial y, en la consola de Firebase, los documentos nuevos en `comandos` (estado `pendiente`) y `acciones`.
3. Volver a Monitoreo → sección **Solicitudes de usuarios**. Cerrar sesión.
4. **Login usuario** (`usuario@riego.cl`) → enviar el formulario vacío (errores) y luego una solicitud válida.
5. **Lectura bloqueada:** en la consola, Firestore → Reglas → Rules Playground, simular `get` en `/solicitudes/x` con el UID del usuario → "Denegado".
6. **Modo avión:** activar, enviar otra solicitud ("Sin conexión: se enviará al reconectar"), desactivar y mostrar que aparece una sola vez en la consola y en la lista del admin.
7. (Opcional) Cambiar el teléfono a modo oscuro para mostrar el fondo nocturno.
