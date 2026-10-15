---
name: Native Telecom Clarity
colors:
  surface: '#f8f9fc'
  surface-dim: '#d8dadd'
  surface-bright: '#f8f9fc'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f4f6'
  surface-container: '#eceef0'
  surface-container-high: '#e7e8eb'
  surface-container-highest: '#e1e2e5'
  on-surface: '#191c1e'
  on-surface-variant: '#424654'
  inverse-surface: '#2e3133'
  inverse-on-surface: '#eff1f3'
  outline: '#737785'
  outline-variant: '#c3c6d6'
  surface-tint: '#0856cf'
  primary: '#0041a2'
  on-primary: '#ffffff'
  primary-container: '#0b57d0'
  on-primary-container: '#ced9ff'
  inverse-primary: '#b2c5ff'
  secondary: '#006e2a'
  on-secondary: '#ffffff'
  secondary-container: '#8ffa9b'
  on-secondary-container: '#00752d'
  tertiary: '#6f3a00'
  on-tertiary: '#ffffff'
  tertiary-container: '#914d00'
  on-tertiary-container: '#ffd1ae'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dae2ff'
  primary-fixed-dim: '#b2c5ff'
  on-primary-fixed: '#001847'
  on-primary-fixed-variant: '#0040a1'
  secondary-fixed: '#8ffa9b'
  secondary-fixed-dim: '#73dc82'
  on-secondary-fixed: '#002108'
  on-secondary-fixed-variant: '#00531e'
  tertiary-fixed: '#ffdcc3'
  tertiary-fixed-dim: '#ffb77d'
  on-tertiary-fixed: '#2f1500'
  on-tertiary-fixed-variant: '#6e3900'
  background: '#f8f9fc'
  on-background: '#191c1e'
  surface-variant: '#e1e2e5'
typography:
  display-lg:
    fontFamily: Manrope
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Manrope
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.015em
  headline-lg-mobile:
    fontFamily: Manrope
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Manrope
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
  headline-sm:
    fontFamily: Manrope
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Manrope
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Manrope
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Manrope
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: Manrope
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Manrope
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Manrope
    fontSize: 11px
    fontWeight: '700'
    lineHeight: 14px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-sm: 0.75rem
  gutter-lg: 1.5rem
  margin: 1rem
  margin-sm: 0.75rem
  margin-lg: 1.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system draws direct inspiration from high-tier mobile system apps (Samsung One UI, Google Dialer, Xiaomi HyperOS) to deliver a trusted, utilitarian, and effortless call defense experience. It prioritizes clarity over visual noise, human utility over cybersecurity paranoia, and instant legibility under high-stress contexts (incoming calls, fraud detection, immediate call blocking).

### Visual Character
- **System-Native Directness:** Surfaces, typography, and controls feel like an integrated extension of the mobile operating system.
- **Calm Reassurance:** High-stakes security actions (blocking, caller verification) use friendly, soft geometry and clear semantic indicators rather than complex dashboards.
- **60-30-10 Rule Execution:** 60% neutral canvases and base structural surfaces, 30% deep contrast typography and system-blue framework elements, and 10% strictly reserved for high-signal semantic accents (verified contacts, scam alerts, urgent actions).

## Colors

The palette enforces absolute role separation to prevent visual clutter and maintain immediate glanceability.

### Primary & System Tones
- **System Blue (`#0B57D0`):** Core action driver, active tab indicator, primary confirmation buttons, and trusted dialer elements.
- **Deep Charcoal Text (`#191C1E`):** Primary display text, caller names, and high-priority headers ensuring WCAG AAA legibility.
- **Medium Neutral Text (`#44474E`):** Secondary metadata, timestamps, and descriptive sub-labels.

### Functional Surfaces (60%)
- **Base Canvas (`#F8F9FA`):** Ambient background that distinguishes modular cards from full-bleed edges.
- **Card & Sheet Surface (`#FFFFFF`):** High-contrast containers for incoming call cards, history lists, and settings groupings.
- **Subtle Surface Container (`#F1F3F5`):** Search bars, filter chips, badge backings, and inert inputs.
- **Divider & Border Rule (`#E2E8F0`):** Ultra-light structural dividing lines and container outlines.

### Semantic Accents (10%)
- **Safe / Whitelist (`#1E8E3E`):** Verified caller IDs, legitimate contacts, safe-pass routing, and successful whitelisting.
- **Spam / Threat Blocked (`#D93025`):** Immediate call drop tags, robocall detection badges, and scam warnings.
- **Suspicious / Caution (`#D97706`):** Unknown international prefix, flagged aggressive telemarketing, and review prompts.

### Accent Theme Tokens
- `accent-stitch-blue`: `#0B57D0` (Default dialer & system state)
- `accent-stitch-sage`: `#1E8E3E` (Contact management & protected numbers)
- `accent-stitch-amber`: `#D97706` (Call screening & real-time monitoring)

## Typography

