package com.hung.ui.menu;

import com.badlogic.gdx.utils.Array;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * One page of a menu, as the approved design shows it: the title,
 * optional chips and text lines, an optional key table, then the items. Built with a fluent API — the
 * calls after item(...) (detail, value, toggle, lock, destructive, enabled) apply to that item:
 * <pre>
 * new Menu("Settings")
 *     .item("Fullscreen", toggle).toggle(() -> settings.isFullscreen)
 *     .item("Back", view::pop);
 * </pre>
 * MenuView draws the page on top of its stack and handles the input.
 */
public class Menu {

    /** Chip / tag colours. */
    public enum Tone { PLAIN, HOT, OK, BAD }

    public final String title;
    /** Wider panel (controls). */
    public boolean isWide;
    /** Red hazard strip: the page confirms something that can't be undone. */
    public boolean isDangerous;
    public final Array<String> lines = new Array<>();
    public final Array<Chip> chips = new Array<>();
    /** Key table rows: keys (space separated) and what they do. */
    public final Array<String[]> keys = new Array<>();
    public final Array<Item> items = new Array<>();
    /** Index of the highlighted item. */
    public int selected = -1;

    public static class Chip {
        public final String text;
        public final Tone tone;

        Chip(String text, Tone tone) {
            this.text = text;
            this.tone = tone;
        }
    }

    public static class Item {
        public final String label;
        public final Runnable action;
        /** Small text under the label; null = none. */
        public String detail;
        /** Text on the right, read every frame; null = none. */
        public Supplier<String> value;
        /** An ON / OFF switch on the right, read every frame; null = none. */
        public BooleanSupplier toggle;
        /** A tag on the right of a disabled item ("LOCKED"); null = none. */
        public String lock;
        /** Deletes or overwrites: red highlight. */
        public boolean isDestructive;
        public boolean isEnabled = true;

        Item(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }

    public Menu(String title) {
        this.title = title;
    }

    public Menu wide() {
        isWide = true;
        return this;
    }

    public Menu dangerous() {
        isDangerous = true;
        return this;
    }

    public Menu line(String text) {
        lines.add(text);
        return this;
    }

    public Menu chip(String text, Tone tone) {
        chips.add(new Chip(text, tone));
        return this;
    }

    public Menu key(String keys, String action) {
        this.keys.add(new String[] {keys, action});
        return this;
    }

    public Menu item(String label, Runnable action) {
        items.add(new Item(label, action));
        return this;
    }

    public Menu detail(String text) {
        last().detail = text;
        return this;
    }

    public Menu value(Supplier<String> value) {
        last().value = value;
        return this;
    }

    public Menu toggle(BooleanSupplier toggle) {
        last().toggle = toggle;
        return this;
    }

    public Menu lock(String text) {
        last().lock = text;
        return this;
    }

    public Menu destructive() {
        last().isDestructive = true;
        return this;
    }

    public Menu enabled(boolean isEnabled) {
        last().isEnabled = isEnabled;
        return this;
    }

    /** Index of the first enabled item, -1 if none. */
    int firstEnabled() {
        for (int i = 0; i < items.size; i++) if (items.get(i).isEnabled) return i;
        return -1;
    }

    private Item last() {
        if (items.isEmpty()) throw new IllegalStateException("Add an item first");
        return items.peek();
    }
}
