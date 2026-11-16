# Plan: migrar Estación de Riego de MQTT a Firebase

## Contexto
- App Android "Estación de Riego" (Kotlin + XML + ViewBinding, minSdk 29, targetSdk 35), paquete `cl.inacap.riegoiot`.
- Hoy usa MQTT (HiveMQ + Mosquitto). Hay que **sacar MQTT** y usar **Firebase Authentication (correo/contraseña) + Cloud Firestore**.
- Se evalúa con una rúbrica de 14 indicadores (Android + Firebase). Cada fase indica qué indicador cubre.
- El Arduino UNO R4 WiFi no existe físicamente. Su parte va al final y es opcional.

## Reglas para Claude Code
1. Trabaja **una fase a la vez**. Al terminar cada fase: compila (`./gradlew assembleDebug`), corre los tests (`./gradlew test`), resume qué cambió y **espera mi aprobación** antes de seguir.
2. Cambio mínimo que cumpla la fase. Sin abstracciones extra, sin MVVM nuevo, sin Compose. Mantén XML + ViewBinding + Activities.
3. Textos de UI en `strings.xml`, en español.
4. No inventes versiones de librerías: usa Firebase BoM y el plugin `com.google.gms.google-services` en versiones estables compatibles con el AGP del proyecto. Si dudas, pregúntame.
5. Si un paso necesita la consola de Firebase, **dímelo como tarea manual** y espera.
6. `google-services.json` NO se sube a git (está en `.gitignore`). Yo lo pongo en `app/` a mano.
7. Haz commit al terminar cada fase aprobada. No hagas push sin que te lo pida.

## Decisiones tomadas
- **Roles:** `admin` y `usuario` (y `estacion` para el Arduino).
  - Admin: monitorea la estación, envía comandos y ve todos los registros.
  - Usuario: **solo ingresa información** (formulario "Solicitud de riego"). No lee nada de Firestore salvo su propio doc de rol.
- Firestore con caché offline (viene activa por defecto en Android).
- La Fase 8 (Arduino) es opcional y va al final.

## Modelo de datos (Firestore)
| Colección | Escribe | Lee | Campos |
|---|---|---|---|
| `usuarios/{uid}` | nadie desde la app (se crea en la consola) | el propio usuario, admin | `nombre, correo, rol` |
| `estacion/riego01` | estacion | admin | `humedad, bomba, umbral, duracion, auto, online, actualizado` |
| `comandos/{id}` | admin | estacion, admin | `tipo (REGAR/CFG), umbral, duracion, auto, creadoPor, fecha, estado (pendiente/ejecutado)` |
| `solicitudes/{id}` | usuario, admin | solo admin | `uid, nombre, sector, comentario, prioridad, fecha` |
| `acciones/{id}` | admin | admin | `uid, accion, fecha` (reemplaza `actions.txt`) |

## Fases

### Fase -1: Montar el repo (antes de todo)
- El repo está recién creado y vacío. El proyecto viene aparte en `Estacion_Riego_U3_entrega.zip`.
- Descomprime el zip y deja **el contenido de la carpeta `Estacion_Riego/`** en la raíz del repo (`app/`, `arduino/`, `broker/`, `gradle/`, `build.gradle.kts`, `settings.gradle.kts`, `gradlew`, etc.). No dejes una carpeta extra `Estacion_Riego/` adentro.
- Deja `PLAN_FIREBASE.md` en la raíz.
- Revisa el `.gitignore` de la raíz. Debe incluir como mínimo: `local.properties`, `build/`, `.gradle/`, `.idea/`, `*.apk`, `app/google-services.json`, `broker/certs/*.key`, `broker/passwd*`.
- `gradlew` debe tener permiso de ejecución (`chmod +x gradlew`).
- Primer commit con el estado original: `estado U3 con MQTT (antes de Firebase)`. Así se puede volver atrás.
- Desde aquí: **un commit por fase aprobada** (`fase N: ...`).
- Listo cuando: el repo tiene el proyecto en la raíz y el commit inicial.

