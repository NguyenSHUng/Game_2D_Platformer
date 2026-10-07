package com.hung.entity;

import com.hung.component.AnimationComponent;
import com.hung.component.ArsenalComponent;
import com.hung.component.AttackComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.DoorComponent;
import com.hung.component.EnemyBrainComponent;
import com.hung.component.EffectComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.PatrolComponent;
import com.hung.component.ProjectileComponent;
import com.hung.component.SpriteComponent;
import com.hung.component.TransformComponent;
import com.hung.component.WeaponComponent;
import com.hung.input.Controller;

/**
 * A game object built by composition. Every entity has a transform;
 * other components are optional (null when absent) and systems skip entities without them.
 * Build entities through {@link EntityFactory} instead of subclassing.
 */
public class Entity {

    private final TransformComponent transform = new TransformComponent();
    private MovementComponent movement;
    private ColliderComponent collider;
    private AnimationComponent animation;
    private WeaponComponent weapon;
    private PatrolComponent patrol;
    private AttackComponent attack;
    private HealthComponent health;
    private ArsenalComponent arsenal;
    private SpriteComponent sprite;
    private EffectComponent effect;
    private ProjectileComponent projectile;
    private DoorComponent door;
    private EnemyBrainComponent brain;
    private Controller controller;

    public TransformComponent getTransform() { return transform; }
    public MovementComponent getMovement() { return movement; }
    public ColliderComponent getCollider() { return collider; }
    public AnimationComponent getAnimation() { return animation; }
    public WeaponComponent getWeapon() { return weapon; }
    public PatrolComponent getPatrol() { return patrol; }
    public AttackComponent getAttack() { return attack; }
    public HealthComponent getHealth() { return health; }
    public ArsenalComponent getArsenal() { return arsenal; }
    public SpriteComponent getSprite() { return sprite; }
    public EffectComponent getEffect() { return effect; }
    public ProjectileComponent getProjectile() { return projectile; }
    public DoorComponent getDoor() { return door; }
    public EnemyBrainComponent getBrain() { return brain; }
    public Controller getController() { return controller; }

    public void setMovement(MovementComponent movement) { this.movement = movement; }
    public void setCollider(ColliderComponent collider) { this.collider = collider; }
    public void setAnimation(AnimationComponent animation) { this.animation = animation; }
    public void setWeapon(WeaponComponent weapon) { this.weapon = weapon; }
    public void setPatrol(PatrolComponent patrol) { this.patrol = patrol; }
    public void setAttack(AttackComponent attack) { this.attack = attack; }
    public void setHealth(HealthComponent health) { this.health = health; }
    public void setArsenal(ArsenalComponent arsenal) { this.arsenal = arsenal; }
    public void setSprite(SpriteComponent sprite) { this.sprite = sprite; }
    public void setEffect(EffectComponent effect) { this.effect = effect; }
    public void setProjectile(ProjectileComponent projectile) { this.projectile = projectile; }
    public void setDoor(DoorComponent door) { this.door = door; }
    public void setBrain(EnemyBrainComponent brain) { this.brain = brain; }
    public void setController(Controller controller) { this.controller = controller; }
}
