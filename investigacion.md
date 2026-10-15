# Arquitectura Técnica y Marco Regulatorio para el Desarrollo de Sistemas de Filtrado de Comunicaciones en Chile: Un Enfoque en Privacidad, Seguridad y Rendimiento On-Device

*Fecha de actualización: Octubre de 2026*  
*Versión: 2.0 (Marco Integral Local-First & Zero-Knowledge)*

---

## 1. Introducción y Estado del Ecosistema en Chile

La proliferación de comunicaciones no deseadas en el territorio chileno se ha consolidado como un problema de seguridad pública e invasión de la privacidad. Con un promedio que supera las 25 llamadas mensuales de spam por usuario activo, Chile se posiciona como uno de los países con mayor presión telefónica comercial en Latinoamérica. 

Frente a este escenario, las soluciones tradicionales (como Truecaller u operadores cloud extranjeros) han implementado modelos depredadores de privacidad basados en extraer, cruzar y monetizar las libretas de direcciones de sus usuarios. **Corta App** plantea un paradigma opuesto: **procesamiento 100% on-device (local-first)**, garantizando que la agenda jamás abandone el hardware del teléfono mientras se intercepta y neutraliza el spam con latencia inferior a 2 milisegundos.

---

## 2. La Tesis Matemática del Almacenamiento Local para Chile

El plan de numeración telefónica en Chile (+56 9 XXXX XXXX para móviles) posee características estructurales ideales para una arquitectura sin nube en tiempo real:

### A. Dimensión del Espacio Numérico
* **Espacio Teórico Móvil (+56 9):** $10^8$ combinaciones posibles (100 millones de números posibles).
* **Parque Activo Real:** Las líneas móviles activas en Chile oscilan entre **26 y 28 millones** (cifras oficiales de SUBTEL).
* **El Universo Activo de Spam:** Ninguna empresa de spam o cobranza opera con millones de líneas en simultáneo. El conjunto de números que genera el 95% del telemarketing agresivo, cobranzas masivas y estafas telefónicas en Chile se concentra en una ventana móvil de **100.000 a 400.000 números activos recurrentes**.

### B. Huella de Memoria y Cero Latencia
* **Almacenamiento Binario Directo:**
  * Representar un número telefónico de 9 dígitos como un entero de 64 bits (`BIGINT` / `Long`) consume exactamente **8 bytes**.
  * 300.000 números telefónicos ocupan:
    $$\text{Tamaño} = 300.000 \times 8 \text{ bytes} = 2.4 \text{ MB}$$
  * Agregando 1 byte de categoría (`0=Telemarketing`, `1=Cobranza`, `2=Estafa`, `3=PingCall`) y 1 byte de score de reputación:
    $$\text{Tamaño total} = 300.000 \times 10 \text{ bytes} \approx 3.0 \text{ MB}$$
* **Filtros Probabilísticos de Bloom en RAM:**
  * Para una tasa de falsos positivos $p = 0.001$ (0.1%), un Filtro de Bloom requiere aproximadamente $14.4$ bits por elemento.
  * Para 300.000 números:
    $$\text{RAM} = 300.000 \times 14.4 \text{ bits} \approx 4.32 \text{ Megabits} = \mathbf{540 \text{ Kilobytes}}$$
* **Garantía Operativa:**
  * La consulta en memoria local toma **menos de 1.5 milisegundos**, cumpliendo con holgura el límite estricto de 5 segundos impuesto por Android `CallScreeningService`.
  * La app opera con efectividad total sin conexión de red: bajo túneles, en el Metro de Santiago, en zonas rurales o en equipos sin saldo de datos móviles.

---

## 3. Población de Datos con Privacidad Absoluta (Zero-Knowledge)

