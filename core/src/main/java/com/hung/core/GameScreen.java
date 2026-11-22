package com.hung.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.hung.ai.EnemyBrainSystem;
import com.hung.ai.PatrolSystem;
import com.hung.animation.AnimationSystem;
import com.hung.animation.EffectSystem;
import com.hung.animation.EnemyEffectSystem;
import com.hung.combat.AttackSystem;
import com.hung.combat.ContactDamageSystem;
import com.hung.combat.HealthSystem;
import com.hung.combat.ProjectileSystem;
import com.hung.combat.ShootingSystem;
import com.hung.combat.WeaponSwitchSystem;
import com.hung.entity.Entity;
import com.hung.input.CameraInput;
import com.hung.input.Controller;
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
import com.hung.save.GameSession;
import com.hung.save.SaveData;
import com.hung.save.SaveManager;
import com.hung.save.SaveSnapshot;
import com.hung.save.Settings;
import com.hung.ui.FpsOverlay;
import com.hung.ui.MinimapRenderer;
import com.hung.ui.MinimapState;
import com.hung.ui.PauseMenu;
import com.hung.ui.Toast;
import com.hung.ui.UiTheme;
import com.hung.util.Constants;
import com.hung.world.CheckpointEvent;
import com.hung.world.DoorSystem;
import com.hung.world.ExitReachedEvent;
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
 * I / F1 opens the character sheet and pauses; Esc opens the pause menu.
 *
 * Saving (GameSession → its save slot): at every new checkpoint (CheckpointEvent), when the exit is
 * reached (ExitReachedEvent, the run is marked complete) and when the player leaves — back to the
 * menu, quit, closing or minimising the window (LibGDX pause()). A continued run gets its save
 * applied before the first frame: position, respawn point, health, weapon in hand and ammo, explored rooms.
 */
public class GameScreen extends ScreenAdapter {

    private static final int DEBUG_TOGGLE_KEY = Input.Keys.F3;
    private static final int FREE_CAMERA_TOGGLE_KEY = Input.Keys.F2;
    private static final int LIGHTING_TOGGLE_KEY = Input.Keys.L;
    private static final int MAP_TOGGLE_KEY = Input.Keys.M;
    private static final int[] STATS_TOGGLE_KEYS = {Input.Keys.I, Input.Keys.F1};
    private static final int PAUSE_KEY = Input.Keys.ESCAPE;
    private static final String TAG = "GameScreen";
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
    private final EnemyEffectSystem enemyEffectSystem;
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
    private final MinimapState exploration;
    private final PauseMenu pauseMenu;
    private final Toast toast;
    private final FpsOverlay fps = new FpsOverlay();

    private final GameSession session;
    private final SaveManager saves;
    private final Settings settings;
    private final Navigator navigator;
    /** Last checkpoint touched (0-based), -1 = none yet. */
    private int checkpointIndex = -1;
    /** The player left this run (menu / quit): nothing is saved after that. */
    private boolean hasLeft;

    private boolean isDebugVisible;
    private boolean isCameraFree;
    /** The character sheet is open: the world is paused. */
    private boolean isStatsOpen;
    /** Seconds of game time, for flickering lights (stops while paused). */
    private float time;

    public GameScreen(SpriteBatch batch, World world, GameSession session, SaveManager saves, Settings settings,
                      UiTheme theme, Navigator navigator) {
        this.batch = batch;
        this.world = world;
        this.session = session;
        this.saves = saves;
        this.settings = settings;
        this.navigator = navigator;
        this.inputSystem = new InputSystem(world);
        this.patrolSystem = new PatrolSystem(world);
        this.enemyBrainSystem = new EnemyBrainSystem(world);
        this.physicsSystem = new PhysicsSystem(world, new CollisionSystem(world));
        this.hazardSystem = new HazardSystem(world, events);
        this.contactDamageSystem = new ContactDamageSystem(world);
        this.attackSystem = new AttackSystem(world);
        this.weaponSwitchSystem = new WeaponSwitchSystem(world);
        this.shootingSystem = new ShootingSystem(world);
        this.projectileSystem = new ProjectileSystem(world);
        this.effectSystem = new EffectSystem(world);
        this.enemyEffectSystem = new EnemyEffectSystem(world);
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
        this.exploration = new MinimapState(world.getLevel().getRooms().size);
        this.minimap = new MinimapRenderer(world.getLevel(), exploration);
        this.toast = new Toast(theme);
        this.pauseMenu = new PauseMenu(theme, settings, new PauseMenu.Actions() {
            @Override
            public void resume() {
                pauseMenu.close();
            }

            @Override
            public void toMainMenu() {
                leave();
                navigator.returnToMainMenu();
            }

            @Override
            public void quitGame() {
                leave();
                navigator.quit();
            }
        });
        events.subscribe(RoomTransitionEvent.class, cameraController);
        events.subscribe(RoomTransitionEvent.class, exploration);
        events.subscribe(CheckpointEvent.class, event -> {
            checkpointIndex = event.index;
            if (save(SaveData.REASON_CHECKPOINT)) toast.show("SAVE", "Checkpoint " + (event.index + 1) + " of " + event.count + " - saved");
        });
        events.subscribe(ExitReachedEvent.class, event -> {
            session.isComplete = true;
            save(SaveData.REASON_COMPLETE);
            pauseMenu.openComplete(crumb(), SaveManager.formatTime(session.playTime), roomsChip());
        });
        if (session.loaded != null) applySave(session.loaded);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(cameraInput);
        Entity player = world.getPlayer();
        Vector2 start = player != null ? player.getTransform().position : world.getLevel().getPlayerSpawn();
        cameraController.centerOn(start.x, start.y);
        doorSystem.start();
    }

