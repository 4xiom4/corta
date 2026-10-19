# Corta App — Documentación Integral del Proyecto 🛡️🇨🇱

> **«Con Corta la haces corta». El escudo anti-spam, identificador y directorio telefónico privado para Chile.**

---

## 1. Visión General y Propósito del Proyecto

### En Lenguaje Cotidiano (Explicación Funcional)
En Chile, el acoso telefónico ha alcanzado niveles críticos. Cada ciudadano recibe múltiples llamadas y mensajes diarios de telemarketing agresivo (ofrecimiento de planes, tarjetas, seguros), cobranzas improcedentes y estafas sofisticadas (suplantación de bancos, falsas entregas de encomiendas o falsos secuestros). 

Las soluciones tradicionales en el mercado (como Truecaller o Whoscall) presentan un costo invisible inaceptable: para identificar una llamada, exigen que el usuario suba su libreta de contactos completa a servidores en el extranjero, comercializando los datos personales y creando redes de rastreo invasivas.

**Corta App nace para romper ese paradigma:**
* **Tu libreta de contactos nunca sale de tu teléfono.**
* **Las llamadas no se consultan en internet:** El análisis de si un número es spam o legítimo se realiza dentro del propio procesador de tu celular en una fracción de segundo (< 2 milisegundos), antes de que el teléfono empiece a timbrar.
* **Directorio de Chile integrado:** La aplicación incluye de fábrica más de 600 números de emergencia, seguridad municipal de comunas (14xx), salud (SAMU 131, CESFAM, hospitales) y servicios públicos de todo el país, organizados por región y comuna.
* **Filtro de mensajes SMS:** Detecta automáticamente estafas bancarias (smishing), enlaces sospechosos y avisos publicitarios, clasificándolos en carpetas ordenadas sin que tengas que borrarlos manualmente.

### En Lenguaje Técnico (Especificación de Ingeniería)
Corta App es un sistema de telecomunicaciones y ciberseguridad *Local-First* y *Zero-Knowledge* desarrollado bajo el paradigma **Kotlin Multiplatform (KMP)** para el ecosistema Android (API 26 a 35). Actúa a nivel de sistema operativo como `RoleManager.ROLE_CALL_SCREENING` y `BroadcastReceiver` prioritario de telefonía y SMS, interceptando eventos de señalización antes del timbrado o notificación acústica. Toda la lógica de clasificación probabilística, normalización de prefijos, concordancia en estructuras Trie y reglas de base de datos se ejecuta en memoria RAM local con garantías deterministas de latencia dura inferiores a 2 milisegundos, erradicando cualquier llamada I/O de red durante la fase crítica de la llamada entrante.

---

## 2. Arquitectura de Software y Stack Tecnológico

El proyecto está estructurado siguiendo principios estrictos de **Clean Architecture** y separación de responsabilidades:

```
corta-v1/
├── composeApp/                     # Módulo Nativo Android (Presentación y Servicios del SO)
│   ├── src/main/kotlin/com/corta/app/
│   │   ├── services/              # CallScreeningService, SmsReceiver, Notificadores
│   │   ├── ui/                    # UI declarativa en Jetpack Compose (M3)
│   │   │   ├── auth/              # Onboarding y selección regional inicial
│   │   │   ├── contacts/          # Agenda telefónica y Directorio Regional Chileno
│   │   │   ├── designsystem/      # Componentes UI (CortaFilterChip, Botones accesibles)
│   │   │   ├── profile/           # Perfil de usuario y soporte al proyecto
│   │   │   ├── settings/          # Ajustes del motor anti-spam, reglas y SQLite
│   │   │   └── sms/               # Bandejas de mensajería y visor de mensajes
│   │   └── di/                    # Módulos de inyección de dependencias Koin
│   └── proguard-rules.pro         # Reglas de ofuscación y optimización R8
│
└── shared/                         # Módulo Común KMP (Lógica de Dominio y Datos Agnóstica)
    ├── src/commonMain/kotlin/com/corta/
    │   ├── domain/                # CallValidator, SpamEvaluator, CortaRepository
    │   └── data/                  # Implementación de bases de datos y adaptadores
    └── src/commonMain/sqldelight/ # Esquema relacional SQLDelight (SSOT de datos)
```

