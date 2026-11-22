# 07 - Agent Coding Rules

[← Index](README.md)

**Use this document to guide AI agents when asking them to code features.**

## CRITICAL RULES (Always Enforce)

### 1. Package Structure - MANDATORY
- All code must be in `com.hung.{system}`
- Valid systems (the packages that exist): `core`, `world`, `entity`, `component`, `ai`, `combat`, `physics`, `input`, `animation`, `rendering`, `ui`, `save`, `util`
- **Never create packages like**: `models/`, `services/`, `helpers/`, `managers/`, `factories/` at root level
- Sub-packages only when system becomes large (e.g., `com.hung.ai.states`)

### 2. No Game Logic in lwjgl3
- `lwjgl3/` is ONLY for launcher configuration
- All game logic stays in `core/`
- lwjgl3 should have minimal code

### 3. Delta Time Mandatory
- Every physics/movement update must use delta time
- Never hardcode frame increments

```java
❌ position.x += 5;
✅ position.x += speed * delta;
```

### 4. Composition Over Inheritance
- Entity hierarchy max 2 levels: `Entity → Player/Enemy`
- Never create: `Enemy → ChaserEnemy → FastChaser → VeryFastChaser`
- Use components to add behavior instead

### 5. Components Are Data, Not Behavior
- Components store data only
- Systems contain behavior logic
- Exception: a component may have small methods that keep its own data valid (`HealthComponent.applyDamage`,
  `AnimationComponent.useStance`); game rules that involve other components stay in systems

```java
❌ WRONG
class TransformComponent {
    public void update(float delta) { }  // NO!
}

✅ RIGHT
class TransformComponent {
    public Vector2 position;
    public Vector2 velocity;
    // Data only, no methods
}
```

### 6. One Responsibility Per Class
- Each class solves one problem
- Max ~400 lines per file

### 7. No Magic Numbers
- All constants must be `public static final` at class top
- Place numeric values in constants with clear names

```java
❌ position.x += 5.5f;
✅ position.x += MOVE_SPEED * delta;  // where MOVE_SPEED = 200f
```

### 8. Resource Loading (One-Time Only)
- Load textures/sounds in constructor or init
- Cache and reuse
- Never load in render() or update()

## CODE STRUCTURE RULES

### 9. Naming Conventions
- Classes: `PascalCase` → `Player`, `CollisionSystem`
- Methods: `camelCase`, start with verb → `update()`, `getHealth()`
- Variables: `camelCase` → `position`, `currentHealth`
- Constants: `SCREAMING_SNAKE_CASE` → `PLAYER_SPEED`, `TILE_SIZE`
- Booleans: prefix with `is/has/can` → `isAlive`, `hasWeapon`, `canShoot`

### 10. Method Ordering in Classes
```java
public class Example {
    // 1. Constants (top)
    private static final float SPEED = 100f;
    
    // 2. Fields
    private Vector2 position;
    private float health;
    
    // 3. Constructor
    public Example() { }
    
    // 4. Public methods
    public void update(float delta) { }
    
    // 5. Private helper methods
    private void checkCollisions() { }
    
    // 6. Getters (bottom)
    public Vector2 getPosition() { return position; }
}
```

### 11. Comment Only Where Needed
- Comment the why, a contract or invariant, or a non-obvious formula; never restate the code
- Short Javadoc on public classes and on public methods whose name doesn't say everything
- No history, no commented-out code, no notes about the change itself
- Full rule: [03-CODE-STANDARDS.md → Comments](03-CODE-STANDARDS.md#comments)

## WHEN GENERATING CODE - CHECKLIST

Before outputting code, agent must verify:

- [ ] Is code in correct package? (`com.hung.*`)
- [ ] Is game logic away from lwjgl3?
- [ ] Does code use delta time for movement?
- [ ] Does code avoid inheritance chains?
- [ ] Are components data-only (no behavior)?
- [ ] Are systems behavior-only (use component data)?
- [ ] Are all magic numbers in constants?
- [ ] Are resources cached, not reloaded?
- [ ] Is naming consistent with conventions?
- [ ] Can class be split into smaller pieces?
- [ ] Are imports clean (nothing unused)?
- [ ] Are comments only where needed, short, and still true?
- [ ] Does code compile as written?

## FORBIDDEN PATTERNS

Never generate code that:

1. ❌ Puts behavior in components
2. ❌ Creates deep inheritance chains
3. ❌ Uses hardcoded numbers outside constants
4. ❌ Loads resources every frame
5. ❌ Puts game logic in lwjgl3/
6. ❌ Creates utility packages at root level
7. ❌ Uses static methods excessively
8. ❌ Ignores delta time
9. ❌ Creates god classes (>500 lines)
10. ❌ Allocates objects in hot loops
