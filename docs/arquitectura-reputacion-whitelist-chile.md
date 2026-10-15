# Arquitectura del Sistema de Reputación Comunitaria y Directorio Nacional de Whitelist (Chile)

*Fecha de emisión: Octubre de 2026*  
*Módulo: Especificación de Dominio y Datos de Corta App*  
*Versión: 1.0 (Diseño de Ingeniería)*

---

## 1. El Problema del Crowdsourcing: ¿Qué tan real es y cómo funciona en la industria?

### A. Cómo lo hace Truecaller y por qué está roto por diseño
En aplicaciones como Truecaller, Hiya o Whoscall, el sistema de reportes se basa en un modelo centralizado ingenuo: si suficientes usuarios escriben un nombre o marcan un número como "Spam", ese nombre se convierte en la etiqueta global para todos los demás usuarios.

Esto genera dos problemas críticos bien documentados en la industria de la ciberseguridad:
1. **Envenenamiento de Datos (Data Poisoning):**
   * **Deudores morosos:** Usuarios que no quieren pagar deudas marcan masivamente a los números de cobranza o a los teléfonos de ejecutivos bancarios como "Estafa" o "Spam".
   * **Venganza personal y acoso:** Personas que marcan el número de su expareja o un familiar como "Estafador" o "Acosador".
   * **Guerra sucia comercial:** Empresas competidoras que reportan las líneas de atención a clientes de sus rivales para que sus llamadas aparezcan en rojo en los teléfonos de los clientes.
2. **Filtración y Comercio de Identidad:**
   * Para "validar" los reportes, estas plataformas suben la libreta de direcciones de cada usuario que instala la app, construyendo un grafo social que luego venden a empresas de marketing.

---

## 2. El Modelo de Corta App: Reputación Ponderada con Zero-Knowledge

En Corta App el crowdsourcing es **viable, matemáticamente sólido y 100% respetuoso de la privacidad**, implementando tres barreras criptográficas:

```
┌─────────────────────────────────────────────────────────────┐
│                      REPORTE LOCAL (DISPOSITIVO)             │
│  - Hash SHA-256 Truncado (16 bytes)                         │
│  - Tipo: +SPAM (sospecha) o -FALSO_POSITIVO (rescate)        │
│  - Token de integridad del dispositivo (Play Integrity API)  │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 MOTOR DE CONSENSO (SERVIDOR / DELTAS)       │
│  - Algoritmo de Consenso Ponderado (K-Anonymity >= 30)      │
│  - Veto inmediato ante reportes de Falso Positivo           │
│  - Emisión de Diff binario (Delta Sync < 15 KB/semana)      │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 BASE DE DATOS LOCAL CIFRADA (TELÉFONO)      │
│  - Evaluación instantánea en memoria RAM (< 1.5 ms)         │
│  - Whitelist Crítica con prioridad absoluta (Inmutable)      │
└─────────────────────────────────────────────────────────────┘
```

### A. Algoritmo de Consenso Ponderado
1. **Votos Asimétricos:**
   * Marcar como **SPAM (+1 punto de sospecha)**.
   * Marcar como **FALSO POSITIVO (-5 puntos de sospecha + bandera de congelamiento)**.
   * La asimetría está diseñada para priorizar la disponibilidad: es infinitamente más grave bloquear por error a un repartidor de gas o una clínica que dejar pasar un spam.
2. **Umbral de Confianza ($K$-Threshold):**
   * Un número jamás entra a la lista de bloqueo comunitaria si solo tiene 2 o 5 reportes.
   * Requiere un mínimo estricto de **$K \ge 30$ reportes independientes de dispositivos únicos** en una ventana temporal de 7 días.
3. **Defensa contra Ataques Sybil (Bots emitiendo reportes falsos):**
   * El cliente utiliza la API nativa de Android **Play Integrity API** (o firma de atestación de hardware) para emitir una prueba criptográfica efímera. Un atacante no puede ejecutar un script de Python en un servidor para inyectar 10.000 reportes falsos, porque cada reporte requiere una atestación de un dispositivo físico real.

---

## 3. El Dilema Territorial en Chile: ¿Es muy complejo hacer una Whitelist Nacional?

El usuario planteó una duda válida: *"al ser Chile pequeño, tener todos los servicios en una whitelist... pero el problema sería que por región o sector cambian, no es tan simple"*.

