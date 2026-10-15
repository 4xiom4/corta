# Auditoría Senior Multi-Área: Viabilidad, Aristas Ocultas y Correcciones Arquitectónicas (Corta App)

*Fecha: Octubre de 2026*  
*Autores: Panel de Arquitectura (Telecom, Seguridad, Rendimiento Android, Compliance Legal)*  
*Versión: 1.0 (Auditoría Exhaustiva de Producción)*

---

## 1. Veredicto Ejecutivo de Viabilidad

> **Veredicto: 100% VIABLE**, pero requería corregir **4 fallos de diseño crítico** y contemplar **5 aristas operativas del mundo real** que habrían provocado bloqueos de Google Play, corrupción de base de datos o fallos en capas de fabricantes como Xiaomi/HyperOS.

---

## 2. Los 4 Fallos Críticos Detectados en el Código Actual

### 🔴 Fallo Crítico 1: Suicidio Criptográfico en `SecurityProvider.kt`
* **El Problema:** En `SecurityProvider.kt` (línea 48), si ocurre un error transitorio al leer `EncryptedSharedPreferences` (común tras reiniciar el teléfono antes del primer desbloqueo - Direct Boot), el código ejecuta:
  ```kotlin
  keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
  context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
  ```
  Al borrar la llave maestra, el sistema genera una nueva clave aleatoria. Cuando SQLCipher intenta abrir la base de datos `corta.db` existente con la nueva clave, lanza `SQLiteException: file is not a database`. **La base de datos del usuario queda corrupta e irrecuperable para siempre.**
* **La Solución Senior:** Nunca borrar la clave maestra ante un error de lectura. Implementar un fallback seguro y manejo de estado bloqueado (Direct Boot Aware).

### 🔴 Fallo Crítico 2: Números Ocultos / Privados / Desconocidos sin Manejo
* **El Problema:** En `CortaCallScreeningService.kt`, si entra una llamada con Caller ID suprimido ("Número Privado" o "Desconocido"), `callDetails.handle` es `null`. El código produce un string vacío `""`, la heurística devuelve `WARN`, y la llamada **sigue sonando**. Gran parte de las estafas y amenazas en Chile entran por número privado.
* **La Solución Senior:** Crear un setting explícito en `corta_prefs`: *"Bloquear / Silenciar llamadas con Número Oculto o Privado"* (Switch por defecto configurable).

### 🔴 Fallo Crítico 3: Incompatibilidad de Paginación en Android 15 (`ContentResolver`)
* **El Problema:** `CallLogViewModel` y `SmsViewModel` concatenan `LIMIT 30 OFFSET x` dentro del string `sortOrder`.
* **La Realidad en Android 10 a 15:** En Android 15, `ContentResolver` rechaza cláusulas SQL no autorizadas en `sortOrder`. Lanza `IllegalArgumentException`, la consulta cae a `LoadResult.Error(e)` y la pantalla se queda en blanco diciendo *"No hay llamadas"*.
* **La Solución Senior:** Implementar `Bundle` con `ContentResolver.QUERY_ARG_LIMIT` y `ContentResolver.QUERY_ARG_OFFSET` en API 26+ y desplazamiento seguro de cursor (`cursor.moveToPosition`).

### 🔴 Fallo Crítico 4: Destrucción Reactiva del Paginador (`flatMapLatest`)
* **El Problema:** En los ViewModels, `combine(internalLogsFlow, rulesFlow).flatMapLatest { Pager(...) }` destruye y vuelve a instanciar el `Pager` completo cada vez que la base de datos emite un cambio. Esto genera tirones de frames (jank) y congelamientos de la interfaz en Compose.
* **La Solución Senior:** Mantener una única instancia de `Pager` viva y delegar la invalidación a través de `PagingSource.invalidate()`.

---

## 3. Las 5 Aristas de Producción que Debemos Contemplar

```
┌─────────────────────────────────────────────────────────────┐
│ 1. COMPLIANCE GOOGLE PLAY: Política de Call Log y SMS       │
├─────────────────────────────────────────────────────────────┤
│ 2. FABRICANTES (OEM): El asesino de apps de Xiaomi / MIUI   │
├─────────────────────────────────────────────────────────────┤
│ 3. HARDWARE CHILENO: Soporte Dual-SIM (SIM 1 vs SIM 2)      │
├─────────────────────────────────────────────────────────────┤
│ 4. DIRECT BOOT: Llamadas entrantes antes del PIN post-reboot│
├─────────────────────────────────────────────────────────────┤
│ 5. MEMORY HYGIENE: Limpieza de memoria RAM con llaves AES   │
└─────────────────────────────────────────────────────────────┘
```

