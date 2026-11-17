# Storm Engine Native — Status 0.13.0

## Newly implemented playable combat prototype

- Two-fighter 1v1 simulation at a fixed 60 Hz update rate.
- Simple CPU opponent that approaches, attacks at close range, and is interrupted by hits.
- Player movement inside bounded arena coordinates.
- Jump arc with gravity, normal attack, special attack using a regenerating chakra meter, and block mitigation.
- Health values, hit counters, round result state, and restart support.
- Vulkan scene renders two procedural humanoid placeholders, arena floor, pillars, and world-space health bars.
- Android landscape combat screen with dedicated touch buttons for jump, attack, guard, and special.
- The existing analog movement gesture remains on the left side of the Vulkan surface.

## Important limitation

This is a playable **combat-system prototype**, not yet a playable port of Naruto Shippuden: Ultimate Ninja Storm Revolution. The fighters are original procedural placeholder geometry, and the arena is procedural. No Naruto character models, retail stages, retail textures, skeletal animation, ultimate techniques, voice lines, sound effects, story mode, character select, or original game logic have been integrated.

## Retail data pipeline progress

- The user-facing workflow now accepts one selected installation folder only.
- Recursive scanning discovers supported CPK archives and loose XFBIN files without asking the user to locate each archive.
- CPK priority order is data.cpk, data_win32.cpk, dataRegion.cpk, dataRegion_win32.cpk, stage.cpk, then remaining archives.
- Supported XFBIN entries are automatically parsed for NUD/NUT payloads after extraction.
- One scan is limited to 512 MiB total extracted output, up to 8 CPK archives, and up to 48 XFBIN files.

- Named-TOC CPK archives can now yield XFBIN entries to app-private storage.
- CRILAYLA-compressed entries are decoded with strict output-size checks and memory limits.
- Source CPK files remain read-only.
- ITOC-only archives are not supported yet.

## Retail data pipeline still required

- CPK extraction and CRILAYLA decompression for the PC game archives.
- Validation against the user's actual `.cpk` and `.xfbin` files.
- NUD/NDP3 mesh decoder, vertex/index buffers, material mapping and NUT/NTP3 texture decoder.
- Skeleton and ANM animation decoding, retargeting, and animation state machine.
- Stage geometry, collision, camera behavior, effects, and audio import.
- Replacing procedural placeholders with assets the user lawfully supplies.

The current APK build verifies compilation and packaging only. It does not verify Vulkan rendering or frame rate on the user's Infinix Hot 50 / Helio G100 device.