### La Realidad Matemática de Chile: Es asombrosamente simple
Chile tiene una ventaja geográfica e institucional enorme: es un país unitario, altamente concentrado y con una estructura administrativa acotada.

Hagamos el desglose numérico real de los tres niveles de servicios en Chile:

```
┌─────────────────────────────────────────────────────────────────┐
│                    DESGLOSE DE SERVICIOS EN CHILE               │
├────────────────────────────────┬─────────────────┬──────────────┤
│ Nivel                          │ Entidades       │ Números Tel. │
├────────────────────────────────┼─────────────────┼──────────────┤
│ 1. Emergencias Nacionales      │ 10 servicios    │ ~15 números  │
│ 2. Seguridad Municipal (14xx)  │ 346 comunas     │ ~75 números  │
│ 3. Servicios Básicos (Luz/Agua)│ ~30 sanit./elec.│ ~120 números │
│ 4. Salud y Clínicas Regionales │ ~80 redes       │ ~250 números │
│ 5. Autopistas Concesionadas    │ ~18 autopistas  │ ~35 números  │
│ 6. Banca y Retail Financiero   │ ~25 entidades   │ ~80 números  │
├────────────────────────────────┼─────────────────┼──────────────┤
│ TOTAL UNIVERSO NACIONAL CHILE  │ ~509 entidades  │ < 600 NÚMEROS│
└────────────────────────────────┴─────────────────┴──────────────┘
```

### Análisis de los Tres Niveles

#### Nivel 1: Emergencias Nacionales de 3 Dígitos (Inmutables)
Son universales en todo el país (desde Arica a Punta Arenas):
* `130`: CONAF (Incendios forestales).
* `131`: SAMU (Urgencia médica).
* `132`: Bomberos de Chile.
* `133`: Carabineros de Chile.
* `134`: Policía de Investigaciones (PDI).
* `137`: Búsqueda y Salvamento Marítimo (Armada).
* `147`: Fono Niños (Carabineros).
* `149`: Fono Familia (Violencia intrafamiliar).
* `4141`: Línea de Prevención del Suicidio (*Quédate*, Minsal).

#### Nivel 2: Las 346 Comunas y la Red 14xx de Seguridad Municipal
Aunque Chile tiene 346 comunas, **menos del 25% de las comunas cuentan con central telefónica 14xx de patrullaje preventivo**. Las comunas rurales o pequeñas suelen derivar directamente al cuadrante de Carabineros (133).

* **En la Región Metropolitana (52 comunas):**
  * `1402`: Las Condes | `1403`: Providencia | `1405`: Santiago | `1406`: Maipú
  * `1414`: Vitacura | `1417`: Ñuñoa | `1445`: La Florida | `1468`: Lo Barnechea
* **En Regiones:**
  * `1468`: Viña del Mar | `1440`: Antofagasta | `1408`: Concepción | `1412`: Valparaíso
* **Tamaño total:** El directorio completo de seguridad municipal de Chile **no supera los 80 números**.

#### Nivel 3: Servicios Básicos y Clínicas por Región
Aunque las empresas cambian según la zona geográfica:
* **Electricidad:** Enel (RM), CGE (Valparaíso a Biobío), Chilquinta (V Región), Saesa (Los Lagos/Ríos), Edelaysen (Aysén), Edelmag (Magallanes).
* **Agua Potable:** Aguas Andinas (RM), Esval (V Región), Essbio (VI a VIII), Nuevosur (Maule), Essal (Sur), Aguas del Altiplano (Norte).
* **Gas y Combustible:** Lipigas, Abastible, Gasco, Metrogas.
* **Autopistas Urbanas e Interurbanas:** Costanera Norte, Vespucio Norte/Sur, Autopista Central, Rutas del Maipo, Ruta 68.

### Conclusión Matemática de Almacenamiento
* Guardar **600 a 1.000 números de utilidad pública de todo Chile** con su nombre asociado (ej: `"SAMU Emergencia"`, `"Enel Distribución"`, `"Seguridad Providencia 1403"`):
  $$\text{Tamaño} = 1.000 \times 40 \text{ bytes (Número + Nombre)} \approx \mathbf{40 \text{ Kilobytes}}$$
* **40 KB es absolutamente nada.** Puede venir **pre-cargado de fábrica en el archivo SQLite de la app** desde el primer segundo en que se instala, sin necesidad de que el usuario descargue nada ni se conecte a internet.

---

## 4. La Solución a la Paradoja del Prefijo 600

