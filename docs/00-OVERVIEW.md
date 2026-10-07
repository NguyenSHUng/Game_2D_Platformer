# 00 - Overview

[← Index](README.md)

## Quick Facts
- **Engine**: LibGDX + Java 21 LTS
- **Platform**: Desktop (lwjgl3)
- **Type**: 2D side-view platformer shooter (tile maps, gravity — no floor depth)
- **Content**: levels from the map generator (`build_asset/build_map`, Tiled `.tmx`),
  enemies from the enemy generator (`build_asset/build_enemy`)
- **Architecture**: Component-based (ECS-inspired)
- **Build Tool**: Gradle Wrapper

## Core Principles
1. **Composition over Inheritance** - Use components, not deep hierarchies
2. **Separation of Concerns** - Each system is independent
3. **Data-Driven Design** - Config moves to JSON/external data
4. **Playable First** - Always have a working game version
5. **Modular Architecture** - Systems are reusable

## Project Structure at a Glance

```
MyGame/
├── assets/           # All game assets (sprites, maps, audio, data)
├── core/             # Main game logic (99% of code goes here)
└── lwjgl3/           # Desktop launcher (minimal code)
```

## Critical Rules for All Code

| Rule | Why |
|------|-----|
| No game logic in `lwjgl3` | Keep platform-independent |
| Components are stateless | Only store data, not behavior |
| All systems use delta time | Frame-rate independent |
| Resources are cached | Load once, reuse many |
| Entities use composition | No deep inheritance |
