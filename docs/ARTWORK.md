# Drink artwork

Generated with the built-in image_gen tool on 2026-10-20, for Caffeine Tracker. These original generic assets contain no third-party photos or brand labels. Original PNGs are retained locally under artwork/source/ (excluded from Git). The Android copies are deterministic 256 × 256 transparent PNG thumbnails, fitted proportionally without changing the illustrations. They total about 237 KiB and require no network access.

## Android assets

- app/src/main/res/drawable-nodpi/drip_coffee.png
- app/src/main/res/drawable-nodpi/instant_coffee.png
- app/src/main/res/drawable-nodpi/energy_drink.png

The existing drawn coffee icon remains in navigation, metrics and as the fallback for other drink types. These pictures identify drip/filter coffee, instant coffee and energy drinks in suggestions, favorites, the drink library and intake rows. Names and amounts remain visible beside each image.

## Prompt set (built-in tool)

Shared prompt: Use case: product-mockup. Create a polished small Android caffeine tracker thumbnail asset, a single compact stylized photorealistic 3D product illustration. Warm amber, cream and dark coffee brown materials, soft studio lighting, clean readable silhouette, centered large with generous 10% padding, slight three-quarter angle. Real transparent background, no solid backdrop, no scene, no ground plane, no words, no label text, no brand, no watermark. Must remain very easy to identify at 64 pixels.

Drip coffee subject: drip/filter coffee represented by a clear glass coffee carafe half-filled with dark black coffee, with an ivory ceramic pour-over filter dripper sitting on top; visible warm paper filter rim. One coherent pour-over coffee brewer.

Instant coffee subject: instant coffee represented by a small open clear jar containing visibly coarse brown soluble coffee granules and a cream ceramic coffee mug beside it. Simple two-object product grouping; clearly distinguish the jar of granules from a brewed filter coffee carafe.

Energy drink subject: a single tall slim energy drink aluminum can in warm matte amber with dark charcoal details, a simple bold lightning-bolt graphic, silver pull-tab top. Generic unbranded and absolutely no lettering. Recognizable tall energy can.

## Replacements and additions, 2026-10-21

Built-in image_gen was used for four separate transparent product cutouts. The generic energy asset replaces the former amber lightning can. New espresso, black-tea and green-tea assets extend the automatic mapping and the existing reusable image picker. DRIP/INSTANT and official brand assets remain unchanged. All new runtime assets are proportionally fitted to 256 × 256 RGBA PNGs, with alpha retained. No runtime network access is needed. Full-size originals are copied into artwork/source/*-v2.png (local, git-ignored). Asset names and SHA-256 are in generated-drink-images.json.

Final prompt set (built-in tool):

Energy: Use case product-mockup. ONE premium photorealistic product cutout for a caffeine tracker Android app, viewed at very small thumbnail sizes. Single realistic slim 250 ml aluminum ENERGY DRINK CAN, upright, entire can visible, centered. Brushed silver aluminum, satin deep charcoal wrap, one broad lime-green diagonal band, silver top and pull tab. No lettering, logos, lightning bolt or brand imitation. Soft studio photography, restrained realistic highlights, no busy patterns or condensation. Three-quarter view, slightly elevated, about 85% image height. Transparent background, no ground, surrounding objects or floor shadow; clean readable silhouette.

Shared espresso/tea prompt: Use case product-mockup. ONE premium photorealistic cutout for a caffeine tracker thumbnail, actual transparent background with alpha, entire vessel fully visible and centered with balanced margins, readable small silhouette. Soft neutral studio photography, clean edges, slight elevated three-quarter angle. No logos, text, background surface, surrounding props, floating steam or heavy cast shadow.

Espresso: small white ceramic demitasse and matching saucer, handle right, dark espresso with caramel golden crema; clearly small espresso rather than mug; together about 85% canvas width.

Black tea: clear glass teacup with transparent curved handle, deep amber tea and one tiny dried dark tea leaf tucked by its lower side; realistic glass and visible liquid surface; about 85% width.

Green tea: pale ivory low Japanese tea bowl, thin sage-green rim, no handle, pale yellow-green tea, one small fresh green tea leaf tucked at bottom right; natural color, no neon or matcha foam; about 85% width.

## Public distribution

The public 1.0 build uses the original coffee icon and generic drink illustrations. Earlier portrait and branded packshots are retained only in local ignored artwork. Saved drink image keys still resolve, with generic energy drink artwork for branded entries.
