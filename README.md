# Luna Visuals

A free and open-source visual client for Minecraft 1.21.11, built on [Fabric](https://fabricmc.net/).
Hit effects, hand animations, a custom HUD, melee skins and cosmetics that everyone can see.
Every cosmetic is available to every player: there are no paid tiers or locks.
Luna Visuals only changes what you see: it never touches game mechanics or sends anything that would give you an
advantage.

## Installation

Build the mod with Gradle, then put the jar from `build/libs` into your Minecraft `mods` folder together with
Fabric Loader and Fabric API.

## License

This project is subject to the [GNU General Public License v3.0](LICENSE). This does only apply for source code
located directly in this clean repository. During the development and compilation process, additional source code may
be used to which we have obtained no rights. Such code is not covered by the GPL license.

For those who are unfamiliar with the license, here is a summary of its main points. This is by no means legal advice
nor legally binding.

*Actions that you are allowed to do:*

- Use
- Share
- Modify

*If you do decide to use ANY code from the source:*

- **You must disclose the source code of your modified work and the source code you took from this project. This means
  you are not allowed to use code from this project (even partially) in a closed-source and/or obfuscated
  application.**
- **You must state clearly and obviously to all end users that you are using code from this project.**
- **Your application must also be licensed under the same license.**

Bundled third-party mods keep their own licenses, see [third-party](third-party).

## Contributing

Contributions are welcome. Keep changes consistent with the existing structure and style.

## Setting up a workspace

Luna Visuals uses Gradle. Gradle itself needs Java 25 to run Loom; the mod is compiled for Java 21.

1. Clone or download the repository.
2. Open the folder as a Gradle project in your IDE (IntelliJ IDEA is recommended).
3. Run `./gradlew genSources` once to get readable Minecraft sources.
4. Start the game with `./gradlew runClient`, or build the jar with `./gradlew build` (output in `build/libs`).

## Project layout

| Path | What is there |
|---|---|
| `src/main/java/net/lunavisuals/core` | modules, settings, config and local presets, theme |
| `src/main/java/net/lunavisuals/fx` | visual effects: hits, trails, outlines, sky, trajectories |
| `src/main/java/net/lunavisuals/hud` | HUD elements, dynamic island, target HUD |
| `src/main/java/net/lunavisuals/gui` | menus and screens |
| `src/main/java/net/lunavisuals/melee` | melee skins (animated knives) and cloth capes |
| `src/main/java/net/lunavisuals/cosmetic` | hats, pets, wings, backpacks |
| `src/main/java/net/lunavisuals/mixin` | Mixins into Minecraft |
