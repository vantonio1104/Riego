# Guía de defensa — Estación de Riego

Sirve para preparar la defensa (indicador 3.1.4.14: *ejecuta, demuestra y explica el código, acreditando comprensión y autoría*).
Para cada indicador dice **qué pide**, **dónde está en el código** y **cómo explicarlo**. Estúdiala y explícalo con tus palabras: la nota depende de que entiendas el código, no de leer esta guía.

## 1. Mapa del proyecto

| Archivo | Qué hace |
|---|---|
| `app/src/main/java/cl/inacap/riegoiot/LoginActivity.kt` | Pantalla de login: valida, inicia sesión con Firebase Auth, lee el rol y abre la pantalla que corresponde |
| `Sesion.kt` | Roles (`admin`, `usuario`, `observador`), lectura del rol, cierre de sesión y protección de pantallas (`exigirRol`) |
| `AdminActivity.kt` | Monitoreo en vivo de la estación y lista de solicitudes (admin). Vista de solo lectura (observador) |
| `ActivadorActivity.kt` | Comandos del admin ("Regar ahora", "Guardar configuración") e historial de acciones |
| `UsuarioActivity.kt` | Formulario "Solicitud de riego" del usuario |
| `Conexion.kt` | Detecta si hay red, traduce errores de Firebase a mensajes y ofrece "Reintentar" |
| `Protocolo.kt`, `Solicitud.kt` | Reglas de validación (rangos de configuración, largo de los textos) |
| `app/src/main/res/layout/*.xml` | Diseño de cada pantalla (XML) |
| `app/src/main/res/values/strings.xml` | Todos los textos de la app, en español |
| `firestore.rules` | Reglas de seguridad que aplica el servidor de Firebase |
| `rules-test/reglas.test.js` | 19 pruebas automáticas de las reglas |

## 2. El flujo completo en un minuto

1. El usuario escribe correo y clave. La app **valida el formato** y llama a `signInWithEmailAndPassword` (Firebase Auth).
2. Si Firebase acepta, la app lee **`usuarios/{uid}.rol` desde el servidor** (`Source.SERVER`). Sin rol confirmado, cierra la sesión: nunca da acceso "por si acaso".
3. Según el rol abre `AdminActivity` (admin y observador) o `UsuarioActivity` (usuario), y muestra "Sesión iniciada como …".
4. Cada pantalla vuelve a revisar el rol al abrirse (`Sesion.exigirRol`). Si no corresponde, vuelve al login con un aviso.
5. El admin **lee** Firestore en vivo (`addSnapshotListener`) y **escribe** comandos. El usuario solo **escribe** solicitudes.
6. Aunque alguien modifique la app, **`firestore.rules`** en el servidor decide qué puede leer o escribir cada rol.

## 3. Indicador por indicador

### 3.1.1.1 — Autenticación con correo y contraseña
- **Dónde:** `LoginActivity.kt:86` (`signInWithEmailAndPassword`), validaciones en `:71` y `:75`, errores en `onLoginError` (`:97`), bloqueo en `LoginGuard` (`:27`).
- **Cómo explicarlo:** "No hay credenciales fijas: Firebase Authentication verifica el correo y la clave. Antes de llamar a Firebase valido el formato del correo con `Patterns.EMAIL_ADDRESS` y que la clave tenga al menos 6 caracteres. Si Firebase la rechaza, muestro *Correo o contraseña incorrectos*, y tras 5 intentos bloqueo el botón 30 segundos."

### 3.1.1.2 — Validación antes de guardar en Firestore
- **Dónde:** `UsuarioActivity.kt:48` (`validar`), límites en `Solicitud.kt:5` y `:8`; configuración del admin en `ActivadorActivity.kt:134` con `Protocolo.cmdConfig` (`Protocolo.kt:25`).
- **Cómo explicarlo:** "Valido campos obligatorios, largo máximo (sector 40, comentario 200) y tipos: el correo con formato de correo, la duración solo números entre 3 y 30. El error se muestra en el mismo campo (`TextInputLayout.error`) y no se envía nada hasta que todo es válido. Las reglas de Firestore vuelven a validar el largo del comentario en el servidor."

### 3.1.1.3 — Control de sesiones
- **Dónde:** sesión activa al abrir la app en `LoginActivity.kt:56`, rol desde el servidor en `:117`, cierre en `Sesion.kt:35` (`cerrar`), protección en `Sesion.kt:50` (`exigirRol`).
- **Cómo explicarlo:** "Firebase recuerda la sesión: al abrir la app, si hay usuario, leo su rol y lo llevo directo a su pantalla. Si no hay red, no puedo confirmar el rol, así que cierro la sesión y aviso: prefiero pedir login otra vez antes que dar un acceso sin verificar. *Cerrar sesión* hace `signOut()`, limpia la pila de pantallas y muestra *Sesión cerrada*."