### Arista 1: Políticas de Google Play Store (Call Log y SMS Permissions)
* **La Regla de Google Play:** Google tiene una política de tolerancia cero para `READ_CALL_LOG` y `RECEIVE_SMS`. Para que la app sea aprobada en Play Store sin ser rechazada:
  * **Uso permitido 1 (Companion Anti-Spam):** Se declara ante Google que la app es un `Call Screening Service` oficial bajo `RoleManager.ROLE_CALL_SCREENING`.
  * **Uso permitido 2 (Default SMS):** Solo se solicitan permisos de escritura de SMS si el usuario designa activamente a Corta como su app predeterminada de mensajes.
  * **Arquitectura Graciosa:** Si el usuario no otorga el rol de SMS, la app **sigue funcionando al 100% como filtro de llamadas**. Las funciones no deben estar acopladas.

### Arista 2: La Capa de Fabricante (Xiaomi / HyperOS / MIUI)
El teléfono del usuario es un **Xiaomi POCO X3 NFC**. En MIUI/HyperOS, el sistema operativo mata los procesos en segundo plano agresivamente:
* Para que Corta intercepte llamadas sin retraso en Xiaomi:
  1. Debe configurarse en el onboarding una guía visual para activar: *"Inicio Automático (Autostart)"*.
  2. Ajustar el Ahorro de Batería de la app en *"Sin Restricciones"*.
  3. Asegurar que el rol de `Identificador de llamadas y spam` esté asignado a Corta en los ajustes de Xiaomi.

### Arista 3: Soporte Dual-SIM (Teléfonos en Chile con 2 Chips o eSIM)
En Chile es común usar SIM 1 (personal, ej. Entel) y SIM 2 (laboral, ej. WOM):
* En `Call.Details`: El sistema provee `getAccountHandle()` (`PhoneAccountHandle`).
* Corta debe identificar en qué SIM entró la llamada para que el usuario sepa en el historial si el spam fue a su línea de trabajo o personal.

### Arista 4: El Ciclo de Vida Direct Boot (Llamadas tras Reiniciar el Teléfono)
* Cuando el teléfono se reinicia y el usuario aún no ingresa su PIN o huella, el almacenamiento cifrado por credenciales (CE) está **bloqueado por Android**.
* Si entra una llamada en ese instante:
  * La base de datos SQLCipher cifrada no puede abrirse.
  * **La Solución:** Corta App mantiene la **Whitelist Crítica de Emergencias (131, 132, 133, 14xx)** y las reglas fundamentales de la SUBTEL (809 y 600) en memoria de código estático (Device-Protected Storage / Código nativo). Las emergencias se protegen incluso con el teléfono recién reiniciado sin desbloquear.

### Arista 5: Higiene Criptográfica en Memoria RAM
* La clave de cifrado de SQLCipher no debe permanecer en el Garbage Collector de Java como un `String` inmutable.
* Debe manejarse como `ByteArray` y ejecutarse `Arrays.fill(passphrase, 0.toByte())` inmediatamente después de abrir la conexión del driver de SQLDelight.

---

## 4. Plan de Acción Técnico Inmediato

1. **Blindar `SecurityProvider.kt`:** Eliminar la autodestrucción de la MasterKey que causa corrupción de base de datos.
2. **Implementar Paginación Moderna (`Bundle`) en `CallLogViewModel`:** Eliminar la inyección de `LIMIT` para que el Xiaomi POCO X3 cargue el historial al instante.
3. **Estabilizar Paging 3:** Eliminar `flatMapLatest` y usar un Flow cached en `viewModelScope`.
4. **Manejar Números Ocultos y Privados en `CortaCallScreeningService`:** Agregar soporte y setting para llamadas sin Caller ID.
5. **Pre-sembrar la Whitelist y SUBTEL en SQLDelight:** Reglas 809 y 600 listas de fábrica, con soporte para números locales (`809...`) e internacionales (`+56809...`).
6. **Restaurar la Navegación de 3 Destinos en Compose:** Mensajes, Llamadas, Contactos (Material 3).
