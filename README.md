# Corta App 🛡️
> **«Con Corta la haces corta». El escudo anti-spam privado para Chile. Bloquea llamadas molestas y estafas con procesamiento 100% on-device.**

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--35)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2%2B%20Multiplatform-purple.svg)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/UI-Compose%20Multiplatform%20(M3)-blue.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Security](https://img.shields.io/badge/Database-SQLCipher%20AES--256%20(Keystore)-red.svg)](https://www.zetetic.net/sqlcipher/)
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](LICENSE)

---

## 💡 El Contrato de Valor (Job to be Done)

> **«Recupera la paz mental de que cuando tu teléfono suene, sea una llamada legítima de alguien que te importa, sin entregar tu agenda de contactos a empresas extranjeras».**

A diferencia de aplicaciones tradicionales que monetizan tus datos subiendo tu libreta de contactos a la nube, **Corta App** opera bajo el principio **Zero-Knowledge**:
* **Tu agenda jamás sale de tu teléfono.**
* **Cero consumo de datos por llamada:** El análisis ocurre en la memoria local en menos de **2 milisegundos**.
* **Diseñada específicamente para Chile:** Alineada con la **Resolución Exenta 286 de SUBTEL**, la normativa **SERNAC "No Molestar"** y la **Ley 19.628 de Protección de Datos Personales**.

---

## ⚡ Características Principales

* 🛡️ **Filtrado Inteligente de Llamadas (Call Screening):** Intercepta y rechaza llamadas de telemarketing agresivo y estafas antes de que el teléfono comience a timbrar.
* 🇨🇱 **Cumplimiento Normativo SUBTEL:**
  * **Prefijo 809:** Bloqueo automático de telemarketing masivo no solicitado.
  * **Prefijo 600:** Identificación transparente de servicios comerciales contratados (bancos, clínicas, servicios básicos).
  * **Móviles desconocidos (+56 9):** Alerta visual contra estafas y números de fraude recurrente.
* 🚑 **Escudo de Emergencias Vital (Whitelist Nacional):** Blindaje total que garantiza que números de auxilio (131 SAMU, 132 Bomberos, 133 Carabineros, 14xx de Seguridad Municipal y servicios de salud) **nunca** sean bloqueados.
* 💬 **Filtrado SMS On-Device:** Detección de smishing, enlaces maliciosos y suplantación de identidad bancaria directamente en el procesador de tu equipo.
* 🔒 **Seguridad Criptográfica en Hardware:** Base de datos local protegida con **SQLCipher (cifrado AES-256)** con llaves administradas por el chip de seguridad **Android Keystore (TEE/StrongBox)**.
* 🎨 **Material Design 3 & Temas Stitch:** Interfaz limpia de 3 destinos esenciales (Mensajes, Llamadas, Contactos) con soporte completo para temas dinámicos Monet y variantes de acento.

---

## 🏗️ Arquitectura Técnica

El proyecto está construido con **Clean Architecture** sobre **Kotlin Multiplatform (KMP)**:

```
corta-v1/
├── composeApp/          # Capa de Presentación Android & Servicios Nativos
│   ├── ui/              # Pantallas en Jetpack Compose (M3) & ViewModels reactivos
│   ├── services/        # CallScreeningService, SmsReceiver, WorkManager
│   └── di/              # Inyección de dependencias Koin (Android modules)
│
└── shared/              # Capa de Dominio y Datos (Puro Kotlin agnóstico)
    ├── domain/          # CallValidator, SpamEvaluator, CortaRepository, PhoneNumberUtils
    ├── data/            # Drivers SQLCipher, Ktor HTTP Client (SUBTEL/Sync)
    └── sqldelight/      # Esquema SQLDelight (SSOT) & Consultas indexadas
```

### La Tesis Local-First para Chile
* **Espacio numérico acotado:** El universo de números activos que hacen spam agresivo, cobranzas extrajudiciales y estafas en Chile se concentra en 100.000 a 400.000 números.
* **Huella mínima:** 300.000 números empaquetados en un índice local ocupan **menos de 4 a 6 MB** de almacenamiento y menos de **1 MB de RAM** en un Filtro de Bloom probabilístico.
* **Sincronización diferencial (Delta Sync):** En lugar de consultar una API remota en cada llamada, el equipo descarga pequeños deltas binarios de pocos kilobytes de forma periódica.
* **Convergencia con iOS:** Esta misma arquitectura de lista local ordenada es el requisito obligatorio de Apple para `CXCallDirectoryExtension` (CallKit), permitiendo una transición natural a iPhone en el futuro.

---

## 📱 Compatibilidad

* **Mínimo:** Android 8.0 Oreo (`minSdk = 26`)
* **Objetivo:** Android 14 / Android 15 (`targetSdk = 34`, testeado en `SDK 35`)
* **Roles del Sistema:**
  * `RoleManager.ROLE_CALL_SCREENING`: Para filtrado activo antes del timbrado.
  * `RoleManager.ROLE_SMS` (Opcional): Para actuar como gestor predeterminado de mensajería.
* **Permisos Críticos:** `READ_CALL_LOG`, `POST_NOTIFICATIONS`, `READ_PHONE_STATE`.

---

## 🚀 Compilación y Desarrollo

### Requisitos
* JDK 17 (administrado automáticamente vía Gradle toolchain).
* Android SDK Platform 34 y Build-Tools 36.0.0.
* Gradle 9.5.0 (usar el wrapper `./gradlew` incluido).

### Comandos Frecuentes
```bash
# Compilar APK Debug
./gradlew :composeApp:assembleDebug

# Ejecutar pruebas unitarias de dominio y UI
./gradlew testDebugUnitTest

# Instalar en dispositivo físico o emulador conectado vía ADB
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

---

## 🔒 Privacidad por Diseño (Manifiesto Zero-Knowledge)

1. **Tu agenda no se sube a la nube.**
2. **Tus llamadas no se rastrean en servidores remotos.**
3. **Tus reportes comunitarios se anonimizan mediante $K$-Anonymity con hashes criptográficos truncados.**
4. **Cumplimiento estricto con la Ley 19.628 de Protección de la Vida Privada de Chile.**

---

Desarrollado con estándares de ingeniería para Chile 🇨🇱.
