package com.hung.world.system;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.combat.LootDrop;
import com.hung.combat.LootTable;
import com.hung.component.ChestComponent;
import com.hung.component.HealthComponent;
import com.hung.component.InteractComponent;
import com.hung.component.SpriteComponent;
import com.hung.core.event.EventBus;
import com.hung.entity.Entity;
import com.hung.physics.Hitboxes;
import com.hung.world.World;
import com.hung.world.event.ChestOpenedEvent;

/**
 * Treasure chests, CLOSED → OPENING → OPENED, each opened once:
 *  - the interact key (InteractComponent) opens the closed chest the living player stands next to
 *    (one chest per press; the request is used up this frame either way);
 *  - the opening plays for OPEN_SECONDS, then the chest drops its one item: chosen by the LootTable
 *    from the chest's id (the same every run) and thrown out by LootDrop to a safe spot in its room.
 * The chest plays its opening strip and keeps its last frame; without art an opened chest is drawn darkened.
 * Reads: InteractComponent, the player's Health / Collider, ChestComponent, the tile grid.
 * Writes: ChestComponent, SpriteComponent, InteractComponent; adds pickup entities. Publishes ChestOpenedEvent.
 */
public class ChestSystem {

    /** Seconds the chest takes to open before the item comes out. */
    public static final float OPEN_SECONDS = 0.4f;
    /** The player may stand this far beside the chest's body, world units. */
    public static final float REACH = 12f;
    /** Opened chests without opening art are drawn in this colour. */
    private static final Color OPENED_TINT = new Color(0.45f, 0.45f, 0.45f, 1f);

    private final World world;
    private final LootTable lootTable;
    private final EventBus events;
    private final ChestOpenedEvent opened = new ChestOpenedEvent();
    // Reused every frame to avoid allocating in the game loop
    private final Rectangle playerBox = new Rectangle();
    private final Rectangle reachBox = new Rectangle();

    /** Without anyone listening to opened chests (tests). */
    public ChestSystem(World world, LootTable lootTable) {
        this(world, lootTable, new EventBus());
    }

    public ChestSystem(World world, LootTable lootTable, EventBus events) {
        this.world = world;
        this.lootTable = lootTable;
        this.events = events;
    }

    public void update(float delta) {
        boolean wantsToOpen = takeRequest();
        Array<Entity> entities = world.getEntities();
        int count = entities.size;
        for (int i = 0; i < count; i++) {
            Entity entity = entities.get(i);
            ChestComponent chest = entity.getChest();
            if (chest == null) continue;
            switch (chest.state) {
                case CLOSED -> {
                    if (wantsToOpen && isWithinReach(entity)) {
                        startOpening(entity, chest);
                        wantsToOpen = false;
                    }
                }
                case OPENING -> {
                    chest.stateTime += delta;
                    if (chest.stateTime >= OPEN_SECONDS) finishOpening(entity, chest);
                }
                case OPENED -> chest.stateTime += delta;
            }
        }
    }

    /** True on the frame the living player asked to interact; clears the request. */
    private boolean takeRequest() {
        Entity player = world.getPlayer();
        InteractComponent interact = player != null ? player.getInteract() : null;
        if (interact == null || !interact.isRequested) return false;
        interact.isRequested = false;
        HealthComponent health = player.getHealth();
        return player.getCollider() != null && (health == null || !health.isDead());
    }

    /** The player's body touches the chest's body grown by REACH to each side. */
    private boolean isWithinReach(Entity chest) {
        Hitboxes.body(world.getPlayer(), playerBox);
        Hitboxes.body(chest, reachBox);
        reachBox.x -= REACH;
        reachBox.width += 2f * REACH;
        return reachBox.overlaps(playerBox);
    }

    private static void startOpening(Entity entity, ChestComponent chest) {
        chest.state = ChestComponent.State.OPENING;
        chest.stateTime = 0f;
        SpriteComponent sprite = entity.getSprite();
        if (sprite == null) return;
        if (chest.opening != null) sprite.animation = chest.opening;
        sprite.stateTime = 0f;
    }

    /**
     * Shows the chest open and done (also for chests a save says were opened): the last frame of its
     * opening, or the darkened tile without art. No item, no event.
     */
    public static void markOpened(Entity entity) {
        ChestComponent chest = entity.getChest();
        chest.state = ChestComponent.State.OPENED;
        chest.stateTime = 0f;
        SpriteComponent sprite = entity.getSprite();
        if (sprite == null) return;
        if (chest.opening != null) {
            // The strip doesn't loop: past its end it holds the last (open) frame
            sprite.animation = chest.opening;
            sprite.stateTime = chest.opening.getAnimationDuration();
        } else {
            sprite.tint.set(OPENED_TINT);
        }
    }

    private void finishOpening(Entity entity, ChestComponent chest) {
        markOpened(entity);
        Vector2 at = entity.getTransform().position;
        world.addEntity(LootDrop.throwFromChest(lootTable.pick(chest.id, chest.tier),
            world.getLevel().getGrid(), chest.room, at.x, at.y));
        events.publish(opened.set(chest.id, chest.tier));
    }
}
