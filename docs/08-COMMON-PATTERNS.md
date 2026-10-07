# 08 - Common Patterns

[← Index](README.md)

Quick reference for typical game development patterns. Use these as templates.

## Pattern 1: Entity with Components

```java
package com.hung.entity;

public class Player extends Entity {
    // Components
    private TransformComponent transform;
    private HealthComponent health;
    private WeaponComponent weapon;
    private AnimationComponent animation;
    
    // Controllers
    private Controller controller;
    
    // Constants
    private static final float MOVE_SPEED = 200f;
    
    // Constructor
    public Player(float x, float y, Controller controller) {
        // Initialize transform
        this.transform = new TransformComponent();
        this.transform.position.set(x, y);
        this.transform.velocity.set(0, 0);
        
        // Initialize health
        this.health = new HealthComponent(100f);
        
        // Initialize weapon
        this.weapon = new WeaponComponent();
        this.weapon.setWeapon(new BasicGun());
        
        // Initialize animation
        this.animation = new AnimationComponent();
        
        // Set controller
        this.controller = controller;
    }
    
    @Override
    public void update(float delta) {
        // Get input
        Vector2 moveDir = controller.getMovementDirection();
        Vector2 aimDir = controller.getAimDirection();
        
        // Update velocity from input
        transform.velocity.set(moveDir).scl(MOVE_SPEED);
        
        // Update position (physics will be separate)
        transform.position.x += transform.velocity.x * delta;
        transform.position.y += transform.velocity.y * delta;
        
        // Update rotation from aim
        transform.rotation = aimDir.angleDeg();
        
        // Update weapon
        if (controller.isFirePressed()) {
            weapon.fire(transform.position, aimDir);
        }
        weapon.update(delta);
        
        // Update health status
        if (health.health <= 0) {
            health.isAlive = false;
        }
    }
    
    @Override
    public void render(Batch batch) {
        // Render player sprite at position with rotation
        batch.draw(sprite, transform.position.x, transform.position.y,
                  sprite.getWidth() / 2, sprite.getHeight() / 2,
                  sprite.getWidth(), sprite.getHeight(),
                  1, 1, transform.rotation);
    }
    
    // Getters
    public TransformComponent getTransform() { return transform; }
    public HealthComponent getHealth() { return health; }
    public WeaponComponent getWeapon() { return weapon; }
}
```

## Pattern 2: Simple Component

```java
package com.hung.component;

import com.badlogic.gdx.math.Vector2;

/**
 * Stores position, velocity, and rotation.
 * No behavior - systems update these values.
 */
public class TransformComponent {
    public Vector2 position;
    public Vector2 velocity;
    public float rotation;  // degrees
    
    public TransformComponent() {
        this.position = new Vector2(0, 0);
        this.velocity = new Vector2(0, 0);
        this.rotation = 0f;
    }
}
```

## Pattern 3: Controller Interface & Implementation

```java
// INTERFACE
package com.hung.input;

import com.badlogic.gdx.math.Vector2;

public interface Controller {
    Vector2 getMovementDirection();
    Vector2 getAimDirection();
    boolean isFirePressed();
    boolean isAbilityPressed();
}

// KEYBOARD IMPLEMENTATION
package com.hung.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;

public class KeyboardController implements Controller {
    
    @Override
    public Vector2 getMovementDirection() {
        Vector2 direction = new Vector2();
        
        if (Gdx.input.isKeyPressed(Input.Keys.W)) direction.y += 1;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) direction.y -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) direction.x -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) direction.x += 1;
        
        return direction.nor();
    }
    
    @Override
    public Vector2 getAimDirection() {
        Vector2 mousePos = new Vector2(Gdx.input.getX(), Gdx.input.getY());
        Vector2 playerPos = new Vector2(400, 300);
        return mousePos.sub(playerPos).nor();
    }
    
    @Override
    public boolean isFirePressed() {
        return Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
    }
    
    @Override
    public boolean isAbilityPressed() {
        return Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
    }
}
```

## Pattern 4: System Class

```java
package com.hung.physics;

import com.hung.entity.Entity;
import java.util.List;

/**
 * Physics system - handles movement.
 * Reads from TransformComponent.
 * Writes to TransformComponent (position).
 */
public class PhysicsSystem {
    private List<Entity> entities;
    
    public PhysicsSystem(List<Entity> entities) {
        this.entities = entities;
    }
    
    public void update(float delta) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            if (!entity.isAlive()) continue;
            
            TransformComponent transform = entity.getTransform();
            
            // Apply velocity to position
            transform.position.x += transform.velocity.x * delta;
            transform.position.y += transform.velocity.y * delta;
        }
    }
}
```

## Pattern 5: Game Loop

```java
package com.hung.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.hung.world.World;

public class Game extends ApplicationAdapter {
    
    private SpriteBatch batch;
    private World world;
    private GameState gameState;
    
    // Systems
    private PhysicsSystem physicsSystem;
    private AISystem aiSystem;
    private CollisionSystem collisionSystem;
    private RenderingSystem renderingSystem;
    
    @Override
    public void create() {
        batch = new SpriteBatch();
        world = new World();
        world.generateLevel(32, 32);
        
        Player player = new Player(100, 100, new KeyboardController());
        world.addEntity(player);
        
        physicsSystem = new PhysicsSystem(world.getEntities());
        aiSystem = new AISystem(world.getEntities());
        collisionSystem = new CollisionSystem(world.getEntities());
        renderingSystem = new RenderingSystem(world);
        
        gameState = GameState.PLAYING;
    }
    
    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        
        switch (gameState) {
            case PLAYING:
                update(delta);
                break;
            case PAUSED:
                break;
            case GAME_OVER:
                break;
        }
        
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        
        batch.begin();
        renderingSystem.render(batch);
        batch.end();
    }
    
    private void update(float delta) {
        aiSystem.update(delta);
        world.update(delta);
        physicsSystem.update(delta);
        collisionSystem.update(delta);
    }
    
    @Override
    public void dispose() {
        batch.dispose();
    }
}
```