### Tecnologías Principales
* **Lenguaje:** Kotlin 2.2+ (KMP).
* **UI:** Jetpack Compose Multiplatform con **Material Design 3 (M3)**.
* **Inyección de Dependencias:** Koin.
* **Base de Datos Local:** SQLDelight con driver cifrado **SQLCipher (AES-256)**.
* **Corrutinas y Flujo Asíncrono:** Kotlin Coroutines (`Dispatchers.IO`, `StateFlow`, `SharedFlow`).
* **Compilación y Shrinking:** R8 / ProGuard con optimización de recursos y eliminación de código muerto.
* **Toolchain de Compilación:** Gradle 9.5.0 con Java 17.

---

## 3. El Motor Anti-Spam de Llamadas (Call Screening)

```
                     LLAMADA ENTRANTE
                            │
                            ▼
              ┌───────────────────────────┐
              │ CortaCallScreeningService │ (Android OS)
              └─────────────┬─────────────┘
                            │ Extrae número & normaliza simétricamente (+56)
                            ▼
                  ┌───────────────────┐
                  │   CallValidator   │
                  └─────────┬─────────┘
                            │
              ┌─────────────┴─────────────┐
              ▼                           ▼
   ¿Es Whitelist Vital?          ¿Evaluación Anti-Spam?
   (131, 132, 133, 14xx)                   │
              │                            ▼
      PERMITIR SIEMPRE              ┌──────────────┐
     (Garantía Legal)               │SpamEvaluator │
                                    └──────┬───────┘
                                           │ Consulta memoria/Trie (< 2 ms)
                                           ▼
                            ┌──────────────────────────────┐
                            │    Acción Decidida:          │
                            │  - ALLOW: Timbrado normal    │
                            │  - WARN: Alerta en pantalla  │
                            │  - BLOCK: setRejectCall(true)│
                            └──────────────────────────────┘
```

### Reglas Críticas del Motor de Llamadas
1. **Latencia Máxima < 2 Milisegundos:** Durante la entrada de una llamada, el sistema operativo Android asigna una ventana muy estrecha para decidir si rechazarla. Corta resuelve la decisión enteramente en memoria RAM mediante estructuras indexadas y tablas de SQLite locales. **Queda terminantemente prohibida cualquier petición web o de red durante el timbrado.**
2. **Acción de Bloqueo Silencioso:** Cuando una llamada se clasifica como `BLOCK`, el servicio ejecuta:
   * `setDisallowCall(true)` (la llamada no entra al marcador del sistema).
   * `setRejectCall(true)` (se corta automáticamente la comunicación al emisor).
   * `setSkipCallLog(false)` (queda registrada en el historial para auditoría del usuario).
   * Notificación silenciosa en la barra de estado informando el número interceptado y la razón técnica (ej. *"Spam comercial SUBTEL 809 bloqueado"*).
3. **Manejo de Números Ocultos / Privados:** Soporte configurable para silenciar o rechazar llamadas entrantes con identificación suprimida, fuente común de estafas carcelarias en Chile.

---

## 4. Normalización Simétrica y Normativa Telefónica Chilena

### El Desafío de la Red Chilena (+56)
En Chile, una misma línea telefónica puede presentarse de diversas maneras dependiendo de la compañía operadora, si la llamada proviene de red fija, móvil o VoIP:
* Móvil: `+56912345678`, `56912345678`, `912345678`.
* Comercial SUBTEL: `+56809123456`, `809123456`.
* Servicio 600: `6006001234`, `+566006001234`.
* Cortos de Emergencia: `133`, `131`, `132`, `1402`.

**Normalización Simétrica:** Todo número entrante y toda regla registrada en base de datos pasa por una función de normalización matemática uniforme (`PhoneNumberUtils.normalize()`). Esto garantiza que una regla sobre el prefijo `809` intercepte la llamada sin importar si la antena emitió el número con o sin el código de país `+56`.

### Categorías SUBTEL Pre-sembradas
* **Prefijo 809 (Telemarketing Masivo):** En conformidad con la regulación chilena, este prefijo está reservado para llamadas comerciales publicitarias masivas. Corta lo pre-siembra localmente desde la primera instalación para bloqueo automático sin necesidad de configurar nada.
* **Prefijo 600 (Servicios de Utilidad y Contratados):** Reservado para atención de bancos, aseguradoras, telecomunicaciones y servicios públicos contratados. Corta los identifica para que el usuario sepa que no se trata de spam molesto, sino de gestiones solicitadas.

---

