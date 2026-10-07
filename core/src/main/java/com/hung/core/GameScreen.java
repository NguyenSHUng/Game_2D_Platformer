package com.hung.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.hung.ai.EnemyBrainSystem;
import com.hung.ai.PatrolSystem;
import com.hung.animation.AnimationSystem;
import com.hung.animation.EffectSystem;
import com.hung.combat.AttackSystem;
import com.hung.combat.ContactDamageSystem;
import com.hung.combat.HealthSystem;
import com.hung.combat.ProjectileSystem;
import com.hung.combat.ShootingSystem;
import com.hung.combat.WeaponSwitchSystem;
import com.hung.entity.Entity;
import com.hung.input.CameraInput;
import com.hung.input.InputSystem;
import com.hung.physics.CollisionSystem;
import com.hung.physics.HazardSystem;
import com.hung.physics.PhysicsSystem;
import com.hung.rendering.CameraController;
import com.hung.rendering.DebugRenderer;
import com.hung.rendering.FadeRenderer;
import com.hung.component.ArsenalComponent;
import com.hung.rendering.HudRenderer;
import com.hung.rendering.LightingRenderer;
import com.hung.rendering.StatsPanel;
import com.hung.rendering.RenderingSystem;
import com.hung.ui.MinimapRenderer;
import com.hung.ui.MinimapState;
import com.hung.util.Constants;
import com.hung.world.DoorSystem;
import com.hung.world.RoomTransition;
import com.hung.world.RoomTransitionEvent;
import com.hung.world.World;

/**
 * Gameplay screen. Runs the systems in the fixed game loop order:
 * input, weapon switch, AI (patrol, then enemy brains), physics (with tile collision), hazards, contact damage, melee attacks,
 * shooting, projectiles, health, animation, effects, then doors (room transitions), then rendering
 * (world, lights, debug overlay, door fade, HUD, minimap).
 *
 * Rooms are event-driven: DoorSystem publishes a RoomTransitionEvent on the EventBus; the camera
 * (clamps to the room) and the minimap (reveals it) are subscribers and never see the doors.
 * While a door fades to black the world is paused.
 *
 * The camera follows the player (-/= or the wheel zoom); F2 (or having no player) switches to the free
 * camera (CameraInput). The map's lights are drawn over the scene (L toggles); M opens the whole map;
 * I / F1 opens the character sheet and pauses.
 */
public class GameScreen extends ScreenAdapter {

    private static final int DEBUG_TOGGLE_KEY = Input.Keys.F3;
    private static final int FREE_CAMERA_TOGGLE_KEY = Input.Keys.F2;
    private static final int LIGHTING_TOGGLE_KEY = Input.Keys.L;
    private static final int MAP_TOGGLE_KEY = Input.Keys.M;
    private static final int[] STATS_TOGGLE_KEYS = {Input.Keys.I, Input.Keys.F1};
    private static final int[] WEAPON_SLOT_KEYS = {Input.Keys.NUM_1, Input.Keys.NUM_2, Input.Keys.NUM_3};
    /** Near-black behind the map, matching the bunker palette. */
    private static final Color BACKGROUND_COLOR = new Color(0x0a0a0aff);

    private final SpriteBatch batch;
    private final World world;

    private final InputSystem inputSystem;
    private final PatrolSystem patrolSystem;
    private final EnemyBrainSystem enemyBrainSystem;
    private final PhysicsSystem physicsSystem;
    private final HazardSystem hazardSystem;
    private final ContactDamageSystem contactDamageSystem;
    private final AttackSystem attackSystem;
    private final WeaponSwitchSystem weaponSwitchSystem;
    private final ShootingSystem shootingSystem;
    private final ProjectileSystem projectileSystem;
    private final EffectSystem effectSystem;
    private final HealthSystem healthSystem;
    private final AnimationSystem animationSystem;
    private final RenderingSystem renderingSystem;
    private final CameraController cameraController;
    private final CameraInput cameraInput;
    private final DebugRenderer debugRenderer;
    private final HudRenderer hudRenderer;
    private final LightingRenderer lightingRenderer;
    private final StatsPanel statsPanel;
    private final EventBus events = new EventBus();
    private final DoorSystem doorSystem;
    private final FadeRenderer fadeRenderer;
    private final MinimapRenderer minimap;

    private boolean isDebugVisible;
    private boolean isCameraFree;
    /** The character sheet is open: the world is paused. */
    private boolean isStatsOpen;
    /** Seconds of game time, for flickering lights (stops while paused). */
    private float time;

