# CortaApp Design System (Material Design 3)
> **Directrices de Diseño, Identidad Visual y Experiencia de Usuario (UX/UI)**
> *Última actualización: Octubre 2026*

---

## 1. Identidad de Marca y Carácter

### El Arquetipo: El Guardián Silencioso
CortaApp no es un software ruidoso ni un panel de control circense. Es una herramienta sobria, confiable y técnica, diseñada para proteger la tranquilidad del usuario sin interrumpir su vida cotidiana.

### Slogan Oficial: *«Con Corta la haces corta»* (¡Corta la hace corta!)
Doble sentido popular chileno: *hacerla corta* (ir al grano, solucionar el problema sin rodeos) y *cortar* (colgar la llamada basura y eliminar el spam de raíz).

### La Regla de los Tres Adjetivos
Todo componente, pantalla, interacción y texto en la aplicación debe cumplir con tres principios innegociables:
1. **Silenciosa:** Si la aplicación hace bien su trabajo, el usuario casi olvida que está instalada. No envía notificaciones innecesarias ni interrupciones visuales de autobombo.
2. **Inquebrantable:** Bloquea de forma certera el spam agresivo y protege con absoluta prioridad la lista blanca de emergencias (131, 132, 133, 14xx).
3. **Privada:** Transmite seguridad y transparencia absoluta: la agenda de contactos y el registro de llamadas nunca salen del dispositivo (Zero-Knowledge).

---

## 2. Paleta de Color: La Regla 60-30-10

Para garantizar jerarquía visual sin fatiga cognitiva, la interfaz sigue estrictamente la proporción armónica:

```
┌─────────────────────────────────────────────────────────────┐
│ 60% NEUTROS / SUPERFICIE (Fondos M3, Containers, Tarjetas)   │
├───────────────────────────────┬─────────────────────────────┤
│ 30% PRIMARIO / ESTRUCTURAL    │ 10% ACENTO / ESTADO CRÍTICO │
│ (Barras, Textos clave, Tabs)  │ (CTAs, Bloqueos, Escudo)    │
└───────────────────────────────┴─────────────────────────────┘
```

* **60% Superficies Neutras (`surfaceContainer`):**
  * Modo Oscuro: Fondos profundos `#111318` y `#1A1C1E` con bordes sutiles.
  * Modo Claro: Blanco cálido `#FAFCFC` con contraste de superficie para reducir destellos.
* **30% Estructural / Primario:** Elementos que anclan la lectura (TopAppBar, textos de títulos, divisores sutiles de 0.5 dp).
* **10% Acento y Estados Semánticos:**
  * **Protección Activa / Aprobado:** Tonos azules y esmeralda profesionales.
  * **Spam Bloqueado / Alerta de Estafa:** Tonos de error M3 (`errorContainer` y `onErrorContainer`) destacados sin saturar la pantalla.

### Temas Stitch Disponibles
Además del color dinámico del sistema (Android 12+ Monet), la app integra 4 esquemas de acento:
* **STITCH_AMBER (Llamadas / Día):** Azul profesional (`#005EB6`) con acentos ámbar cálidos.
* **STITCH_PURPLE (Llamadas / Noche):** Lavanda suave (`#ADC6FF`) sobre fondo negro profundo (`#111318`).
* **STITCH_CORAL (Mensajes / Noche):** Azul cielo sobre gris profundo (`#1A1C1E`) con acento coral para smishing.
* **STITCH_SAGE (Contactos / Dual):** Azul clásico con acentos verde salvia (`#4A6B4E`) que denotan seguridad y calma.

---

## 3. Jerarquía y Arquitectura de Navegación

Siguiendo la **Ley de Hick** de UX para minimizar la sobrecarga de decisión, la aplicación organiza su flujo en **3 destinos principales en la barra inferior (NavigationBar)**:

```
               ┌─────────────────────────────────┐
               │         TopAppBar Limpia        │
               │  [Menú / Escudo]    [Búsqueda]  │
               ├─────────────────────────────────┤
               │                                 │
               │        Contenido Principal      │
               │                                 │
               ├─────────────────────────────────┤
               │       NavigationBar (Bottom)    │
               │  [MENSAJES] [LLAMADAS] [CONTACT]│
               └─────────────────────────────────┘
```

1. **MENSAJES (SmsScreen):** Lista de conversaciones SMS con pestañas simples: *Bandeja de Entrada* y *Mensajes Bloqueados*.
2. **LLAMADAS (CallLogScreen):** Historial del sistema integrado con las llamadas interceptadas y opciones de bloqueo en un toque.
3. **CONTACTOS (ContactsScreen):** Agenda local del usuario con índice alfabético rápido y búsqueda instantánea.

*Los destinos de configuración, reglas SUBTEL y estadísticas se gestionan desde el menú superior lateral (NavigationMenuSheet) para no saturar la navegación primaria.*

---

## 4. Tipografía e Iconografía Unificada

* **Tipografía:** Escala nativa de **Material 3 Typography** (`headlineSmall`, `titleLarge`, `bodyLarge`, `labelMedium`).
  * *Regla técnica:* No aplicar multiplicadores algorítmicos manuales que desborden los contenedores en dispositivos con zoom de pantalla habilitado.
* **Iconografía:** Uso estricto de **Google Material Symbols Rounded**:
  * Pestaña o botón seleccionado: **Filled/Rounded** (`Icons.Rounded.*`).
  * Pestaña inactiva: **Outlined** (`Icons.Outlined.*`).
  * Íconos bidireccionales: Usar variantes **AutoMirrored** (`Icons.AutoMirrored.Rounded.*`) para llamadas entrantes/salientes y flechas de navegación.
  * Targets táctiles: Todo botón de interacción mide como mínimo **48x48 dp** para accesibilidad universal.
