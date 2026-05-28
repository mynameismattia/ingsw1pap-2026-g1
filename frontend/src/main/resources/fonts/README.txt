Just 21 — Bundled UI fonts

These .ttf files are registered at boot by MainApp.loadCustomFonts() and
referenced by the CSS font-family stacks (game.css, menu.css, license.css).
All three families are SIL Open Font License 1.1 — safe to bundle.

Bundled files:
  Manrope-Bold.ttf        -> CSS "Manrope Bold"  (titoli, dialog header)
  Manrope-Medium.ttf      -> CSS "Manrope Medium" (hint banner)
  Inter-Medium.ttf        -> CSS "Inter Medium"  (label, nomi, body)
  Inter-Regular.ttf       -> CSS "Inter"         (fallback body)
  JetBrainsMono-Medium.ttf-> CSS "JetBrains Mono Medium" (saldi, bet, numeri)

Sources:
  Manrope        github.com/davelab6/manrope  ("ttf format (legacy)")
  Inter          github.com/rsms/inter        (release v4.1, extras/ttf)
  JetBrains Mono github.com/JetBrains/JetBrainsMono (fonts/ttf)

Note: i CSS non usano un peso "ExtraBold" — il più pesante richiesto è
"Manrope Bold", quindi non serve bundlare Manrope-ExtraBold.

If a file is missing, MainApp ignores it and the CSS falls back to the
system fonts (Segoe UI / Menlo). Rebuild from IntelliJ after any change.
