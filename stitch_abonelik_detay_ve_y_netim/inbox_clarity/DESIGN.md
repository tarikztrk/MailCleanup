# Design System Specification: The Digital Curator

## 1. Overview & Creative North Star
The "Digital Curator" is the guiding philosophy for this design system. We are moving away from the cluttered, "inbox-fatigue" aesthetic of traditional email clients. Instead, we are building a high-end, editorial dashboard that treats a user's subscription list as a curated collection. 

To achieve a "Trustworthy" and "Modern" feel, we reject the rigid, boxy templates of standard apps. We utilize **Intentional Asymmetry** and **Tonal Depth** to create a sense of calm authority. By leaning into white space (using our 16 and 20 spacing tokens) and sophisticated typography, we ensure that managing 500 subscriptions feels as effortless as flipping through a premium magazine.

## 2. Colors & Surface Philosophy
The palette is rooted in professional blues and neutral architectural grays. However, the application of these colors must be nuanced.

### The "No-Line" Rule
**Strict Mandate:** Designers are prohibited from using 1px solid borders to section content. 
*   **How to separate:** Use background shifts. A `surface-container-low` section sitting on a `surface` background is the standard for grouping.
*   **The Goal:** To create a seamless, fluid interface where the content defines the boundaries, not the lines.

### Surface Hierarchy & Nesting
Treat the UI as a series of physical layers—like stacked sheets of fine vellum.
*   **Level 0 (Base):** `surface` (#f8f9fa)
*   **Level 1 (Sections):** `surface-container-low` (#f3f4f5)
*   **Level 2 (Active Cards):** `surface-container-lowest` (#ffffff)
*   **Level 3 (Modals/Overlays):** `surface-bright` (#f8f9fa) with Glassmorphism.

### The "Glass & Gradient" Rule
To avoid a "flat" corporate look:
*   **CTAs:** Use a subtle linear gradient from `primary` (#0040a1) to `primary_container` (#0056d2) at a 135-degree angle. This adds "soul" and depth.
*   **Floating Navigation:** Use `surface_container_lowest` with an 80% opacity and a 20px backdrop-blur to allow the content beneath to "bleed" through softly.

## 3. Typography: Editorial Authority
We utilize a dual-font system to create a high-contrast, editorial hierarchy.

*   **Display & Headlines (Manrope):** Chosen for its geometric modernism. Use `display-lg` for data visualization numbers (e.g., "42 Unread") to make them feel like hero elements.
*   **Body & Labels (Inter):** Chosen for its clinical legibility. Inter handles the heavy lifting of subscription titles and sender details.
*   **Hierarchy Note:** Always maintain a minimum of two scale jumps between a headline and body text (e.g., pair `headline-sm` with `body-md`) to ensure the "Editorial" intent is clear.

## 4. Elevation & Depth
We eschew traditional drop shadows in favor of **Tonal Layering**.

*   **Layering Principle:** Depth is achieved by "stacking." A white card (`surface-container-lowest`) placed on a light gray base (`surface-container-low`) creates a natural lift.
*   **Ambient Shadows:** If a card must float (e.g., a "Delete All" confirmation), use a shadow with a 40px blur, 4% opacity, and a tint of `on-surface` (#191c1d). It should look like a soft glow, not a dark smudge.
*   **The Ghost Border:** For high-density lists where separation is critical, use `outline-variant` (#c3c6d6) at **15% opacity**. It should be felt, not seen.

## 5. Components

### Buttons (The "Action Pillars")
*   **Primary (Subscribe/Keep):** Gradient fill (`primary` to `primary_container`), `xl` (1.5rem) roundedness.
*   **Tertiary/Danger (Unsubscribe):** Use `tertiary` (#940010) but only for the text/icon. The container should be `tertiary_fixed` (#ffdad6) at 40% opacity to keep the "Minimalist" vibe without overwhelming the user with red.

### The "Curator" List Items
*   **Styling:** No dividers. Use `spacing-3` (1rem) as a vertical gap between items.
*   **Background:** Use a subtle hover/active state shift to `surface-container-high`.
*   **Leading Element:** High-contrast logo or a `primary-fixed` avatar.

### Data Visualization Icons
*   **Style:** Thin-stroke (1.5px) icons. 
*   **Context:** Use `secondary` (#48626e) for inactive states and a "Pulse" of `primary` for active data points.

### Cards
*   **Constraint:** Max roundedness `xl` (1.5rem). 
*   **Nesting:** A card on the dashboard should never have a border; it should rely on a `surface-container-lowest` fill against the `surface` background.

## 6. Do’s and Don’ts

### Do:
*   **Use Spacing as a Tool:** Use `spacing-12` (4rem) to separate major functional groups (e.g., "Active Subscriptions" vs. "Suggested Unsubscribes").
*   **Embrace Asymmetry:** Align headline text to the left while keeping action buttons floating to the right with generous breathing room.
*   **Tone-on-Tone:** Use `primary-fixed` text on a `primary-container` background for a sophisticated, low-contrast "Modern" look.

### Don’t:
*   **Don't use 100% Black:** Never use #000000. Use `on-surface` (#191c1d) for all text to maintain the "Soft Minimalist" aesthetic.
*   **Don't use Standard Dividers:** If the layout feels "messy," increase the `spacing` scale rather than adding a line.
*   **Don't crowd the Red:** Red (`tertiary`/`error`) is a loud signal. Use it sparingly. An entire screen of red buttons is a failure of curation; only the specific "Unsubscribe" action should carry the weight.