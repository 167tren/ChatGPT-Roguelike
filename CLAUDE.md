# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A modern roguelike game built with Java 2D (Swing/AWT) inspired by Dead Cells, Risk of Rain 2, and Vampire Survivors. The game emphasizes replayability through procedural generation, rapid power progression, and the addictive "one more run" gameplay loop.

## Build and Run

```bash
# Compile all Java files
javac *.java

# Run the game
java Game

# Quick recompile and run
javac *.java && java Game
```

No build system (Maven/Gradle) is used - this is intentional for simplicity and rapid prototyping.

## Architecture

### Core Game Loop
- **Game.java** (main entry point) - JPanel-based game loop at 60 FPS, handles rendering, input, and orchestrates all game systems
- **GameMode enum** - Switches between DUNGEON exploration and COMBAT encounters
- Game state machine: DUNGEON → trigger combat → COMBAT → victory/defeat → DUNGEON

### Key Systems

**Dungeon Generation** (Dungeon.java)
- Procedural room-based generation using BSP-like random placement
- Rooms connected by L-shaped corridors
- Places Sanctuary (shop), Stairs (next floor), enemies, and decorative elements
- Seed-based generation allows replaying the same layout

**Combat System** (CombatManager.java, CombatState.java)
- Timing-based encounters with a moving cursor on a track
- Track contains segments: HIT (damage), CRIT (bonus damage), DANGER (enemy attacks), BLOCK (reduce damage)
- Player presses spacebar to strike when cursor is on beneficial segments
- Combat is stateful with rounds, combo system, and scaling difficulty
- Uses callback interfaces: `Listener` (victory/defeat) and `Effects` (visual feedback)

**Entity System**
- **Entity.java** - Base class with hp, maxHp, x, y
- **Enemy.java** - Extends Entity, adds type (EnemyType enum), elite status, and level scaling
- **Player** - Instantiated as an Entity in Game.java

**Progression Systems**
- **Relics** (Relic.java, RelicCatalog.java) - Passive buffs dropped from combat, applied through static catalog
- **Shards** - Currency earned from victories, spent at Sanctuary
- **Floor progression** - Each floor scales enemy HP/damage and combat difficulty

### Visual System
- **GameConfig.java** - Central configuration for all constants (colors, sizing, balance tuning)
- **Particle.java** - Visual feedback for movement and combat
- Rendering uses double-buffered Graphics2D with custom shading on floor tiles

### Special Locations
- **Sanctuary.java** - Shop where player spends shards on relics or healing
- **Stairs.java** - Advances to next floor, increasing difficulty

## Development Patterns

**Configuration-driven design**: All gameplay constants live in GameConfig.java - modify values there to tune balance without touching game logic.

**Seed-based generation**: Use `setSeed()` to reproduce specific dungeon layouts. Demo seed available as `GameConfig.DEMO_SEED`.

**Mode separation**: Dungeon exploration uses arrow keys for grid movement; Combat mode uses spacebar for timing inputs. Input handling in `initInput()` branches on current mode.

**Combat modifiers via Relics**: Relics modify CombatState during combat setup. To add new effects, extend RelicCatalog and modify CombatManager.beginCombat().

**Particle effects**: Add particles to `particles` (dungeon) or `combatParticles` (combat) lists. They auto-update and render each frame.

## Design Pillars

The game is built around modern roguelike principles:
- **Short runs** - Quick session length encourages repeated attempts
- **Procedural freshness** - Every dungeon layout, enemy placement, and loot drop is randomized
- **In-run power growth** - Relics create emergent builds each run
- **Meta-progression** - Permanent upgrades planned for future stages
- **Skill reward** - Combat timing and dungeon pathing matter
- **Visual feedback** - Particles, screen shake, and clear color coding for game state

See README.md for full development roadmap and design vision.
