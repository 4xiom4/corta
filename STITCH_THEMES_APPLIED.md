# Stitch Themes Applied to Corta v1

## Overview
Se han aplicado exitosamente 4 nuevos esquemas de color basados en los prototipos de Stitch al proyecto Corta. Estos temas están disponibles en la pantalla de Configuración.

## Temas Implementados

### 1. **STITCH_AMBER** 🌅
**Tipo**: Light/Day variant (Llamadas - Light Amber)
- **Primary**: `#005EB6` (Azul profesional)
- **Primary Container**: `#5F9EFB` (Azul claro)
- **Surface**: `#FAFCFC` (Blanco cálido)
- **On Surface**: `#2F3337` (Texto oscuro)
- **Tertiary**: `#705D31` (Ámbar/Marrón)
- **Tertiaryainer**: `#FFE0A8` (Ámbar claro)

**Use Case**: Ideal para pantalla de historial de llamadas durante el día

---

### 2. **STITCH_PURPLE** 💜
**Tipo**: Dark/Night variant (Llamadas - Dark Purple)
- **Primary**: `#ADC6FF` (Azul lavanda claro)
- **Primary Container**: `#004786` (Azul profundo)
- **Surface**: `#111318` (Negro profundo)
- **On Surface**: `#E2E2E6` (Texto claro)
- **Tertiary**: `#DEC38C` (Dorado/Ámbar)
- **Background**: `#111318` (Negro puro)

**Use Case**: Tema nocturno profesional para llamadas

---

### 3. **STITCH_CORAL** 🪸
**Tipo**: Dark/Night variant (Mensajes - Dark Coral)
- **Primary**: `#D0E4FF` (Azul cielo claro)
- **Primary Container**: `#003D5C` (Azul marino)
- **Surface**: `#1A1C1E` (Gris profundo)
- **On Surface**: `#E2E2E6` (Texto claro)
- **Tertiary**: `#FFBB8` (Coral/Naranja)
- **Error**: `#FF8A80` (Coral vibrante)
- **Background**: `#0D0E10` (Negro ultra profundo)

**Use Case**: Tema nocturno cálido para mensajería

---

### 4. **STITCH_SAGE** 🌿
**Tipo**: Light/Day variant + Dark/Night variant (Contactos)
**Light:**
- **Primary**: `#005EB6` (Azul profesional)
- **Tertiary**: `#4A6B4E` (Verde sage)
- **Surface**: `#FAFCFC` (Blanco)

**Dark:**
- **Primary**: `#ADC6FF` (Azul lavanda claro)
- **Tertiary**: `#B1CD8D` (Verde menta claro)
- **Surface**: `#1A1C1E` (Gris profundo)

**Use Case**: Tema para pantalla de contactos (dual light/dark)

---

## Aplicación en Código

### Ubicación: Theme.kt
Archivo: `/composeApp/src/main/kotlin/com/corta/app/ui/theme/Theme.kt`

- **Enum AccentColor**: Agregados valores `STITCH_AMBER`, `STITCH_PURPLE`, `STITCH_CORAL`, `STITCH_SAGE`
- **Color Schemes**: Implementados 8 esquemas de color (4 light + 4 dark)
- **AppTheme()**: Actualizada función composable para soportar los nuevos temas

### Ubicación: SettingsScreen.kt
Archivo: `/composeApp/src/main/kotlin/com/corta/app/ui/settings/SettingsScreen.kt`

- Agregadas etiquetas descriptivas en español para cada tema Stitch
- Opción seleccionable en el selector de "Color de Acento"
- Persistencia en SharedPreferences

---

## Cómo Usar

### En la App (UI)
1. Abre **Configuración** (ícono ⚙️)
2. Busca **"Color de Acento"**
3. Selecciona uno de los 4 nuevos temas Stitch
4. El tema se aplica inmediatamente en toda la app

### En Código
```kotlin
// Obtener el AccentColor actual
val accentColor = AccentColor.fromPreference(prefs.getString("accent_color", null))

// Aplicar en el tema
AppTheme(
    themeMode = currentThemeMode,
    accentColor = accentColor,
    content = { /* UI Content */ }
)
```

---

## Paleta de Colores Completa

| Componente | Amber Light | Purple Dark | Coral Dark | Sage Light |
|-----------|------------|-----------|-----------|-----------|
| Primary | `#005EB6` | `#ADC6FF` | `#D0E4FF` | `#005EB6` |
| Secondary | `#595F6D` | `#C1C6D5` | `#C1C6D5` | `#595F6D` |
| Tertiary | `#705D31` | `#DEC38C` | `#FFBB8` | `#4A6B4E` |
| Error | `#A83836` | `#FFB4AB` | `#FF8A80` | `#A83836` |
| Surface | `#FAFCFC` | `#111318` | `#1A1C1E` | `#FAFCFC` |
| OnSurface | `#2F3337` | `#E2E2E6` | `#E2E2E6` | `#2F3337` |

---

## Especificaciones de Diseño (Stitch)

### Principios Clave
- ✅ **Tonal Architecture**: Sin bordes 1px, solo cambios de tono
- ✅ **Glass & Gradient**: Material Design 3 con glassmorphism
- ✅ **Editorial Flow**: Whitespace agresivo, asimetría intencional
- ✅ **No-Line Rule**: Bordes definidos por color, no por líneas

### Tipografía
- Fuente: **Inter** (ya implementada en AppTypography)
- Display/Headline: Bold con `-0.02em` letter spacing
- Body: `on-surface-variant` para jerarquía visual
- Label: ALL CAPS con `+0.05em` letter spacing

### Componentes Estilizados
- **Buttons**: Gradient `primary` → `primary-container` a 135°
- **Bottom Navigation**: Glass blur con dot indicator en lugar de caja
- **Input Fields**: Filled soft field con bottom-bar en `primary`
- **Cards**: Redondedeadas (md/lg/xl), sin bordes explícitos

---

## Próximos Pasos (Opcionales)

1. **Aplicar glassmorphism** a TopAppBar y BottomNavigationBar
2. **Gradientes**: Implementar en botones primarios
3. **Animaciones**: Transiciones suaves al cambiar temas
4. **Persistencia de preferencias**: Ya implementada ✅
5. **Documentación en strings.xml**: Etiquetas multiidioma

---

## Referencias
- Stitch Prototypes: `/stitch_call_history_light_amber_variant/`
- Design System: `/stitch_call_history_light_amber_variant/nexus_connect/DESIGN.md`
- Material 3 Guidelines: https://m3.material.io/

**Aplicado el**: 23 de marzo de 2026  
**Version**: v1.0 (4 temas bases)