    public GameScreen(SpriteBatch batch, World world) {
        this.batch = batch;
        this.world = world;
        this.inputSystem = new InputSystem(world);
        this.patrolSystem = new PatrolSystem(world);
        this.enemyBrainSystem = new EnemyBrainSystem(world);
        this.physicsSystem = new PhysicsSystem(world, new CollisionSystem(world));
        this.hazardSystem = new HazardSystem(world);
        this.contactDamageSystem = new ContactDamageSystem(world);
        this.attackSystem = new AttackSystem(world);
        this.weaponSwitchSystem = new WeaponSwitchSystem(world);
        this.shootingSystem = new ShootingSystem(world);
        this.projectileSystem = new ProjectileSystem(world);
        this.effectSystem = new EffectSystem(world);
        this.healthSystem = new HealthSystem(world);
        this.animationSystem = new AnimationSystem(world);
        this.renderingSystem = new RenderingSystem(world, batch);
        this.cameraController = new CameraController();
        this.cameraInput = new CameraInput();
        this.debugRenderer = new DebugRenderer();
        this.hudRenderer = new HudRenderer();
        this.lightingRenderer = new LightingRenderer();
        this.statsPanel = new StatsPanel();
        this.doorSystem = new DoorSystem(world, events);
        this.fadeRenderer = new FadeRenderer();
        MinimapState exploration = new MinimapState(world.getLevel().getRooms().size);
        this.minimap = new MinimapRenderer(world.getLevel(), exploration);
        events.subscribe(RoomTransitionEvent.class, cameraController);
        events.subscribe(RoomTransitionEvent.class, exploration);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(cameraInput);
        cameraController.centerOn(world.getLevel().getPlayerSpawn().x, world.getLevel().getPlayerSpawn().y);
        doorSystem.start();
    }

    @Override
    public void render(float delta) {
        float frameTime = Math.min(delta, Constants.MAX_FRAME_TIME);
        handleToggles();
        if (!isStatsOpen) {
            time += frameTime;
            if (world.getRoomTransition().phase != RoomTransition.Phase.FADING_OUT) update(frameTime);
            doorSystem.update(frameTime);
        }

        Entity player = world.getPlayer();
        if (player != null && !isCameraFree) {
            cameraInput.updateFollowZoom(delta, cameraController);
            cameraController.follow(player.getTransform().position, world.getLevel(), frameTime);
        } else {
            cameraInput.update(delta, cameraController, world);
        }

        ScreenUtils.clear(BACKGROUND_COLOR);
        renderingSystem.render(batch, cameraController.getCamera());
        lightingRenderer.render(batch, cameraController.getCamera(), world, time, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        if (isDebugVisible) {
            debugRenderer.render(cameraController.getCamera(), world);
        }
        fadeRenderer.render(world.getRoomTransition());
        hudRenderer.render(batch, world, isCameraFree);
        minimap.render(batch, player != null ? player.getTransform().position : null, time);
        if (isStatsOpen) statsPanel.render(batch, world);
    }

    @Override
    public void resize(int width, int height) {
        cameraController.resize(width, height);
        cameraController.clampTo(world.getLevel());
        hudRenderer.resize(width, height);
        lightingRenderer.resize(width, height);
        statsPanel.resize(width, height);
        fadeRenderer.resize(width, height);
        minimap.resize(width, height);
    }

    @Override
    public void dispose() {
        renderingSystem.dispose();
        debugRenderer.dispose();
        hudRenderer.dispose();
        lightingRenderer.dispose();
        statsPanel.dispose();
        fadeRenderer.dispose();
        minimap.dispose();
    }

    /** Keys that work even while the game is paused by the character sheet. */
    private void handleToggles() {
        if (Gdx.input.isKeyJustPressed(DEBUG_TOGGLE_KEY)) {
            isDebugVisible = !isDebugVisible;
        }
        if (Gdx.input.isKeyJustPressed(LIGHTING_TOGGLE_KEY)) {
            lightingRenderer.setEnabled(!lightingRenderer.isEnabled());
        }
        if (Gdx.input.isKeyJustPressed(MAP_TOGGLE_KEY)) {
            minimap.toggleExpanded();
        }
        for (int key : STATS_TOGGLE_KEYS) {
            if (Gdx.input.isKeyJustPressed(key) && world.getPlayer() != null) isStatsOpen = !isStatsOpen;
        }
        if (isStatsOpen) {
            // The sheet doubles as a weapon picker
            ArsenalComponent arsenal = world.getPlayer().getArsenal();
            for (int slot = 0; slot < WEAPON_SLOT_KEYS.length && arsenal != null; slot++) {
                if (Gdx.input.isKeyJustPressed(WEAPON_SLOT_KEYS[slot])) arsenal.requestedSlot = slot;
            }
            weaponSwitchSystem.update();
        }
        if (Gdx.input.isKeyJustPressed(FREE_CAMERA_TOGGLE_KEY) && world.getPlayer() != null) {
            isCameraFree = !isCameraFree;
            inputSystem.setEnabled(!isCameraFree);
        }
    }

    private void update(float delta) {

        inputSystem.update(delta);
        weaponSwitchSystem.update();
        patrolSystem.update();
        enemyBrainSystem.update(delta);
        physicsSystem.update(delta);
        hazardSystem.update();
        contactDamageSystem.update();
        attackSystem.update();
        shootingSystem.update(delta);
        projectileSystem.update(delta);
        healthSystem.update(delta);
        animationSystem.update(delta);
        effectSystem.update(delta);
    }
}