### 3.1.1.4 — Pérdida de conexión y errores de Firebase
- **Dónde:** `Conexion.kt:15` (`hayRed`), `:23` (`mensajeError`), `:33` (`errorConReintentar`); envío sin duplicar en `UsuarioActivity.kt:70` y `:91`.
- **Cómo explicarlo:** "Firestore guarda las escrituras en caché si no hay red y las sube al reconectar. Si no hay red, aviso *Sin conexión: se enviará al reconectar*. Para no duplicar, creo el id del documento una sola vez (`document()`) y en un reintento escribo **el mismo documento**. Si falla una lectura, aparece un Snackbar con *Reintentar*. Distingo `UNAVAILABLE` (sin red), `PERMISSION_DENIED` (sin permiso) y errores de autenticación."

### 3.1.2.5 — Formulario con EditText, TextView y Button
- **Dónde:** `res/layout/activity_usuario.xml`: **2 EditText** (`etSector` `:68`, `etComentario` `:87`), **4 TextView** (`tvFormTitulo` `:41`, `tvFormDescripcion` `:50`, `tvPrioridadLabel` `:97`, `tvEstado` `:141`), **Button** `btnEnviar` (`:134`) y un selector de prioridad. El login tiene además 2 EditText, 3 TextView y 1 Button.
- **Cómo explicarlo:** "Uso `TextInputEditText`, que es un EditText de Material Design. El TextView `tvEstado` muestra el resultado del envío: *Enviando…*, *Solicitud enviada* o el error."

### 3.1.2.6 — El administrador consulta registros de Firestore
- **Dónde:** `AdminActivity.kt:104` (`observarSolicitudes`), `:74` (`observarEstacion`), `ActivadorActivity.kt:85` (`observarHistorial`).
- **Cómo explicarlo:** "Uso `addSnapshotListener`: la lista se actualiza sola cuando llega una solicitud nueva, sin recargar. Ordeno por fecha descendente y limito la cantidad. Muestro los datos en un `RecyclerView`."

### 3.1.2.7 — Guardar datos desde ambos perfiles
- **Dónde:** usuario: `UsuarioActivity.kt:57` (`enviar`) en `solicitudes`. Admin: `ActivadorActivity.kt:100` (`enviarComando`), que guarda en `comandos` y `acciones` en un solo `batch` (`:102`).
- **Cómo explicarlo:** "El usuario guarda solicitudes; el administrador guarda comandos y su registro en el historial. El `batch` garantiza que se guarden los dos documentos o ninguno. La fecha la pone el servidor (`FieldValue.serverTimestamp()`), no el teléfono."

### 3.1.3.8 — Imagen de fondo, imagen adicional y fuente personalizada
- **Dónde:** fondo `@drawable/fondo_riego` en las 4 pantallas (ej. `activity_login.xml:15`) con capa de legibilidad `fondo_overlay`; versión nocturna en `drawable-night-nodpi`. Logo propio `ic_logo` (`activity_login.xml:33`) e ícono `ic_logout`. Fuente **Nunito** (`res/font/nunito_bold.ttf`) en 4 textos: `activity_login.xml:44`, `activity_monitoreo.xml:85` y `:177`, `activity_usuario.xml:45`.
- **Cómo explicarlo:** "La capa semitransparente sobre el fondo mantiene el texto legible en modo claro y oscuro. La fuente se aplica con `android:fontFamily="@font/nunito_bold"`. Su licencia (OFL) está en `licencias/`."

### 3.1.3.9 — Navegación según el perfil
- **Dónde:** `LoginActivity.kt:125` (decide la pantalla por rol), `exigirRol` al inicio de cada pantalla (ej. `AdminActivity.kt:56`, `UsuarioActivity.kt:28`), `android:exported="false"` en `AndroidManifest.xml`.
- **Cómo explicarlo:** "El login decide la pantalla según el rol. Además cada pantalla verifica el rol al abrirse: si un usuario llegara a la pantalla del admin, vuelve al login con *No tienes acceso a esa pantalla*. Las pantallas internas no se pueden abrir desde otras apps (`exported=false`)."

