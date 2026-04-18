# SceneMax3D Command Reference

**SceneMax3D v1.8.8** — A visual scripting language for creating 3D games and interactive scenes.

> Author: Adi Barda | Last updated: March 2026

## Table of Contents

- [Built-in MCP Server and AI Assistants](built-in-mcp-server.md)
- [SceneMax MCP Tool Reference](mcp-tools-reference.md)
- [Screen & Canvas](screen-and-canvas.md)
- [Camera](camera.md)
- [Camera Systems](camera-systems.md)
- [3D Objects & Models](objects-and-models.md)
- [Sprites](sprites.md)
- [Animation](animation.md)
- [Movement & Rotation](movement-and-rotation.md)
- [Variables & Data Types](variables-and-data-types.md)
- [Arrays](arrays.md)
- [Control Flow](control-flow.md)
- [Procedures & Functions](procedures.md)
- [Events & Input](events-and-input.md)
- [Collisions & Physics](collisions-and-physics.md)
- [Audio](audio.md)
- [Effects](effects.md)
- [UI & Drawing](ui-and-drawing.md)
- [Camera Tracking & Minimap](minimap.md)
- [Levels & Shared State](levels-and-shared-state.md)
- [Path Replay](path-replay.md)
- [Utilities](utilities.md)
- [itch.io Integration Guide](itchio-integration.md)

## Design Notes

- [Effekseer Particle System Designer](effekseer-particle-designer.md)

## Quick Start

```scenemax
// Set up a full-screen game with a skybox
Screen.mode full
Canvas.size 1600,900
skybox.show "lagoon"

// Create a character and make the camera follow it
player => sinbad
Camera.chase player

// Move the player when a key is pressed
when key up is pressed do
  player.move forward 3 in 1 second
end do
```

## Language Basics

SceneMax3D uses a simple, English-like syntax. Objects are created with `is a` or `=>` shorthand, properties are set with `=`, and commands use natural language patterns like `move left 3 in 2 seconds`.
