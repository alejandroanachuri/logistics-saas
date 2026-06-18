\---

name: Logistics Core System

colors:

&#x20; surface: '#faf8ff'

&#x20; surface-dim: '#d2d9f4'

&#x20; surface-bright: '#faf8ff'

&#x20; surface-container-lowest: '#ffffff'

&#x20; surface-container-low: '#f2f3ff'

&#x20; surface-container: '#eaedff'

&#x20; surface-container-high: '#e2e7ff'

&#x20; surface-container-highest: '#dae2fd'

&#x20; on-surface: '#131b2e'

&#x20; on-surface-variant: '#434655'

&#x20; inverse-surface: '#283044'

&#x20; inverse-on-surface: '#eef0ff'

&#x20; outline: '#737686'

&#x20; outline-variant: '#c3c6d7'

&#x20; surface-tint: '#0053db'

&#x20; primary: '#004ac6'

&#x20; on-primary: '#ffffff'

&#x20; primary-container: '#2563eb'

&#x20; on-primary-container: '#eeefff'

&#x20; inverse-primary: '#b4c5ff'

&#x20; secondary: '#505f76'

&#x20; on-secondary: '#ffffff'

&#x20; secondary-container: '#d0e1fb'

&#x20; on-secondary-container: '#54647a'

&#x20; tertiary: '#006329'

&#x20; on-tertiary: '#ffffff'

&#x20; tertiary-container: '#007f36'

&#x20; on-tertiary-container: '#c7ffca'

&#x20; error: '#ba1a1a'

&#x20; on-error: '#ffffff'

&#x20; error-container: '#ffdad6'

&#x20; on-error-container: '#93000a'

&#x20; primary-fixed: '#dbe1ff'

&#x20; primary-fixed-dim: '#b4c5ff'

&#x20; on-primary-fixed: '#00174b'

&#x20; on-primary-fixed-variant: '#003ea8'

&#x20; secondary-fixed: '#d3e4fe'

&#x20; secondary-fixed-dim: '#b7c8e1'

&#x20; on-secondary-fixed: '#0b1c30'

&#x20; on-secondary-fixed-variant: '#38485d'

&#x20; tertiary-fixed: '#7ffc97'

&#x20; tertiary-fixed-dim: '#62df7d'

&#x20; on-tertiary-fixed: '#002109'

&#x20; on-tertiary-fixed-variant: '#005320'

&#x20; background: '#faf8ff'

&#x20; on-background: '#131b2e'

&#x20; surface-variant: '#dae2fd'

typography:

&#x20; display-lg:

&#x20;   fontFamily: Inter

&#x20;   fontSize: 48px

&#x20;   fontWeight: '600'

&#x20;   lineHeight: 56px

&#x20;   letterSpacing: -0.02em

&#x20; display-lg-mobile:

&#x20;   fontFamily: Inter

&#x20;   fontSize: 32px

&#x20;   fontWeight: '600'

&#x20;   lineHeight: 40px

&#x20;   letterSpacing: -0.02em

&#x20; headline-md:

&#x20;   fontFamily: Inter

&#x20;   fontSize: 24px

&#x20;   fontWeight: '600'

&#x20;   lineHeight: 32px

&#x20; headline-sm:

&#x20;   fontFamily: Inter

&#x20;   fontSize: 20px

&#x20;   fontWeight: '600'

&#x20;   lineHeight: 28px

&#x20; body-lg:

&#x20;   fontFamily: Inter

&#x20;   fontSize: 18px

&#x20;   fontWeight: '400'

&#x20;   lineHeight: 28px

&#x20; body-md:

&#x20;   fontFamily: Inter

&#x20;   fontSize: 16px

&#x20;   fontWeight: '400'

&#x20;   lineHeight: 24px

&#x20; body-sm:

&#x20;   fontFamily: Inter

&#x20;   fontSize: 14px

&#x20;   fontWeight: '400'

&#x20;   lineHeight: 20px

&#x20; label-md:

&#x20;   fontFamily: Inter

&#x20;   fontSize: 14px

&#x20;   fontWeight: '600'

&#x20;   lineHeight: 20px

&#x20; label-sm:

&#x20;   fontFamily: Inter

&#x20;   fontSize: 12px

&#x20;   fontWeight: '500'

&#x20;   lineHeight: 16px

rounded:

&#x20; sm: 0.25rem

&#x20; DEFAULT: 0.5rem

&#x20; md: 0.75rem

&#x20; lg: 1rem

&#x20; xl: 1.5rem

&#x20; full: 9999px

spacing:

&#x20; base: 4px

&#x20; xs: 4px

&#x20; sm: 8px

&#x20; md: 16px

&#x20; lg: 24px

&#x20; xl: 32px

&#x20; gutter: 16px

&#x20; margin-mobile: 16px

&#x20; margin-desktop: 32px

&#x20; max-width-form: 480px

&#x20; max-width-container: 1120px

\---



\## Brand \& Style

The design system focuses on a \*\*Corporate / Modern\*\* aesthetic tailored for the logistics sector in Argentina. It balances the industrial precision required for freight management with a friendly, approachable interface that reduces cognitive load for B2B users. 