    @Override
    public void render(float delta) {
        float frameTime = Math.min(delta, Constants.MAX_FRAME_TIME);
        if (pauseMenu.isOpen()) {
            pauseMenu.update();
        } else {
            handleToggles();
            if (Gdx.input.isKeyJustPressed(PAUSE_KEY)) {
                // Esc first closes the character sheet, then opens the menu
                if (isStatsOpen) isStatsOpen = false;
                else pauseMenu.open(crumb(), currentRoomChip(), SaveManager.formatTime(session.playTime), roomsChip());
            }
        }
        if (!isStatsOpen && !pauseMenu.isOpen()) {
            time += frameTime;
            session.playTime += frameTime;
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
        toast.render(batch, delta);
        pauseMenu.render(batch);
        if (settings.isFpsVisible) fps.render(batch);
    }

    /** LibGDX pause: the window was minimised or is closing — save where the player is. */
    @Override
    public void pause() {
        if (!hasLeft) save(session.isComplete ? SaveData.REASON_COMPLETE : SaveData.REASON_QUIT);
    }

    /** The player leaves the run: one last save, then nothing more is written. */
    private void leave() {
        if (hasLeft) return;
        save(session.isComplete ? SaveData.REASON_COMPLETE : SaveData.REASON_QUIT);
        hasLeft = true;
    }

    /** "HARD / SLOT 1" over the pause menu's title. */
    private String crumb() {
        return session.mode.label.toUpperCase() + " / SLOT " + (session.slot + 1);
    }

    private String currentRoomChip() {
        return exploration.getCurrentRoom() != null ? exploration.getCurrentRoom().id.toUpperCase() : "START";
    }

    private String roomsChip() {
        return exploration.getVisitedCount() + "/" + exploration.getRoomCount() + " ROOMS";
    }

    // ------------------------------------------------------------------ save / load

    /** Writes the run to its slot (true when written). */
    private boolean save(String reason) {
        SaveData data = SaveSnapshot.capture(world, session, exploration, checkpointIndex, reason, System.currentTimeMillis());
        if (data == null || !saves.write(session.slot, data)) return false;
        Gdx.app.log(TAG, "Saved slot " + (session.slot + 1) + " (" + reason + ") at (" + data.x + "," + data.y + ")");
        return true;
    }

    private void applySave(SaveData data) {
        if (world.getPlayer() == null) return;
        if (SaveSnapshot.apply(data, world, exploration)) {
            checkpointIndex = data.checkpointIndex;
        } else {
            Gdx.app.error(TAG, "The map changed since slot " + (session.slot + 1) + " was saved; starting from its spawn");
        }
        Gdx.app.log(TAG, "Continued slot " + (session.slot + 1) + " at " + world.getPlayer().getTransform().position);
    }

    @Override
    public void resize(int width, int height) {
        cameraController.resize(width, height);
        cameraController.clampTo(world.getLevel());
        hudRenderer.resize(width, height);
        lightingRenderer.resize(width, height);
        statsPanel.resize(width, height);
        pauseMenu.resize(width, height);
        toast.resize(width, height);
        fps.resize(width, height);
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
        fps.dispose();
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
            // The sheet doubles as a weapon picker. InputSystem doesn't run while it is open, so the
            // controller's slot press is read here instead, once.
            Entity player = world.getPlayer();
            ArsenalComponent arsenal = player.getArsenal();
            Controller controller = player.getController();
            int slot = controller != null ? controller.getWeaponSlotPressed() : -1;
            if (arsenal != null && slot >= 0) arsenal.requestedSlot = slot;
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
        enemyEffectSystem.update();
        effectSystem.update(delta);
    }
}