The type scale leverages **Manrope** across all roles to achieve the modern, neutral, semi-geometric aesthetic typical of flagship mobile system dialers.

- **Numbers & Incoming Caller Heads:** Display and headline levels feature tighter tracking (`-0.02em`) to accommodate long international phone strings without clipping.
- **Metadata and Statuses:** Labels and secondary metadata balance compact sizes with higher weights (`600` and `700`) to guarantee fast legibility while driving or walking.
- **Hierarchy Structure:** Large titles are reserved for navigation headers and full-screen incoming alerts; list screens prioritize clean body-md and label-lg pairings for scannable call logs.

## Layout & Spacing

The layout is built for single-handed mobile usage, matching modern OEM bottom-focused design patterns (large top header margins for reachability, bottom controls for thumb interaction).

### Mobile Grid & Layout Principles
- **Grid Strategy:** Single-column fluid layout with safe margin boundaries. Outer screen horizontal padding defaults to `1rem` (16px) on compact handheld devices and expands to `1.5rem` (24px) on wider displays and tablets.
- **Reachability Rhythm:** Primary action areas and sticky bottom bars anchor to the thumb zone (`space-md` to `space-lg` bottom safe inset).
- **Grouped Sectioning:** Cards and list items cluster into isolated rounded blocks separated by `space-md` (16px) to emulate native settings and dialer groups.

## Elevation & Depth

This system avoids heavy drop shadows and faux realism, maintaining a crisp, light-mode system aesthetic driven by tonal layering and micro-elevation.

- **Level 0 (Canvas Base):** Background `#F8F9FA` with zero elevation.
- **Level 1 (Cards & Modules):** Pure white `#FFFFFF` surface resting on canvas. Bound by a hair-thin structural border (`1px solid #E2E8F0`) with no drop shadow, or an ultra-diffused micro-tint: `0px 1px 3px rgba(25, 28, 30, 0.04)`.
- **Level 2 (Floating Overlays & Bottom Sheets):** Modal dialogs and call filter sheets use `0px 8px 24px rgba(25, 28, 30, 0.08)` paired with an ambient backdrop scrim (`rgba(25, 28, 30, 0.35)`).
- **Level 3 (Floating Action Button & Urgent Alerts):** Quick call/block floating trigger uses `0px 6px 16px rgba(11, 87, 208, 0.24)` to preserve crisp depth against white lists.

## Shapes

The geometry uses friendly, modern squircle-like corner radii typical of One UI and HyperOS, establishing an approachable, consumer-friendly security identity.

- **Cards & List Groups:** `1rem` (16px) to `1.5rem` (24px) for grouped containers and call log modules.
- **Quick Action Buttons & Inputs:** `1rem` (16px) to ensure touch targets feel ergonomic and soft.
- **Floating Controls, Chips & Pills:** Full pill radius (`9999px` or minimum `1.75rem` / 28px) for category filters, status tags, and primary bottom navigation pills.

## Components

### Buttons & Interactive Controls
- **Primary System Action:** Pill or `16px` rounded container with `#0B57D0` fill, white `#FFFFFF` label (`label-lg`), zero border, height `48px` to `56px` for touch accessibility.
- **Destructive / Spam Block Button:** Crisp `#D93025` solid fill with white text, or soft-tint alert button (`#FEE2E2` fill, `#D93025` text).
- **Secondary / Ghost Action:** `#F1F3F5` fill with `#191C1E` label, no border, active scale feedback (`scale(0.98)`).

### Call Log & List Items
- **Structure:** `64px` to `72px` minimum row height. Left icon/avatar slot (`44px` circle or squircle with soft neutral `#F1F3F5` background), centered caller name and timestamp block, and right-aligned quick-action icon (Call, Details, Block).
- **Status Indicators:** Micro-dot or tinted pill tag denoting verified safe (`#1E8E3E`), flagged spam (`#D93025`), or unverified screening (`#D97706`).

### Badges & Pill Chips
- **Spam Flag:** Background `#FEE2E2`, label `#D93025`, border none, font `label-sm`.
- **Safe Shield:** Background `#DCFCE7`, label `#15803D`, border none, font `label-sm`.
- **Category Filter Chips:** Horizontal scrolling pills, height `36px`, unselected `#FFFFFF` with `#E2E8F0` border, selected `#0B57D0` with `#FFFFFF` text.

### Form Inputs & Search Fields
- **Global Search / Dialer Query:** Enclosed pill or `16px` rounded rect, background `#F1F3F5`, placeholder color `#44474E`, no visible border until focused. On focus: `#FFFFFF` fill with `2px solid #0B57D0`.

### Incoming Call Alert Cards
- **Threat Detected:** Full card header tint in `#D93025` with prominent blocked call warning, clear oversized phone number, and one-tap "Report & Blacklist" action.
- **Verified Caller:** White card container featuring a calm `#1E8E3E` shield header, caller identity validation badge, and primary `#0B57D0` answer trigger.