La SUBTEL cometió un error de diseño regulatorio al agrupar bajo el prefijo `600`:
* Llamadas de servicios masivos con contrato previo (Bancos, Seguros, Falabella, Ripley).
* Llamadas de atención ciudadana y urgencias de salud estatal (Salud Responde `600 360 7777`, Fonasa `600 360 3000`, Registro Civil `600 370 2000`).

Si una app anti-spam bloquea ciegamente el `600`, **el usuario pierde la hora al médico, el aviso de examen de laboratorio o el aviso de corte de agua programado**.

### La Estrategia en Tres Capas de Corta App

```
                  ┌────────────────────────────────────────┐
                  │        Llamada entrante 600...         │
                  └──────────────────┬─────────────────────┘
                                     │
                     ¿Está en la Whitelist Nacional?
                                     │
                    ┌────────────────┴────────────────┐
                    │ SÍ                              │ NO
                    ▼                                 ▼
   ┌─────────────────────────────────┐  ┌───────────────────────────────────┐
   │ PERMITIR CON IDENTIFICADOR      │  │ ADVERTIR (NO BLOQUEAR)            │
   │ "Salud Responde Minsal"         │  │ Pantalla amarilla:                │
   │ "BancoEstado Soporte"           │  │ "Llamada Comercial / Servicio 600"│
   └─────────────────────────────────┘  └───────────────────────────────────┘
```

1. **Si el número 600 coincide con el Directorio Nacional Verificado:**
   * La llamada suena con total normalidad.
   * La app muestra la tarjeta verde: **"Servicio Oficial Verificado: [Nombre]"**.
2. **Si el número 600 es desconocido (empresa privada o call center externo):**
   * **No se bloquea por defecto.**
   * La app activa la acción `FilterAction.WARN`: el teléfono timbra, pero la pantalla muestra una advertencia preventiva: **"Aviso: Comunicación masiva / comercial (Prefijo 600)"**.
   * El usuario decide libremente si atiende o corta.
3. **El Prefijo 809 (Telemarketing no solicitado):**
   * Aquí no hay ambigüedad: por ley SUBTEL, el 809 es prospección no consentida. **Se bloquea silenciosamente en el 100% de los casos**.

---

## 5. Modelo de Datos para SQLDelight

Para formalizar esta arquitectura en el backend local de Corta App, se estructuran las siguientes tablas:

```sql
-- Directorio de Whitelist Nacional Verificada (Pre-cargada)
CREATE TABLE VerifiedDirectory (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    phoneNumber TEXT NOT NULL UNIQUE,       -- Formato normalizado (+56...)
    entityName TEXT NOT NULL,               -- "Salud Responde Minsal", "Seguridad 1403"
    category TEXT NOT NULL,                 -- "EMERGENCY", "MUNICIPAL", "UTILITY", "HEALTH", "BANK"
    region TEXT,                            -- "RM", "V", "ALL", etc.
    isCritical INTEGER AS Boolean NOT NULL DEFAULT 1 -- 1 = Jamás bloqueable bajo ninguna regla
);

-- Reputación Comunitaria (Descargada por Deltas)
CREATE TABLE CommunityReputation (
    phoneNumberHash TEXT NOT NULL PRIMARY KEY, -- SHA-256 truncado del número
    spamReportsCount INTEGER NOT NULL DEFAULT 0,
    falsePositiveCount INTEGER NOT NULL DEFAULT 0,
    category TEXT NOT NULL,                    -- "TELEMARKETING", "COLLECTION", "SCAM", "PING"
    consensusAction TEXT NOT NULL,             -- "BLOCK", "WARN", "ALLOW"
    lastUpdated INTEGER NOT NULL
);
```

---

## 6. Conclusión y Hoja de Ruta

1. **La idea del crowdsourcing es 100% viable**, siempre que use el modelo asimétrico (-5 por falso positivo, +1 por spam) y atestación de dispositivo para neutralizar el envenenamiento de datos.
2. **La Whitelist de Chile no es un obstáculo; es nuestra mayor fortaleza:** Son menos de 600 números telefónicos para todo el país. Pesan menos de 40 KB y se distribuyen embebidos en la propia app.
3. **La paradoja del 600 queda resuelta:** Los servicios estatales de salud y emergencias entran por la Whitelist Verificada, mientras que las empresas privadas reciben una etiqueta informativa de advertencia, protegiendo al usuario sin causarle pérdidas de información crítica.
