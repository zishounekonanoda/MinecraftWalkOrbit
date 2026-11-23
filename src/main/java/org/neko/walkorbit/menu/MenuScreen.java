package org.neko.walkorbit.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One menu screen, described once and drawn either as a dialog or as a chest (see {@link MenuService}).
 * Both get the same icons, labels and descriptions from here.
 *
 * @param title   screen title
 * @param header  a few short lines at the top (status, errors). Longer explanations go in tooltips.
 * @param items   display-only icons with one line each (e.g. the current combination)
 * @param inputs  fields whose values are written back through their setters before any button runs
 * @param buttons the actions
 * @param exit    the way back / out. Kept full width and placed last.
 */
public record MenuScreen(Component title, List<Component> header, List<Item> items, List<Input> inputs,
                         List<Button> buttons, Button exit) {

    /**
     * A display-only icon and its line.
     *
     * @param prefix optional sprite shown before the line in dialogs only
     */
    public record Item(ItemStack icon, Component prefix, Component line) {
        public Item(ItemStack icon, Component line) {
            this(icon, null, line);
        }
    }

    /**
     * @param label   button text (no inline sprites; see {@code prefix})
     * @param prefix  optional sprite shown before the label in dialogs only (chest items can't draw it)
     * @param tooltip description, shown on hover in dialogs and as lore in chests
     * @param icon    the chest item
     */
    public record Button(Component label, Component prefix, List<Component> tooltip, ItemStack icon,
                         Consumer<Player> onClick) {
        public Button(Component label, List<Component> tooltip, ItemStack icon, Consumer<Player> onClick) {
            this(label, null, tooltip, icon, onClick);
        }
    }

    /** A value the player can change on the screen. */
    public sealed interface Input permits TextField, Slider, Choice {
        String key();

        Component label();

        ItemStack icon();
    }

    /** Free text. Dialog: text box. Chest: click, then type it in chat. */
    public record TextField(String key, Component label, ItemStack icon, int maxLength,
                            Supplier<String> get, Consumer<String> set) implements Input {
    }

    /** A number in a range. Dialog: slider. Chest: left/right click +/- step, with shift +/- bigStep. */
    public record Slider(String key, Component label, ItemStack icon, float min, float max, float step, float bigStep,
                         Supplier<Float> get, Consumer<Float> set) implements Input {
    }

    /** One of several options. Dialog: drop-down. Chest: left/right click cycles. */
    public record Choice(String key, Component label, ItemStack icon, List<Option> options,
                         Supplier<String> get, Consumer<String> set) implements Input {
        public record Option(String id, Component label) {
        }
    }
}