## Pattern 6: Weapon System

```java
package com.hung.combat;

import com.badlogic.gdx.math.Vector2;

public class Weapon {
    private String name;
    private float damage;
    private float fireRate;
    private float cooldown;
    private float projectileSpeed;
    
    public Weapon(String name, float damage, float fireRate, float projectileSpeed) {
        this.name = name;
        this.damage = damage;
        this.fireRate = fireRate;
        this.cooldown = 0;
        this.projectileSpeed = projectileSpeed;
    }
    
    public void update(float delta) {
        cooldown -= delta;
        if (cooldown < 0) cooldown = 0;
    }
    
    public boolean canFire() {
        return cooldown <= 0;
    }
    
    public Projectile fire(Vector2 position, Vector2 direction) {
        if (!canFire()) return null;
        
        Projectile projectile = new Projectile(position.x, position.y, damage);
        projectile.setVelocity(direction.x * projectileSpeed, direction.y * projectileSpeed);
        
        cooldown = fireRate;
        
        return projectile;
    }
}
```

## Pattern 7: Collision Detection (Simple AABB)

```java
package com.hung.physics;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class Hitbox {
    public Rectangle rect;
    
    public Hitbox(float x, float y, float width, float height) {
        this.rect = new Rectangle(x, y, width, height);
    }
    
    public void update(Vector2 position) {
        rect.x = position.x - rect.width / 2;
        rect.y = position.y - rect.height / 2;
    }
    
    public boolean overlaps(Hitbox other) {
        return rect.overlaps(other.rect);
    }
}

public class CollisionSystem {
    private List<Entity> entities;
    
    public CollisionSystem(List<Entity> entities) {
        this.entities = entities;
    }
    
    public void update(float delta) {
        for (int i = 0; i < entities.size(); i++) {
            for (int j = i + 1; j < entities.size(); j++) {
                checkCollision(entities.get(i), entities.get(j));
            }
        }
    }
    
    private void checkCollision(Entity a, Entity b) {
        Hitbox hitboxA = a.getHitbox();
        Hitbox hitboxB = b.getHitbox();
        
        if (hitboxA != null && hitboxB != null) {
            if (hitboxA.overlaps(hitboxB)) {
                onCollision(a, b);
            }
        }
    }
}
```

## Pattern 8: Animation

```java
package com.hung.animation;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.List;

public class Animation {
    private List<TextureRegion> frames;
    private float frameDuration;
    private float elapsedTime;
    private boolean isLooping;
    
    public Animation(List<TextureRegion> frames, float frameDuration, boolean isLooping) {
        this.frames = frames;
        this.frameDuration = frameDuration;
        this.elapsedTime = 0;
        this.isLooping = isLooping;
    }
    
    public void update(float delta) {
        elapsedTime += delta;
    }
    
    public TextureRegion getCurrentFrame() {
        int frameIndex;
        if (isLooping) {
            frameIndex = (int)(elapsedTime / frameDuration) % frames.size();
        } else {
            frameIndex = (int)(elapsedTime / frameDuration);
            if (frameIndex >= frames.size()) {
                frameIndex = frames.size() - 1;
            }
        }
        return frames.get(frameIndex);
    }
}
```

## Pattern 9: Resource Manager

```java
package com.hung.util;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.audio.Sound;

public class ResourceLoader {
    private AssetManager assetManager;
    
    public ResourceLoader() {
        this.assetManager = new AssetManager();
    }
    
    public void loadTextures() {
        assetManager.load("player.png", Texture.class);
        assetManager.load("enemy.png", Texture.class);
        assetManager.finishLoading();
    }
    
    public Texture getTexture(String name) {
        return assetManager.get(name, Texture.class);
    }
    
    public void dispose() {
        assetManager.dispose();
    }
}
```

## Pattern 10: Factory Pattern

```java
package com.hung.entity;

public class EnemyFactory {
    
    public static Enemy createChaserEnemy(float x, float y) {
        Enemy enemy = new Enemy(x, y);
        enemy.setAI(new ChaserAI(enemy));
        enemy.setWeapon(new BasicGun());
        enemy.setSpeed(150f);
        enemy.setMaxHealth(50);
        return enemy;
    }
    
    public static Enemy createTurretEnemy(float x, float y) {
        Enemy enemy = new Enemy(x, y);
        enemy.setAI(new TurretAI(enemy));
        enemy.setWeapon(new ShotgunWeapon());
        enemy.setSpeed(0);
        enemy.setMaxHealth(100);
        return enemy;
    }
}
```

## Pattern 11: State Machine

```java
package com.hung.ai;

public interface State {
    void onEnter();
    void update(float delta);
    void onExit();
    State getNextState();
}

public class StateMachine {
    private State currentState;
    
    public StateMachine(State initialState) {
        this.currentState = initialState;
        currentState.onEnter();
    }
    
    public void update(float delta) {
        State nextState = currentState.getNextState();
        if (nextState != currentState) {
            currentState.onExit();
            currentState = nextState;
            currentState.onEnter();
        }
        
        currentState.update(delta);
    }
}

public class IdleState implements State {
    private Enemy enemy;
    
    @Override
    public void onEnter() {
        enemy.setVelocity(0, 0);
    }
    
    @Override
    public void update(float delta) {
        // Do nothing
    }
    
    @Override
    public State getNextState() {
        if (enemy.canSeePlayer()) {
            return new ChaseState(enemy);
        }
        return this;
    }
}
```
