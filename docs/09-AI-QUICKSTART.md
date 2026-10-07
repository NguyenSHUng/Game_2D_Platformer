# 09 - AI Quickstart

[← Index](README.md)

**For Claude/AI Assistants: Start here before writing any code**

## Before You Code Anything

Read these in order (takes ~1 hour):

1. **[04-GAME-DESIGN.md](04-GAME-DESIGN.md)** (20 min) - Game mechanics and features
2. **[05-SYSTEMS-GUIDE.md](05-SYSTEMS-GUIDE.md)** (30 min) - System specifications
3. **[03-CODE-STANDARDS.md](03-CODE-STANDARDS.md)** (15 min) - Code style rules
4. **[07-AGENT-CODING-RULES.md](07-AGENT-CODING-RULES.md)** (15 min) - Mandatory constraints
5. **[08-COMMON-PATTERNS.md](08-COMMON-PATTERNS.md)** (reference) - Copy-paste templates

## Workflow: How to Write Code

### Step 1: Understand the Feature
Read the request carefully. Ask yourself: "What should this do?"

### Step 2: Read Relevant Docs
- Feature description in 04-GAME-DESIGN.md
- System specifications in 05-SYSTEMS-GUIDE.md
- Find pattern in 08-COMMON-PATTERNS.md

### Step 3: Identify Components & Systems
Question: Is this DATA or BEHAVIOR?
- **DATA** → Component class (in component/)
- **BEHAVIOR** → System class (in system/)

### Step 4: Check Code Standards
- Correct package name?
- Class named correctly? (PascalCase)
- Methods named correctly? (camelCase, start with verb)
- Constants at top? (SCREAMING_SNAKE_CASE)
- Structure matches template?

### Step 5: Find & Adapt Template
Copy template from 08-COMMON-PATTERNS.md, adapt to fit

### Step 6: Write Code with Checklist

```java
// ✅ Correct package
package com.hung.combat;

// ✅ Import only what's needed
import com.badlogic.gdx.math.Vector2;

/**
 * ✅ Javadoc explains what this does
 */
public class Weapon {
    
    // ✅ Constants at top, SCREAMING_SNAKE_CASE
    private static final float DEFAULT_FIRE_RATE = 0.3f;
    
    // ✅ Fields, camelCase
    private float fireRate;
    private float cooldown;
    
    // ✅ Constructor
    public Weapon(float fireRate) {
        this.fireRate = fireRate;
        this.cooldown = 0;
    }
    
    // ✅ Methods, camelCase, start with verb
    public void update(float delta) {
        cooldown -= delta;
    }
    
    // ✅ Getters at bottom
    public boolean canFire() { return cooldown <= 0; }
}
```

### Step 7: Verify Against Rules
Check all rules from 07-AGENT-CODING-RULES.md

### Step 8: Output Code
Present the code with:
1. File location
2. Clear explanation
3. Integration points
4. Next steps

## Common Patterns by Feature

### Movement
```
Read: 04-GAME-DESIGN "Core Mechanics" → Movement
Read: 05-SYSTEMS-GUIDE "Physics System"
Use: Pattern 4 (System Class) + Pattern 3 (Controller)
```

### Enemy AI
```
Read: 04-GAME-DESIGN "Enemy System"
Read: 05-SYSTEMS-GUIDE "AI System"
Use: Pattern 11 (State Machine) + Pattern 3 (Controller)
```

### Animation
```
Read: 05-SYSTEMS-GUIDE "Animation System"
Use: Pattern 8 (Animation)
```

### Collision
```
Read: 05-SYSTEMS-GUIDE "Collision System"
Use: Pattern 7 (Collision Detection)
```

### Weapon
```
Read: 04-GAME-DESIGN "Weapon System"
Read: 05-SYSTEMS-GUIDE "Combat System"
Use: Pattern 6 (Weapon System)
```

## Key Game Concepts

### 1. Delta Time (CRITICAL!)
```
Every frame-time calculation must include delta time:

❌ WRONG: position.x += 5;
✅ RIGHT: position.x += speed * delta;

Why: Game speed consistent regardless of FPS.
```

### 2. Components = Data
```
❌ WRONG: public void takeDamage(int amount) { }
✅ RIGHT: Data only, no methods
```

### 3. Inheritance Bad, Composition Good
```
❌ WRONG: Entity → Enemy → Chaser → FastChaser
✅ RIGHT: Entity with reusable components
```

### 4. Game Loop Order (NEVER CHANGE)
```
1. INPUT
2. UPDATE
3. PHYSICS
4. COLLISION
5. AUDIO
6. RENDER
```

### 5. Controller Interface
```
Every entity uses same interface:
- KeyboardController (player)
- AIController (enemy)
- GamepadController (future)
```

## Quick Reference

**Game Loop Order**:
```
INPUT → UPDATE (AI, Physics, Animation) → COLLISION → AUDIO → RENDER
```

**Package Structure**:
```
com.hung.core       // Game loop
com.hung.world      // World, levels
com.hung.entity     // Entities
com.hung.component  // Data components
com.hung.physics    // Collision
com.hung.combat     // Weapons
com.hung.ai         // AI/State machines
com.hung.input      // Controllers
com.hung.animation  // Sprites
com.hung.rendering  // Drawing
com.hung.audio      // Sounds
com.hung.item       // Pickups
com.hung.ui         // HUD
com.hung.util       // Helpers
```

## Do's and Don'ts

### ✅ DO
- Use delta time for all time-based calculations
- Put data in components
- Put behavior in systems
- Use pattern templates
- Follow naming conventions
- Cache resources
- Keep classes focused

### ❌ DON'T
- Put game logic in lwjgl3/
- Put behavior in components
- Use inheritance chains
- Load resources every frame
- Hardcode numbers
- Make classes >400 lines
- Call systems from other systems
- Use static methods excessively

## When You Get Stuck

| Question | Answer |
|----------|--------|
| Where does this code go? | Check [02-ARCHITECTURE.md](02-ARCHITECTURE.md) "Package Organization" |
| How do I structure this class? | Use Pattern from [08-COMMON-PATTERNS.md](08-COMMON-PATTERNS.md) |
| What should this system do? | Read [05-SYSTEMS-GUIDE.md](05-SYSTEMS-GUIDE.md) |
| How does the game mechanic work? | Read [04-GAME-DESIGN.md](04-GAME-DESIGN.md) |
| Am I breaking the rules? | Check [07-AGENT-CODING-RULES.md](07-AGENT-CODING-RULES.md) |