## 5. Directorio Regional Chileno (+600 Números) y Whitelist Inmutable

### ¿Por qué una Lista Blanca Inmutable?
En situaciones de riesgo vital o catástrofe, una aplicación anti-spam jamás debe bloquear por error una llamada de socorro. Corta implementa una **Lista Blanca Inmutable** que tiene prioridad absoluta sobre cualquier otra regla:

* **Emergencias Nacionales:**
  * `130`: CONAF (Incendios forestales).
  * `131`: SAMU (Urgencias médicas y ambulancias).
  * `132`: Bomberos de Chile.
  * `133`: Carabineros de Chile.
  * `134`: PDI (Policía de Investigaciones).
  * `137`: Rescate Marítimo (Armada de Chile).
  * `147`: Fono Niños (Carabineros).
  * `149`: Fono Familia (Violencia intrafamiliar).
* **Líneas 14xx de Seguridad Municipal:** Más de 50 comunas de Chile cuentan con números de patrullaje comunal rápido (ej. `1402` Providencia, `1403` Las Condes, `1406` Ñuñoa, `1414` Vitacura, `1417` Lo Barnechea, `1445` Santiago, etc.).
* **Directorio de 16 Regiones:** Más de 600 registros oficiales que cubren desde Arica y Parinacota hasta Magallanes y la Antártica Chilena, incluyendo hospitales regionales, comisarías territoriales y servicios públicos de utilidad.

---

## 6. Motor de Mensajería SMS y Heurística Anti-Smishing

El tráfico de estafas en Chile se ha trasladado masivamente a la mensajería de texto SMS (suplantación de BancoEstado, falsos partes de TAG, cuentas suspendidas de Netflix o paquetería retenida de CorreosChile):

1. **Recepción Multipart:** `SmsReceiver` intercepta el intent del sistema (`SMS_DELIVER_ACTION` o recepción de SMS), agrupando mensajes largos divididos en partes (PDU multipart) para evaluarlos como un cuerpo textual único.
2. **Heurística Léxica Local:** El motor analiza en el propio equipo:
   * **Remitentes alfanuméricos falsos:** Suplantación de nombres institucionales.
   * **Acortadores de URL y enlaces fraudulentos:** Presencia de dominios apócrifos no oficiales (`bit.ly`, enlaces `.xyz`, dominios de phishing imitando portales bancarios chilenos).
   * **Palabras clave de ingeniería social:** Patrones como *"clave internet suspendida"*, *"abono retenido"*, *"multa de tránsito impaga"*, *"actualiza tus datos de inmediato"*.
3. **Bandejas Organizadas:**
   * **Entrada:** Conversaciones personales y avisos legítimos.
   * **Salida:** Mensajes emitidos por el usuario.
   * **Spam y Estafas:** Mensajes maliciosos aislados y silenciados sin saturar la bandeja principal.
   * **Historial Completo:** Registro global auditado.

---

## 7. Búsqueda Unificada On-Device

En la pantalla de llamadas (`CallLogScreen`), la barra superior cuenta con un motor de búsqueda unificado que indexa simultáneamente tres fuentes de datos sin conectarse a internet:
1. **Historial Telefónico del Dispositivo:** Llamadas entrantes, salientes, perdidas y bloqueadas.
2. **Contactos del Teléfono:** Nombres y números de la libreta personal.
3. **Directorio Oficial Chileno:** Los más de 600 números comunales y regionales precargados.

Los resultados aparecen categorizados de inmediato con botones de marcado rápido (`ACTION_DIAL`), permitiendo encontrar y llamar a la seguridad municipal de tu comuna o a un hospital sin necesidad de buscar en navegadores web.

---

## 8. UX de Categorías y Pestañas Predeterminadas (Principio 1-N)

A diferencia de interfaces confusas con botones redundantes de "favorito" o "estrella", Corta implementa el estándar **SSOT (Single Source of Truth)** para la navegación por pestañas:

* **Control desde la Hoja `(+)`:** En Llamadas, Mensajes y Contactos, el botón `(+)` abre la gestión de categorías.
* **El Orden Visual Gobierna Todo:** Si mueves una categoría con los botones `↑ / ↓` a la primera posición (`índice == 0`), esa categoría pasa automáticamente a ser la primera pestaña visible en la pantalla y la **pestaña que se abrirá por defecto cada vez que entres a la aplicación**.
* **Insignia "Pestaña inicial":** Una etiqueta sutil y protegida visualmente confirma cuál es la categoría que abre la pantalla.

