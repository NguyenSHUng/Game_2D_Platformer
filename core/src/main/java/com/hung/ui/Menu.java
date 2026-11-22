package com.hung.ui;

import com.badlogic.gdx.utils.Array;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * One page of a menu, as the approved design shows it: a crumb ("NEW GAME / 1 OF 2") over the title,
 * optional chips and text lines, an optional key table, then the items. Built with a fluent API — the
 * calls after item(...) / slot(...) (detail, value, toggle, lock, destructive, enabled) apply to that item:
 * <pre>
 * new Menu("Settings").crumb("SYSTEM / SETTINGS").line("Changes are saved and applied at once.")
 *     .item("Fullscreen", toggle).toggle(() -> settings.isFullscreen)
 *     .item("Back", view::pop);
 * </pre>
 * MenuView draws the page on top of its stack and handles the input.
 */
public class Menu {

    /** Chip / tag colours. */
    public enum Tone { PLAIN, HOT, OK, BAD }

    public final String title;
    public String crumb = "";
    /** Wider panel (slot cards, controls). */
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
        /** Drawn as a save slot card instead of a plain row; null = plain row. */
        public SlotCard slot;

        Item(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }

    /** What a save slot card shows. */
    public static class SlotCard {
        /** 1-based. */
        public final int number;
        /** Empty slot: only "Empty slot". */
        public final boolean isEmpty;
        public String mode;
        public String tag;
        public Tone tagTone = Tone.HOT;
        public String time;
        public int rooms;
        public int roomCount;
        public String date;
        /** Why it can't be played (corrupt / incompatible); null = fine. */
        public String problem;

        public SlotCard(int number, boolean isEmpty) {
            this.number = number;
            this.isEmpty = isEmpty;
        }
    }

    public Menu(String title) {
        this.title = title;
    }

    public Menu crumb(String text) {
        crumb = text;
        return this;
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

    public Menu slot(SlotCard card, Runnable action) {
        Item item = new Item("Slot " + card.number, action);
        item.slot = card;
        items.add(item);
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

    /** Highlights the item with this label if it exists and is enabled. */
    public Menu select(String label) {
        for (int i = 0; i < items.size; i++) {
            if (items.get(i).label.equals(label) && items.get(i).isEnabled) selected = i;
        }
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