### 3.1.3.10 — Layouts XML vinculados con Kotlin
- **Dónde:** `app/build.gradle.kts` → `viewBinding = true`. Cada layout genera su clase: `activity_login.xml` → `ActivityLoginBinding` (`LoginActivity.kt`), `activity_monitoreo.xml` → `ActivityMonitoreoBinding` (`AdminActivity.kt:52`), etc.
- **Cómo explicarlo:** "Con **ViewBinding**, Android genera una clase por layout. En `onCreate` hago `binding = ActivityLoginBinding.inflate(layoutInflater)` y `setContentView(binding.root)`, y accedo a cada componente por su id: `binding.etUsername`, `binding.btnLogin`. Es más seguro que `findViewById`, porque si un id no existe el error aparece al compilar. Los textos están en `strings.xml` y los colores en `colors.xml`."

### 3.1.4.11 — Seguridad con Auth y reglas de Firestore
- **Dónde:** `firestore.rules` (funciones `rol()` y `es(r)`), publicadas con `firebase deploy`. Pruebas en `rules-test/reglas.test.js` (19/19).
- **Cómo explicarlo:** "Las reglas corren en el servidor de Firebase: aunque alguien modifique la app, no puede saltárselas. `rol()` lee el rol del usuario desde `usuarios/{uid}`, y cada colección permite solo lo de cada rol. Nadie puede cambiarse el rol desde la app (`allow write: if false`). El admin solo crea comandos `REGAR` o `CFG`, y nadie borra registros."

### 3.1.4.12 — El usuario común no puede consultar
- **Dónde:** `firestore.rules`: `solicitudes` → `allow read: if es('admin')`; `estacion`, `comandos` y `acciones` no permiten leer al usuario.
- **Cómo explicarlo:** "La pantalla del usuario no tiene ninguna lista, pero eso no basta: la protección real son las reglas. Solo puede **crear** su solicitud, con su propio `uid` y un comentario de 200 caracteres o menos. Se probó en el Rules Playground y en las pruebas automáticas: si intenta leer, Firebase responde *permiso denegado*."

### 3.1.4.13 — Mensajes de éxito y error
- **Dónde:** login (`onLoginError` y el aviso *Sesión iniciada como …* en `LoginActivity.kt:136`), *Sesión cerrada* (`Sesion.cerrar`), almacenamiento (*Solicitud enviada*, *Configuración guardada correctamente*, *Riego manual solicitado*), conexión (`mensajeError`, *Sin conexión…*). Todos en `strings.xml`.
- **Cómo explicarlo:** "Cada operación termina con un mensaje: de éxito o un error claro. Los errores de Firebase se traducen a mensajes en español según su tipo."

### 3.1.4.14 — Ejecución y demostración
- **Dónde:** guion de demo en `LEEME_U3.md` (sección 7) y evidencia en el informe Word.
- **Cómo hacerlo:** sigue el guion: admin → monitoreo y comando → solicitudes; usuario → formulario y envío; observador → solo lectura; modo avión → sincroniza. Ten Android Studio abierto para mostrar el código de lo que pregunten.

## 4. Preguntas probables

- **¿Por qué el monitoreo dice "Sin datos"?** Porque no hay Arduino conectado: el documento `estacion/riego01` lo escribe la estación. La app no inventa valores. Los comandos sí se guardan en Firestore como `pendiente`.
- **¿Qué pasa si un usuario modifica la app para leer solicitudes?** Las reglas del servidor lo bloquean: `solicitudes` solo la lee el rol `admin`.
- **¿Dónde se guarda el rol y quién lo puede cambiar?** En `usuarios/{uid}.rol`. Solo desde la consola de Firebase: las reglas no permiten escribir esa colección desde la app.
- **¿Por qué no se duplica una solicitud enviada sin red?** Porque el id del documento se crea una vez y el reintento escribe el mismo documento (`set` sobre la misma referencia).
- **¿Qué es `await()`?** Convierte la tarea de Firebase en una función `suspend` de corrutinas, para escribir código secuencial sin callbacks anidados.
- **¿Qué diferencia hay entre `get()` y `addSnapshotListener`?** `get()` lee una vez (el rol); el listener queda escuchando y actualiza la pantalla en vivo (solicitudes, historial).
- **¿Por qué el rol se lee con `Source.SERVER` en el login?** Para no confiar en una copia vieja guardada en el teléfono: el acceso se decide con el dato actual del servidor.
- **¿Qué hace el `batch`?** Guarda el comando y su registro en el historial juntos: o se guardan los dos, o ninguno.
- **¿Para qué sirve el rol observador?** Es un rol de solo lectura (mínimo privilegio): ve el monitoreo, pero no envía comandos ni ve solicitudes.

## 5. Pruebas que respaldan todo

- **App:** `./gradlew test` → 9 de 9 (validaciones, protocolo, errores de conexión y roles).
- **Reglas:** `cd rules-test && firebase emulators:exec --only firestore "npm test"` → 19 de 19.
- **Manual:** 21 pruebas en el emulador, detalladas en `LEEME_U3.md` (sección 6).
