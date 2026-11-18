// Tests de firestore.rules contra el emulador de Firestore.
// Ejecutar desde esta carpeta: firebase emulators:exec --only firestore "npm test"
import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { addDoc, collection, deleteDoc, doc, getDoc, getDocs, setDoc, updateDoc } from 'firebase/firestore';

const UID = { admin: 'uid-admin', usuario: 'uid-usuario', observador: 'uid-observador', estacion: 'uid-estacion' };
let env;

/** Firestore como si hubiera iniciado sesion la cuenta con ese rol. */
const como = (rol) => env.authenticatedContext(UID[rol]).firestore();

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'riego-26ba0', // solo emulador: no toca el proyecto real
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(() => env.cleanup());

// Datos de ejemplo cargados sin reglas antes de cada test
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const [rol, uid] of Object.entries(UID)) {
      await setDoc(doc(db, 'usuarios', uid), { nombre: rol, correo: `${rol}@riego.cl`, rol });
    }
    await setDoc(doc(db, 'estacion', 'riego01'), { humedad: 45, bomba: false, umbral: 30, duracion: 10, auto: true, online: true });
    await setDoc(doc(db, 'comandos', 'c1'), { tipo: 'REGAR', creadoPor: UID.admin, estado: 'pendiente' });
    await setDoc(doc(db, 'solicitudes', 's1'), { uid: UID.usuario, nombre: 'usuario', sector: 'Huerto', comentario: 'Seco', prioridad: 'Alta' });
    await setDoc(doc(db, 'acciones', 'a1'), { uid: UID.admin, accion: 'Riego manual solicitado' });
  });
});

const solicitud = (uid, comentario = 'Hojas secas') => ({ uid, nombre: 'x', sector: 'Invernadero 2', comentario, prioridad: 'Media' });

describe('observador (solo lectura)', () => {
  test('lee estacion/riego01', () => assertSucceeds(getDoc(doc(como('observador'), 'estacion/riego01'))));
  test('lee su propio doc de usuarios', () => assertSucceeds(getDoc(doc(como('observador'), 'usuarios', UID.observador))));
  test('no lee el doc de otro usuario', () => assertFails(getDoc(doc(como('observador'), 'usuarios', UID.admin))));
  test('no lee solicitudes', async () => {
    await assertFails(getDoc(doc(como('observador'), 'solicitudes/s1')));
    await assertFails(getDocs(collection(como('observador'), 'solicitudes')));
  });
  test('no lee comandos', async () => {
    await assertFails(getDoc(doc(como('observador'), 'comandos/c1')));
    await assertFails(getDocs(collection(como('observador'), 'comandos')));
  });
  test('no lee acciones', async () => {
    await assertFails(getDoc(doc(como('observador'), 'acciones/a1')));
    await assertFails(getDocs(collection(como('observador'), 'acciones')));
  });
  test('no crea comandos', () =>
    assertFails(addDoc(collection(como('observador'), 'comandos'), { tipo: 'REGAR', creadoPor: UID.observador, estado: 'pendiente' })));
  test('no escribe estacion, solicitudes ni acciones', async () => {
    await assertFails(updateDoc(doc(como('observador'), 'estacion/riego01'), { bomba: true }));
    await assertFails(addDoc(collection(como('observador'), 'solicitudes'), solicitud(UID.observador)));
    await assertFails(addDoc(collection(como('observador'), 'acciones'), { uid: UID.observador, accion: 'x' }));
  });
});

describe('admin (sin cambios)', () => {
  test('lee estacion, solicitudes, comandos y acciones', async () => {
    const db = como('admin');
    await assertSucceeds(getDoc(doc(db, 'estacion/riego01')));
    await assertSucceeds(getDocs(collection(db, 'solicitudes')));
    await assertSucceeds(getDocs(collection(db, 'comandos')));
    await assertSucceeds(getDocs(collection(db, 'acciones')));
  });
  test('crea comandos REGAR y CFG, no de otro tipo', async () => {
    const db = como('admin');
    await assertSucceeds(addDoc(collection(db, 'comandos'), { tipo: 'REGAR', creadoPor: UID.admin, estado: 'pendiente' }));
    await assertSucceeds(addDoc(collection(db, 'comandos'), { tipo: 'CFG', umbral: 30, duracion: 10, auto: true, creadoPor: UID.admin, estado: 'pendiente' }));
    await assertFails(addDoc(collection(db, 'comandos'), { tipo: 'BORRAR', creadoPor: UID.admin, estado: 'pendiente' }));
  });
  test('registra acciones y no borra comandos', async () => {
    const db = como('admin');
    await assertSucceeds(addDoc(collection(db, 'acciones'), { uid: UID.admin, accion: 'Riego manual solicitado' }));
    await assertFails(deleteDoc(doc(db, 'comandos/c1')));
  });
  test('no escribe estacion ni su rol', async () => {
    await assertFails(updateDoc(doc(como('admin'), 'estacion/riego01'), { bomba: true }));
    await assertFails(updateDoc(doc(como('admin'), 'usuarios', UID.admin), { rol: 'estacion' }));
  });
});

describe('usuario (sin cambios)', () => {
  test('crea su solicitud', () => assertSucceeds(addDoc(collection(como('usuario'), 'solicitudes'), solicitud(UID.usuario))));
  test('no crea solicitudes a nombre de otro ni con comentario de mas de 200', async () => {
    await assertFails(addDoc(collection(como('usuario'), 'solicitudes'), solicitud(UID.admin)));
    await assertFails(addDoc(collection(como('usuario'), 'solicitudes'), solicitud(UID.usuario, 'a'.repeat(201))));
  });
  test('no lee estacion, solicitudes, comandos ni acciones', async () => {
    const db = como('usuario');
    await assertFails(getDoc(doc(db, 'estacion/riego01')));
    await assertFails(getDocs(collection(db, 'solicitudes')));
    await assertFails(getDocs(collection(db, 'comandos')));
    await assertFails(getDocs(collection(db, 'acciones')));
  });
  test('lee su propio doc y no se cambia el rol', async () => {
    await assertSucceeds(getDoc(doc(como('usuario'), 'usuarios', UID.usuario)));
    await assertFails(updateDoc(doc(como('usuario'), 'usuarios', UID.usuario), { rol: 'admin' }));
  });
  test('no crea comandos', () =>
    assertFails(addDoc(collection(como('usuario'), 'comandos'), { tipo: 'REGAR', creadoPor: UID.usuario, estado: 'pendiente' })));
});

describe('estacion y sin sesion', () => {
  test('estacion escribe su estado, lee y marca comandos', async () => {
    const db = como('estacion');
    await assertSucceeds(setDoc(doc(db, 'estacion/riego01'), { humedad: 50, bomba: false, online: true }));
    await assertSucceeds(getDocs(collection(db, 'comandos')));
    await assertSucceeds(updateDoc(doc(db, 'comandos/c1'), { estado: 'ejecutado' }));
  });
  test('sin sesion no se lee nada', async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, 'estacion/riego01')));
    await assertFails(getDocs(collection(db, 'solicitudes')));
  });
});
