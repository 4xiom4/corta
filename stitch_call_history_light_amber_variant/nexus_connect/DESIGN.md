# Design System Specification: The Fluid Editorial Framework

## 1. Overview & Creative North Star: "The Digital Concierge"
This design system rejects the "utilitarian grid" in favor of an **Editorial Flow**. In a communication app, the interface must disappear to prioritize the human connection. Our North Star is **The Digital Concierge**: an experience that feels quiet, high-end, and hyper-legible.

Instead of rigid boxes and heavy dividers, we use **Tonal Architecture**. We break the template look by utilizing aggressive whitespace (from our spacing scale) and intentional asymmetry—positioning primary actions and typography to guide the eye through a series of "layered sheets" rather than a flat screen. This is a premium interpretation of Material 3, optimized for a universal audience.

---

## 2. Color & Surface Philosophy
The palette is built on tonal transitions rather than structural lines.

### The "No-Line" Rule
**Explicit Instruction:** Do not use 1px solid borders for sectioning or list items. Boundaries must be defined solely through background color shifts. 
*   *Example:* Use `surface_container_low` for the main background and `surface_container_lowest` for individual message bubbles or cards. 

### Surface Hierarchy & Nesting
Treat the UI as a physical stack of fine paper. 
*   **Base Layer:** `surface` (#faf9fc)
*   **Sectioning:** `surface_container` (#edeef2)
*   **Floating Elements:** `surface_bright` (#faf9fc) with Glassmorphism.

### The "Glass & Gradient" Rule
To elevate the "Modern" brand value, use **Glassmorphism** for the Bottom Navigation Bar and Top App Bars. 
*   **Token:** `surface_container_highest` at 85% opacity with a `24px` backdrop blur.
*   **Signature Textures:** For primary CTAs (e.g., "Start Chat"), apply a subtle linear gradient from `primary` (#005eb6) to `primary_container` (#5f9efb) at a 135-degree angle. This adds "soul" and depth that flat hex codes lack.

---

## 3. Typography: Editorial Authority
We utilize **Inter** to achieve a clean, Swiss-style aesthetic that ensures universal legibility across all age groups.

*   **Display (lg/md/sm):** Used for empty states or "Welcome" moments. Use `on_surface` with `-0.02em` letter spacing for a premium, "tight" feel.
*   **Headline (sm):** Used for screen titles (e.g., "Messages"). This should be bold and authoritative.
*   **Title (md/lg):** Used for contact names in lists. High contrast (`on_surface`) is mandatory.
*   **Body (md/lg):** Used for message previews. Use `on_surface_variant` (#5c5f64) to create a secondary visual tier.
*   **Label (md):** Used for the Bottom Navigation (MENSAJES, LLAMADAS, CONTACTOS). Set to `All Caps` with `+0.05em` letter spacing to ensure clarity for older demographics.

---

## 4. Elevation & Depth: Tonal Layering
Traditional drop shadows are forbidden. We use **Ambient Depth**.

*   **The Layering Principle:** Place a `surface_container_lowest` card on a `surface_container_low` background. The difference in hex value provides a "soft lift" that feels more modern than a shadow.
*   **Ambient Shadows:** If a Floating Action Button (FAB) requires a shadow, use the `on_surface` color at **6% opacity** with a `32px` blur and `8px` Y-offset. It should look like a soft glow, not a dark smudge.
*   **The Ghost Border:** If high-contrast accessibility is required, use a "Ghost Border": `outline_variant` (#afb2b8) at **15% opacity**. Never use 100% opacity for borders.

---

## 5. Components

### Buttons & Chips
*   **Primary Button:** Uses the `xl` (1.5rem) roundedness scale. Background is the Primary-to-Primary-Container gradient.
*   **Chips:** Use `surface_container_high` for unselected states. For selection, transition to `primary` with `on_primary` text. No borders.

### Lists & Cards (The Communication Feed)
*   **The Feed:** Forbid the use of divider lines between messages. Use the **Spacing Scale (4)** (1rem) to separate message threads.
*   **Avatars:** Use `full` (9999px) roundedness. Add a `2px` offset "ring" using the `background` color to make them pop against container backgrounds.

### Input Fields
*   **The "Soft Field":** Instead of an outlined box, use a filled `surface_container_highest` with `md` (0.75rem) corners. The active state is indicated by a `2px` bottom-bar in `primary`, rather than a full border.

### Bottom Navigation
*   **Structure:** Three icons with labels (MENSAJES, LLAMADAS, CONTACTOS).
*   **Styling:** Use a `surface_container_highest` glass blur. The active state should not use a box, but rather a "Primary Dim" (#0052a1) icon color and a subtle dot indicator below the label.

---

## 6. Do’s and Don’ts

### Do
*   **DO** use `surface_container_low` for the message list background and `surface_container_lowest` for the message bubbles.
*   **DO** use the **Spacing Scale (8)** (2rem) for page margins to give the content "room to breathe."
*   **DO** leverage the 5 color variants (Blue, Sage, Amber, etc.) for contact avatars or specific category tags to help fast navigation.

### Don't
*   **DON'T** use 1px dividers. If you need separation, use a `4px` height `surface_container` block or whitespace.
*   **DON'T** use pure black (#000000) for Night Theme text. Use `on_surface` for better eye comfort.
*   **DON'T** use `none` or `sm` roundedness. This system requires `md`, `lg`, or `xl` to maintain the "Soft Minimalist" brand value.

---

## 7. Scaling for Night Theme
When switching to the Night Theme variants (e.g., Deep Purple, Neon Teal):
1.  **Maintain the Tonal Delta:** The contrast ratio between `surface` and `surface_container` must remain identical to the Light Theme.
2.  **Electric Accents:** Use the "Electric" variants (Electric Indigo, Neon Teal) only for CTAs and status indicators. The bulk of the UI should remain in `surface_dim` and `surface_container` to prevent user fatigue.