### Fase 0: Firebase (TAREA MANUAL, la hago yo)
- Crear el proyecto en la consola de Firebase y registrar la app `cl.inacap.riegoiot`. Poner `google-services.json` en `app/`.
- Activar Authentication → Correo/contraseña.
- Crear 3 cuentas: admin, usuario y estación. Crear Firestore.
- Crear `usuarios/{uid}` con `rol` para cada cuenta.
- Claude Code: solo dame esta lista y espera a que confirme.

### Fase 1: Sacar MQTT y agregar Firebase
- Borrar: `MqttManager.kt`, la carpeta `broker/`, `res/raw/ca.crt`, la dependencia de HiveMQ y los excludes de netty en `packaging`. Borrar también el `buildConfigField` `MQTT_HOST` y `SIMULADO`.
- Borrar los restos de la plantilla: `com/example/estacion_riego/MainActivity.kt`, `res/layout/activity_main.xml` y los tests de `com.example`.
- Agregar Firebase BoM, `firebase-auth`, `firebase-firestore`, el plugin `google-services` y `kotlinx-coroutines-play-services` (para `.await()`).
- **Mantener** `Protocolo.kt` (`cmdConfig` y validación de rangos) y `ProtocoloTest`.
- Las pantallas que dependían de `MqttManager` deben compilar aunque queden vacías por ahora.
- Listo cuando: compila y `ProtocoloTest` pasa.

### Fase 2: Login, roles y sesión (3.1.1.1, 3.1.1.3, 3.1.3.9, 3.1.4.13)
- `LoginActivity`: valida el formato del correo (`Patterns.EMAIL_ADDRESS`) y una contraseña de 6 caracteres o más. Usa `signInWithEmailAndPassword`. Mantén el bloqueo tras 5 intentos.
- Después del login, lee `usuarios/{uid}.rol`. Si es `admin` abre `AdminActivity` (la actual `MonitoreoActivity` + `ActivadorActivity`); si es `usuario` abre `UsuarioActivity` (nueva).
- Si el rol no se puede leer (sin red, sin doc o rol desconocido): `signOut()` y un mensaje. **Nunca dar acceso sin rol confirmado.**
- Al abrir la app con una sesión activa (`currentUser != null`), lee el rol y redirige.
- Cada pantalla protegida revisa el rol en `onCreate`. Si no corresponde, `finish()` y vuelve al login.
- Botón "Cerrar sesión" en las pantallas de admin y de usuario.
- Mensajes distintos para: credenciales malas, sin red, demasiados intentos y cuenta sin rol.
- Borrar `UserRoleManager` (el rol por prefijo del nombre de usuario).
- Listo cuando: admin entra a Admin, usuario entra a Usuario, una clave mala muestra un mensaje y entrar sin red no da acceso.

### Fase 3: Reglas de Firestore (3.1.4.11, 3.1.4.12)
- Crear `firestore.rules` en la raíz con:
```
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    function rol() { return get(/databases/$(db)/documents/usuarios/$(request.auth.uid)).data.rol; }
    function es(r) { return request.auth != null && rol() == r; }

    match /usuarios/{uid}   { allow read: if request.auth != null && (request.auth.uid == uid || es('admin'));
                              allow write: if false; }
    match /estacion/{id}    { allow read: if es('admin'); allow write: if es('estacion'); }
    match /comandos/{id}    { allow create: if es('admin') && request.resource.data.tipo in ['REGAR','CFG'];
                              allow read, update: if es('estacion') || es('admin');
                              allow delete: if false; }
    match /solicitudes/{id} { allow create: if (es('usuario') || es('admin'))
                                && request.resource.data.uid == request.auth.uid
                                && request.resource.data.comentario is string
                                && request.resource.data.comentario.size() <= 200;
                              allow read: if es('admin');
                              allow update, delete: if false; }
    match /acciones/{id}    { allow read, create: if es('admin'); allow update, delete: if false; }
  }
}
```
- TAREA MANUAL: pegarlas en la consola y probar en el Rules Playground que el usuario NO puede leer `solicitudes` ni `estacion`. Sacar capturas.