---

## 9. Seguridad, Criptografía y Privacidad Zero-Knowledge

### Protección Criptográfica en Reposo
* **Base de Datos SQLCipher:** Todos los registros, reglas de filtrado y cachés locales están cifrados con **AES-256**.
* **Gestión de Llaves en Hardware (Android Keystore):** La clave criptográfica no está quemada en el código fuente. Se genera y almacena en el módulo de seguridad del hardware del procesador del teléfono (**TEE / StrongBox**).
* **Blindaje contra Corrupción en Reinicio (Direct Boot Aware):** En caso de reinicio del dispositivo antes del primer desbloqueo, el sistema maneja la persistencia de forma segura evitando la regeneración de claves maestras que causaría corrupción de datos.

### Filosofía Zero-Knowledge y Reputación Comunitaria
* **Sin Grafo Social:** Corta no lee los nombres de tus familiares ni crea redes de relación.
* **Reportes con K-Anonymity:** Cuando la sincronización comunitaria de reportes se activa para usuarios colaboradores, los números sospechosos no se asocian a la identidad del usuario ni a su número de teléfono; se consolidan como valores criptográficos anonimizados por frecuencia estadística.

---

## 10. Sistema de Diseño e Interfaz (Material Design 3 & Stitch)

* **Tres Destinos Fundamentales:** La barra de navegación inferior contiene exclusivamente **Mensajes**, **Llamadas** y **Contactos**.
* **Separación de Identidad vs Configuración (Truecaller Pattern):**
  * El **avatar de usuario** abre exclusivamente la gestión de identidad, cuenta y aportes/donación.
  * El **icono de engranaje (Ajustes)** abre la configuración técnica: listas de bloqueo, modo estricto, diagnóstico del sistema y estado de SQLite.
* **Paleta 60-30-10 y Temas Stitch:** Compatibilidad total con **Monet (Dynamic Color)** y 4 variantes estilísticas de alto contraste (Amber, Purple, Coral, Sage).
* **OLED Dark Mode:** Negro absoluto (`#000000`) para máxima eficiencia energética en pantallas AMOLED.
* **Android 15 Edge-to-Edge:** Márgenes ergonómicos respetando `WindowInsets.safeDrawing` y áreas de toque mínimas de 48x48 dp.

---

## 11. Marco Legal y Cumplimiento Normativo en Chile

Corta App está concebida bajo el marco legal chileno vigente:
1. **Resolución Exenta 286 de SUBTEL:** Otorga el sustento técnico para el tratamiento y bloqueo del tráfico no solicitado bajo la numeración 809.
2. **Ley 19.496 (Protección de los Derechos de los Consumidores, Art. 28B):** Faculta a los consumidores a no recibir comunicaciones promocionales no deseadas. Corta asiste al usuario en el ejercicio material de este derecho.
3. **Ley 19.628 (Protección de la Vida Privada):** Al no recolectar, almacenar ni transferir datos personales o libretas de direcciones a servidores externos, Corta cumple por diseño con los más altos estándares de privacidad de datos personales.

---

## 12. Guía de Compilación y Distribución

El proyecto cuenta con dos variantes de build principales:

### Comandos de Compilación
```bash
# 1. Compilación de desarrollo (con herramientas de depuración e inspección)
./gradlew :composeApp:assembleDebug

# 2. Compilación de producción (optimizada con R8, 14 MB, sin debuggable)
./gradlew :composeApp:assembleRelease

# 3. Ejecución de pruebas unitarias y de arquitectura
./gradlew testDebugUnitTest

# 4. Instalación de versión Release en dispositivo físico conectado vía ADB
adb install -r composeApp/build/outputs/apk/release/composeApp-release.apk
```

### Métricas de Rendimiento del APK Release
* **Peso final:** ~14 MB (reducido desde ~40 MB gracias a R8, ProGuard y stripping de recursos).
* **Latencia de evaluación SQLite:** < 0.1 ms en hardware real.
* **Consumo de memoria en reposo:** Inferior a 25 MB de RAM.
* **Consumo de batería:** Inexistente en reposo; se despierta únicamente ante eventos de llamada o SMS entrantes gestionados por el kernel de Android.

---
*Documento informativo y técnico del proyecto Corta App.*  
*Versión de referencia: v1.3 (Octubre 2026).*
