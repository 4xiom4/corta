# Collaborative Rules (Local Mock v1)

Este documento describe la implementacion inicial KISS para reglas colaborativas.

## Objetivo

- Usar backend **mock local** hoy.
- Mantener contrato estable para migrar a backend real sin rehacer dominio.

## Componentes

- Contratos compartidos: `shared/src/commonMain/kotlin/com/corta/domain/CollaborativeRules.kt`
- Gateway mock Android: `composeApp/src/main/kotlin/com/corta/app/data/LocalMockCollaborativeRulesGateway.kt`
- Politica de acceso: `composeApp/src/main/kotlin/com/corta/app/data/PrefsCommunityAccessPolicy.kt`
- Integracion principal: `shared/src/commonMain/kotlin/com/corta/domain/CortaRepository.kt`

## Gating de acceso

La base comunitaria se habilita solo cuando:

- `is_guest = false`
- `is_donor = true`

La base curada (`CURATED`) se sincroniza para todos los usuarios.

## Flujo de sincronizacion

1. App start (`CortaApplication`) sincroniza reglas colaborativas y reportes pendientes.
2. `SyncWorker` repite sincronizacion junto a SUBTEL.
3. Servicios de llamadas/SMS encolan reportes (`CommunityReport` table).

## Fuentes de reglas y precedencia

1. `SUBTEL`
2. `USER`
3. `CURATED`
4. `COMMUNITY`

La whitelist critica sigue teniendo prioridad sobre cualquier fuente.

