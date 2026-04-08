# AGENTS Guide for Nova

This file is for coding agents working in `Nova/`.

## Scope
- This repository is the Fabric-based Nova client plus the React control surface under `ui/`.
- If this file conflicts with the source code, follow the source code.

## Core Rule
- Nova runtime features must not depend on Fabric API feature hooks or helper abstractions.
- Nova gameplay/client behavior changes should be implemented through vanilla client internals and mixins.
- Treat mixin injection as the default implementation path for Nova features.
- Do not introduce Fabric API event-based feature logic for Nova unless the user explicitly asks for it.

## Practical Implications
- Module logic may use the existing local event/module/value/config systems.
- But the actual Minecraft behavior changes behind those modules should be wired by mixins into Minecraft classes.
- When evaluating whether a feature is suitable for Nova, prefer features that can be expressed as:
  - rendering overrides
  - client-side state/view changes
  - HUD or browser UI state
  - input handling changes
  - lightweight client-only behavior changes

## Avoid by Default
- Do not build Nova features around Fabric API lifecycle/event dependencies.
- Do not add new feature implementations that require Fabric API to be present as the behavioral backbone.