### Fase 4: Pantalla Usuario: formulario (3.1.1.2, 3.1.2.5, 3.1.2.7)
- `UsuarioActivity` con el formulario "Solicitud de riego": EditText `sector` (obligatorio, máx. 40), EditText `comentario` (obligatorio, máx. 200), selector de `prioridad` (Baja/Media/Alta), 3 o más TextView (título, etiquetas, estado) y botón Enviar.
- Validar antes de enviar. Mostrar los errores en el `TextInputLayout`.
- Guardar sin duplicar: `val ref = db.collection("solicitudes").document()` y luego `ref.set(...)`. En un reintento se reutiliza el mismo `ref` hasta que el envío salga bien.
- `fecha` con `FieldValue.serverTimestamp()`.
- Listo cuando: el registro aparece en la consola y un formulario incompleto no se envía.

### Fase 5: Pantalla Admin (3.1.2.6, 3.1.2.7)
- Monitoreo: `addSnapshotListener` sobre `estacion/riego01`. Reutilizar el layout actual de monitoreo.
- **Sin datos falsos:** mientras no llegue el documento, mostrar "Sin datos" (eliminar el 45 % inicial).
- "Regar ahora" y "Guardar configuración" (validar con `Protocolo.cmdConfig`) crean un doc en `comandos` (estado `pendiente`) y otro en `acciones`.
- El historial lee `acciones` (ordenado por fecha desc, límite 20). Eliminar `ActionLogger` y `actions.txt`.
- Nueva sección o pantalla "Solicitudes": RecyclerView con `solicitudes` ordenadas por fecha desc.
- Listo cuando: el admin ve las solicitudes del usuario y sus comandos quedan guardados.

### Fase 6: Errores y conectividad (3.1.1.4, 3.1.4.13)
- Detectar escrituras pendientes. Si no hay red, mostrar: "Sin conexión: se enviará al reconectar".
- Diferenciar los errores de `FirebaseFirestoreException` (`UNAVAILABLE` → sin red, `PERMISSION_DENIED` → sin permiso) y de `FirebaseAuthException`.
- Botón "Reintentar" cuando falla una carga.
- Chip de conexión en Admin (con `ConnectivityManager`).
- Listo cuando: con el modo avión se guarda la solicitud, aparece en la consola al volver la red y no queda duplicada.

### Fase 7: Interfaz (3.1.3.8, 3.1.3.10)
- Imagen de fondo en el login (y opcionalmente en las demás pantallas) con buena legibilidad (overlay o tarjeta).
- Una imagen propia adicional (logo de la app), no un ícono `@android:drawable`.
- Una fuente en `res/font/` (ej. Google Font descargada como `.ttf`) aplicada a 2 o más textos (títulos).
- Revisar que se vea bien en modo claro y oscuro.

### Fase 8 (opcional): Arduino con Firestore
- Actualizar `arduino/riego_u3/riego_u3.ino`: `WiFiSSLClient` + REST.
  - Login: `POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=API_KEY` con la cuenta estación → `idToken` (dura 1 h; renovarlo con `securetoken.googleapis.com`).
  - Escribir `estacion/riego01` (PATCH a la REST de Firestore) cada 30 s o cuando cambie algo.
  - Leer `comandos` con `estado == pendiente` cada 5 s, ejecutarlos y marcarlos `ejecutado`.
- Mantener la lista blanca, los rangos, el corte de bomba a los 30 s y los comandos por Serial.
- Credenciales en `arduino_secrets.h` (placeholders).
- Antes de programar, revisa si la librería `FirebaseClient` (Mobizt) soporta el UNO R4 WiFi y dime cuál camino conviene.

### Fase 9: Cierre
- Actualizar `LEEME_U3.md`: setup de Firebase, cuentas de prueba (sin contraseñas reales), reglas, la tabla ISO 27002 actualizada (8.5 Firebase Auth, 8.2/8.3 reglas por rol, 8.24 TLS de Firebase, 8.14 caché offline) y una tabla de pruebas.
- Guion de demo: login admin → monitoreo → comando → solicitudes; login usuario → enviar → intento de lectura bloqueado; modo avión → sincroniza.