The visual language emphasizes reliability through a structured grid, high legibility, and a clean, utilitarian aesthetic. It avoids unnecessary decoration, opting instead for functional clarity and a systematic approach to hierarchy, ensuring that complex data remains actionable and trustworthy.



\## Colors

The palette is rooted in professional blues and slate grays to evoke stability. 



\- \*\*Primary Action\*\*: Blue 600 is used for high-priority interactive elements like main buttons and active states.

\- \*\*Surface\*\*: The background utilizes Slate 50 to provide a soft contrast against pure white cards, reducing eye strain during long work sessions.

\- \*\*Status\*\*: Success (Green 600) and Error (Red 600) are reserved for feedback loops, such as delivery confirmations or transit delays.

\- \*\*Accessibility\*\*: All color combinations are optimized to meet WCAG 2.1 AA standards, specifically ensuring the contrast ratio for text against backgrounds is at least 4.5:1.



\## Typography

This design system utilizes \*\*Inter\*\* for its exceptional legibility in data-dense environments. 



\- \*\*Hierarchy\*\*: Headings use a semi-bold weight (600) to provide clear anchors for scanning. Body text maintains a weight of 400 for maximum readability.

\- \*\*Data Display\*\*: For tabular data and tracking numbers, ensure `font-feature-settings: 'tnum' on, 'lnum' on` is enabled to provide tabular figures that align vertically.

\- \*\*Localization\*\*: Typography supports specific Spanish (Argentina) characters and maintains line heights that accommodate slightly longer word lengths common in Spanish translations.



\## Layout \& Spacing

A \*\*Fluid Grid\*\* approach is used with strict constraints for specific content types. 



\- \*\*Grid System\*\*: Use a 12-column grid for desktop views. On mobile, transition to a single-column layout with 16px side margins.

\- \*\*Spacing Rhythm\*\*: All margins and paddings are based on a 4px/8px incremental scale to ensure mathematical consistency.

\- \*\*Content Constraints\*\*: 

&#x20;   - Administrative forms and login screens are capped at a \*\*480px\*\* width to prevent line lengths from becoming unreadable.

&#x20;   - Marketing and dashboard overviews use a maximum container width of \*\*1120px\*\* to maintain visual focus on ultra-wide monitors.

\- \*\*Breakpoints\*\*: 

&#x20;   - Mobile: < 640px

&#x20;   - Tablet: 640px - 1024px

&#x20;   - Desktop: > 1024px



\## Elevation \& Depth

The design system employs \*\*Tonal Layers\*\* supplemented by subtle ambient shadows to indicate interactivity.



\- \*\*Planes\*\*: The base background is Slate 50. Content sits on White (#FFFFFF) cards.

\- \*\*Shadows\*\*: Use a single, soft shadow style for floating elements like dropdowns and cards: `0px 4px 6px -1px rgba(15, 23, 42, 0.1), 0px 2px 4px -2px rgba(15, 23, 42, 0.05)`.

\- \*\*Borders\*\*: All primary containers and inputs must feature a 1px border in Slate 200 to define boundaries without adding visual weight.

\- \*\*Interactions\*\*: On hover, elevate cards slightly by deepening the shadow or adding a 1px border of Blue 200 to signal focus.



\## Shapes

The shape language is defined as \*\*Rounded\*\*, utilizing a standard 0.5rem (8px) radius for most UI components.



\- \*\*Standard (8px)\*\*: Used for buttons, input fields, and standard cards.

\- \*\*Large (16px)\*\*: Used for modal containers and large dashboard sections.

\- \*\*Extra Large (24px)\*\*: Reserved for marketing-oriented sections or decorative elements.

\- \*\*Full (Pill)\*\*: Used exclusively for status badges (e.g., "En Camino", "Entregado") to distinguish them from actionable buttons.



\## Components



\### Buttons

\- \*\*Primary\*\*: Blue 600 background, White text. Hover: Blue 700.

\- \*\*Secondary\*\*: White background, Slate 200 border, Slate 900 text.

\- \*\*Focus State\*\*: 2px offset Blue 600 ring for accessibility.



\### Input Fields

\- \*\*Structure\*\*: Labels must always be placed \*above\* the input field (not as placeholders) for accessibility. 

\- \*\*States\*\*: Default border is Slate 200. Focus border is Blue 600 with a subtle outer glow.

\- \*\*Validation\*\*: Error states use Red 600 borders with supporting error text below the field.



\### Cards

\- White background, 1px Slate 200 border, and the standard "Rounded" (8px) corner.

\- Padding should be consistent (24px for desktop, 16px for mobile).



\### Chips \& Badges

\- Used for status tracking. Use light background tints of the status color (e.g., Green 50 background with Green 700 text for "Entregado").



\### Lists \& Tables

\- Data rows should have a minimum height of 48px. 

\- Use subtle dividers (1px Slate 100) between rows instead of alternating zebra stripes to keep the UI clean.



\### Specialized Logistics Components

\- \*\*Timeline/Stepper\*\*: Vertical stepper for mobile, horizontal for desktop to track shipment progress.

\- \*\*Data Mini-cards\*\*: Small cards with large "Display" type for KPIs like "Vehículos Activos" or "Entregas Pendientes".

