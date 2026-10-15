# Modelo de Gobernanza de Datos, Blindaje Legal y Pipeline de Curaduría (Chile)

*Fecha de emisión: Octubre de 2026*  
*Módulo: Operaciones, Legal Compliance y Mantenimiento de Corta App*  
*Versión: 1.0*

---

## 1. Identidad y Slogan Oficial: La Conexión Cultural Chilena

### El Slogan: *"Con Corta la haces corta"* (o *"¡Corta la hace corta!"*)
El nombre de la aplicación y su propuesta de valor convergen de manera perfecta con el modismo chileno:
* **"Hacerla corta":** Ir directo al grano, solucionar un problema sin rodeos, no perder el tiempo.
* **"Corta!":** Cortar de inmediato la llamada fraudulenta, colgar el teléfono al spam, cortar de raíz la invasión de la privacidad.

Este slogan se adopta como el claim de marca oficial en onboarding, tiendas de aplicaciones y campañas comunitarias.

---

## 2. Marco Legal en Chile y Blindaje de Responsabilidad

Al operar una herramienta que clasifica e intercepta telecomunicaciones, los desarrolladores debemos cumplir con la legislación chilena vigente:

### A. Sustento Normativo Oficial
1. **Resolución Exenta 286 de SUBTEL (2025-2026):**
   * El bloqueo del prefijo **809** no es un capricho del desarrollador; es la ejecución directa de la normativa oficial de telecomunicaciones que clasifica a estas llamadas como *comunicación masiva no solicitada*.
   * La app actúa como un ejecutor tecnológico de la voluntad regulatoria del Estado de Chile.
2. **Ley 19.496 sobre Protección de los Derechos de los Consumidores (Art. 28B):**
   * El marco de "No Molestar" faculta a los ciudadanos a no ser contactados. Corta App asiste al consumidor en el ejercicio de este derecho.
3. **Ley 19.628 sobre Protección de la Vida Privada:**
   * Al adoptar una arquitectura **Zero-Knowledge (On-Device)**, Corta App **no recolecta, no comercializa y no almacena datos de carácter personal en servidores externos**. La libreta de contactos y el registro de llamadas nunca salen del equipo físico, lo que exime a la app de riesgos de filtración masiva de bases de datos.

### B. Términos de Servicio y Cláusula de Exención de Responsabilidad (Disclaimer)
Para proteger legalmente a los desarrolladores frente a eventuales reclamos de empresas de telemarketing o usuarios:
* **Naturaleza Asistencial:** La aplicación se define como una *"herramienta de asistencia y filtrado basada en heurísticas locales, normas públicas y reportes comunitarios"*.
* **Control del Usuario:** El usuario mantiene siempre la potestad soberana sobre su teléfono. La app cuenta con una lista blanca personal donde el usuario puede desbloquear o permitir cualquier número en cualquier momento.
* **Servicios de Emergencia:** Se establece formalmente que la aplicación incluye un blindaje inmutable de fábrica que previene el bloqueo de servicios de auxilio y seguridad nacional (131, 132, 133, 14xx).

---

## 3. Protocolo de Deslistado y Derecho a Réplica (Right of Delisting)

Para evitar disputas legales con empresas legítimas o instituciones que hayan sido reportadas erróneamente por la comunidad:
1. **Canal Público de Verificación:**
   * Se habilita un canal oficial (`contacto@corta.app` o portal web simple `corta.app/verificar`).
2. **Procedimiento de Acreditación:**
   * Cualquier entidad comercial o estatal puede solicitar la verificación de sus números telefónicos acreditando su personería jurídica y RUT de empresa.
3. **SLA de Resolución (24 a 48 Horas):**
   * Si una empresa acredita que sus números corresponden a servicios contratados legítimos (prefijo 600 o números fijos de soporte), se incorpora de inmediato a la **Whitelist Verificada** en el siguiente delta de actualización.

---

## 4. El Pipeline de Curaduría y Actualización Continua (Developer Pipeline)

¿Cómo mantenemos los desarrolladores la base de datos siempre limpia, fidedigna y actualizada?

```
┌─────────────────────────────────────────────────────────────┐
│ 1. INGESTIÓN DE FUENTES OFICIALES Y REPORTES                │
│  - Planes de numeración y asignaciones de bloques SUBTEL    │
│  - Reclamos públicos consolidados SERNAC                    │
│  - Reportes comunitarios validados (K-Anonymity >= 30)      │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. CONTROL DE CALIDAD AUTOMATIZADO (CI/CD QUALITY GATE)      │
│  - Test de Colisión con Whitelist Vital (131, 132, 14xx...) │
│  - Normalización E.164 simétrica (+56 9, +56 2, 809, 600)  │
│  - Detección de Falsos Positivos congelados                 │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. FIRMA DIGITAL CRIPTOGRÁFICA (Ed25519)                    │
│  - El archivo binario de deltas es firmado por los devs     │
│  - El teléfono verifica la firma antes de aplicar cambios   │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. DISTRIBUCIÓN LIGERA (DELTA SYNC)                         │
│  - Archivo comprimido < 15 KB distribuido por CDN / GitHub  │
│  - El teléfono descarga y aplica diff en menos de 100 ms    │
└─────────────────────────────────────────────────────────────┘
```

### A. Test de Colisión con la Whitelist Vital (Quality Gate)
En el pipeline de desarrollo (GitHub Actions o servidor local), antes de compilar y publicar un nuevo delta binario, se ejecuta un test automatizado obligatorio:
* Si algún número del delta coincide con un número de la lista blanca crítica (SAMU 131, Bomberos 132, Carabineros 133, líneas 14xx municipales, servicios básicos o códigos 2FA conocidos), **el build se detiene de inmediato con error crítico (`FATAL: Whitelist collision detected`)** y se bloquea la publicación.

### B. Firma Criptográfica de Deltas (Seguridad contra MitM)
* Los deltas binarios se firman digitalmente con una clave privada **Ed25519** en posesión exclusiva de los desarrolladores.
* La aplicación en el teléfono contiene la clave pública embebida en su código compilado.
* Si un atacante intercepta la conexión o intenta inyectar números maliciosos en la red, el teléfono descarta la actualización por fallo de firma digital.

---

## 5. Resumen de Gobernanza

1. **Responsabilidad Legal Blindada:** Apoyada en la Resolución 286 de SUBTEL, la Ley del Consumidor y el diseño Zero-Knowledge (Ley 19.628).
2. **Proceso de Apelación Transparente:** Las empresas legítimas tienen un canal expedito para acreditarse y entrar a la lista blanca oficial.
3. **Calidad de Datos Garantizada por Código:** Ningún número de emergencia o servicio esencial puede ser bloqueado accidentalmente gracias al test de colisión del pipeline.
4. **Cultura Chilena en el ADN:** *"Con Corta la haces corta"*, un software que no da vueltas, respeta el tiempo del usuario y soluciona el problema de raíz.