> *Para el diseño formal del algoritmo de consenso y el desglose de los 600 números de la Whitelist Nacional, ver [docs/arquitectura-reputacion-whitelist-chile.md](file:///home/axioma/Documentos/Proyectos/Corta%20App/v1/docs/arquitectura-reputacion-whitelist-chile.md).*

La alimentación de la base de datos se rige por un modelo de sincronización desacoplado:

### 1. Sincronización Diferencial Unidireccional (Delta Sync)
* El teléfono **nunca** consulta una API externa cuando entra una llamada o mensaje.
* El servidor central compila y distribuye una vez al día o cada semana un archivo diferencial binario (diff) con los nuevos números confirmados y las revocaciones de números dados de baja.
* Un delta típico con 1.500 números nuevos pesa menos de **15 KB** comprimido, descargándose en segundo plano vía `WorkManager` con consumo nulo de batería.

### 2. Reportes Comunitarios con Privacidad Diferencial ($K$-Anonymity)
* Cuando un usuario decide reportar un número desde el historial de Corta:
  * No se envía el número en texto plano, ni la identidad del usuario, ni su IMEI/IP.
  * Se genera un **hash SHA-256 truncado** del número normalizado.
  * El backend de agregación no añade un número a la lista comunitaria oficial hasta que acumula reportes de al menos **$K \ge 25$ usuarios independientes** dentro de una ventana de tiempo definida.

### 3. Fuentes Regulatorias y Whitelist Crítica (Chile)
* **Lista oficial SUBTEL:** Prefijos obligatorios 809 y 600.
* **Integración con SERNAC:** Registro de reclamos públicos del portal "No Molestar".
* **Blindaje Vital Inmutable (Whitelist):**
  * Números de auxilio nacional: 130 (CONAF), 131 (SAMU), 132 (Bomberos), 133 (Carabineros), 134 (PDI), 137 (Búsqueda marítima), 147 (Fono Niños), 149 (Fono Familia).
  * Líneas 14xx de Seguridad Municipal de Chile (1402, 1403, 1406, 1414, 1416, 1417, 1418, 1419, 1445, 1468, etc.).
  * Servicios de salud y básicos esenciales: 600 360 7777 (Salud Responde), Registro Civil, bancos públicos y utilidades esenciales (Enel, CGE, Aguas Andinas, Metrogas).

---

## 4. Las Tres Vertientes del Spam en Chile

| Categoría | Infraestructura Técnica | Patrón de Ataque | Neutralización en Corta App |
| :--- | :--- | :--- | :--- |
| **Telemarketing Agresivo** *(Telcos, Seguros)* | Troncales SIP y tramas E1 con rangos numéricos correlativos (series fijas o móviles consecutivas). | Llamadas comerciales en días y horarios hábiles ofreciendo portabilidad o tarjetas. | **Bloqueo por bloques de prefijo.** Coincidencia en Trie $O(L)$ para series completas asignadas a call centers. |
| **Cobranza Extrajudicial** *(Empresas de cobranza)* | Discadores predictivos que rotan números SIM pre-pago constantemente para eludir bloqueos. | Ráfagas de llamadas cada 20 minutos; excluidas por la Corte Suprema de los prefijos 809/600. | **Actualizaciones diferenciales frecuentes (Deltas)** y detección de patrones de insistencia. |
| **Estafas y Suplantación** *(Fraude carcelario / bandas)* | Suplantación de ejecutivos bancarios, "cuento del tío", spoofing de identificadores locales. | Manipulación psicológica agresiva; llamadas dirigidas a adultos mayores. | **Tolerancia cero comunitaria.** Etiquetado visible de *ALERTA DE FRAUDE* con sonido silenciado. |

* **Llamadas Fantasma / Ping Calls:** Discadores que cortan a los 2 segundos para validar si la línea tiene un humano disponible. La app permite clasificar llamadas menores a 3 segundos de desconocidos con un toque.

---

## 5. Marco Regulatorio Nacional en Régimen (2025 - 2026)

### Resolución Exenta 286 de SUBTEL
En régimen definitivo en todo Chile:
* **Prefijo 809:** Comunicaciones masivas comerciales no solicitadas. El sistema lo trata con regla por defecto de **BLOQUEO AUTOMÁTICO SILENCIOSO**.
* **Prefijo 600:** Comunicaciones masivas solicitadas o contractuales. El sistema lo trata como **ADVERTIR / IDENTIFICAR** para evitar la pérdida de turnos médicos o avisos bancarios legítimos.

### Jurisprudencia de la Corte Suprema (Cobranzas)
El máximo tribunal determinó que las llamadas de cobranza extrajudicial no constituyen "publicidad masiva ni venta de productos", por lo que no están obligadas a operar bajo el prefijo 809. Por ende, Corta App mantiene un módulo de configuración independiente para cobranzas y respeta los plazos legales del SERNAC ("No me hostiguen cobranzas").

### Ley 19.496 (SERNAC "No Molestar", Art. 28B)
El usuario tiene el derecho irrenunciable a suspender llamadas promocionales. Tras la solicitud en la plataforma oficial del SERNAC, rige un plazo de **7 días hábiles** de gracia. La app registra la evidencia de llamadas recibidas con posterioridad al séptimo día para sustentar denuncias formales ante el SERNAC.

---

## 6. Convergencia Arquitectónica con Apple iOS (CallKit)

Una de las mayores ventajas de haber diseñado Corta App con arquitectura **Local-First** es la compatibilidad conceptual absoluta con el ecosistema de Apple:
* **La Restricción de iOS:** A diferencia de Android, el framework **CallKit de Apple prohíbe de forma estricta cualquier petición de red durante una llamada**.
* **`CXCallDirectoryExtension`:** Apple exige que la aplicación entregue una lista binaria de números telefónicos ordenados e indexados previamente guardados en la memoria del dispositivo.
* **Resultado:** Las apps dependientes de servidores cloud fallan en iOS o requieren reingeniería total. Corta App ya utiliza la estructura exacta de datos en SQLite/KMP que exige CallKit, garantizando una futura versión de iPhone con mínima fricción.

---

## 7. Comparativa de Mercado: Corta App vs. Truecaller

| Criterio | Truecaller / Clones Tradicionales | Corta App (Chile Local-First) |
| :--- | :--- | :--- |
| **Privacidad de la Agenda** | Sube y monetiza los contactos de los usuarios en servidores foráneos. | **Zero-Knowledge:** La agenda jamás sale del procesador del teléfono. |
| **Batería y Consumo de Datos** | Consume batería y datos móviles realizando peticiones HTTP en cada llamada. | **0 datos por llamada.** Búsqueda instantánea en microsegundos en la memoria local. |
| **Calibración Local** | Algoritmos genéricos internacionales que desconocen la legislación chilena. | **Especializada en Chile:** Calibrada según la Res. 286 SUBTEL, bancos locales y SERNAC. |
| **Resiliencia Operativa** | Falla o añade latencia en subterráneos, zonas rurales o sin cobertura 4G/5G. | Opera al 100% en modo avión, túneles, subterráneos o sin saldo. |
| **Cumplimiento Legal** | Conflictos normativos constantes con autoridades de protección de datos. | Diseñada bajo el estándar de **Privacy by Design** (Ley 19.628 de Chile). |
