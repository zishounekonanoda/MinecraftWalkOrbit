package org.neko.walkorbit.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.neko.walkorbit.LocaleManager;
import org.neko.walkorbit.WalkOrbit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shows {@link MenuScreen}s the same way everywhere:
 * <ul>
 *   <li>as a dialog when the player can get one (client 1.21.6+, dialogs not turned off, showing it worked),
 *       otherwise as a chest with the same icons, labels and descriptions;</li>
 *   <li>descriptions as tooltips (dialog) / lore (chest);</li>
 *   <li>buttons sized to the longest label and laid side by side; the exit button full width, last;</li>
 *   <li>button handlers run on the main thread, once, and only if the player is still online;</li>
 *   <li>"is the menu open" is tracked here. Closing a dialog with Esc sends nothing, so moving counts as
 *       closed, and anything left over times out.</li>
 * </ul>
 */
public final class MenuService implements Listener {
    /** First protocol version with dialogs (1.21.6). */
    private static final int DIALOG_PROTOCOL = 771;
    private static final long STATE_TIMEOUT_MS = Duration.ofMinutes(5).toMillis();
    private static final long PROMPT_TIMEOUT_MS = Duration.ofSeconds(60).toMillis();
    private static final ClickCallback.Options CALLBACK = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(30)).build();

    /** Rough dialog width budget for one row of buttons, in pixels. */
    private static final int ROW_BUDGET = 460;
    private static final int EXIT_WIDTH = 200;

    private enum Kind { DIALOG, CHEST }

    private record State(Kind kind, long since) {
    }

    private record Prompt(MenuScreen.TextField field, Supplier<MenuScreen> factory, long expires) {
    }

    private final WalkOrbit plugin;
    private final NamespacedKey dialogOffKey;
    private final Map<UUID, State> open = new HashMap<>();
    private final Map<UUID, Prompt> prompts = new HashMap<>();

    public MenuService(WalkOrbit plugin) {
        this.plugin = plugin;
        this.dialogOffKey = new NamespacedKey(plugin, "dialog_off");
        Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 100L, 100L);
    }

    // ------------------------------------------------------------------ public API

    /** Shows the screen built by {@code factory}, as a dialog if possible, else as a chest. */
    public void open(Player p, Supplier<MenuScreen> factory) {
        MenuScreen screen = factory.get();
        if (canShowDialog(p) && openDialog(p, screen)) return;
        openChest(p, factory, 0);
    }

    /** Whether this player has one of these menus open (dialog or chest), as far as the server can tell. */
    public boolean isInMenu(Player p) {
        return open.containsKey(p.getUniqueId());
    }

    public boolean isDialogEnabled(Player p) {
        return !p.getPersistentDataContainer().has(dialogOffKey, PersistentDataType.BYTE);
    }

    public void setDialogEnabled(Player p, boolean enabled) {
        if (enabled) p.getPersistentDataContainer().remove(dialogOffKey);
        else p.getPersistentDataContainer().set(dialogOffKey, PersistentDataType.BYTE, (byte) 1);
    }

    /** Dialogs need a 1.21.6+ client and the player not having turned them off. */
    public boolean canShowDialog(Player p) {
        if (!isDialogEnabled(p)) return false;
        int protocol = clientProtocol(p);
        return protocol < 0 || protocol >= DIALOG_PROTOCOL;
    }

    // ------------------------------------------------------------------ dialog

    private boolean openDialog(Player p, MenuScreen s) {
        try {
            List<DialogBody> body = new ArrayList<>();
            for (Component line : s.header()) body.add(DialogBody.plainMessage(line));
            for (MenuScreen.Item item : s.items()) {
                Component line = item.prefix() == null ? item.line()
                        : item.prefix().append(Component.text(" ")).append(item.line());
                body.add(DialogBody.item(item.icon()).description(DialogBody.plainMessage(line))
                        .showTooltip(false).build());
            }

            List<DialogInput> inputs = new ArrayList<>();
            for (MenuScreen.Input in : s.inputs()) inputs.add(dialogInput(in));

            int width = buttonWidth(s.buttons());
            int columns = Math.max(1, Math.min(Math.max(1, s.buttons().size()), ROW_BUDGET / (width + 4)));
            List<ActionButton> buttons = new ArrayList<>();
            for (MenuScreen.Button b : s.buttons()) buttons.add(dialogButton(p, s, b, width));
            ActionButton exit = dialogButton(p, s, s.exit(), EXIT_WIDTH);

            DialogBase base = DialogBase.builder(s.title()).canCloseWithEscape(true).body(body).inputs(inputs).build();
            DialogType type = DialogType.multiAction(buttons).columns(columns).exitAction(exit).build();
            p.closeInventory();
            p.showDialog(Dialog.create(b -> b.empty().base(base).type(type)));
            open.put(p.getUniqueId(), new State(Kind.DIALOG, System.currentTimeMillis()));
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not show a dialog to " + p.getName() + ", using the chest menu: " + t);
            return false;
        }
    }

    private DialogInput dialogInput(MenuScreen.Input in) {
        return switch (in) {
            case MenuScreen.TextField f -> DialogInput.text(f.key(), f.label())
                    .initial(orEmpty(f.get().get())).maxLength(f.maxLength()).width(300).build();
            case MenuScreen.Slider sl -> DialogInput.numberRange(sl.key(), sl.label(), sl.min(), sl.max())
                    .step(sl.step()).initial(sl.get().get()).width(300).build();
            case MenuScreen.Choice c -> {
                List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
                String current = c.get().get();
                for (MenuScreen.Choice.Option o : c.options()) {
                    entries.add(SingleOptionDialogInput.OptionEntry.create(o.id(), o.label(), o.id().equals(current)));
                }
                yield DialogInput.singleOption(c.key(), c.label(), entries).width(300).build();
            }
        };
    }

    private ActionButton dialogButton(Player owner, MenuScreen s, MenuScreen.Button b, int width) {
        Component label = b.prefix() == null ? b.label() : b.prefix().append(Component.text(" ")).append(b.label());
        Component tip = b.tooltip() == null || b.tooltip().isEmpty() ? null
                : Component.join(JoinConfiguration.newlines(), b.tooltip());
        UUID id = owner.getUniqueId();
        DialogAction action = DialogAction.customClick((view, audience) -> runSync(() -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null) return;                       // left meanwhile: do nothing
            open.remove(id);                              // the dialog closes after a click
            readInputs(s, view);
            b.onClick().accept(p);
        }), CALLBACK);
        return ActionButton.builder(label).tooltip(tip).width(width).action(action).build();
    }

    private static void readInputs(MenuScreen s, DialogResponseView view) {
        for (MenuScreen.Input in : s.inputs()) {
            switch (in) {
                case MenuScreen.TextField f -> {
                    String v = view.getText(f.key());
                    if (v != null) f.set().accept(v);
                }
                case MenuScreen.Slider sl -> {
                    Float v = view.getFloat(sl.key());
                    if (v != null) sl.set().accept(v);
                }
                case MenuScreen.Choice c -> {
                    String v = view.getText(c.key());
                    if (v != null) c.set().accept(v);
                }
            }
        }
    }

    /** Width that fits the longest label (estimated from the default font). */
    private static int buttonWidth(List<MenuScreen.Button> buttons) {
        int max = 0;
        for (MenuScreen.Button b : buttons) {
            int w = textWidth(b.label()) + (b.prefix() == null ? 0 : 12);
            max = Math.max(max, w);
        }
        return Math.max(60, Math.min(300, max + 20));
    }

    private static int textWidth(Component c) {
        String s = PlainTextComponentSerializer.plainText().serialize(c);
        return s.codePoints().map(cp -> cp < 0x80 ? 6 : 10).sum();
    }

    // ------------------------------------------------------------------ chest

    /** Chest version of a screen. */
    private static final class Chest implements InventoryHolder {
        final Supplier<MenuScreen> factory;
        final int page;
        final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
        Inventory inventory;

        Chest(Supplier<MenuScreen> factory, int page) {
            this.factory = factory;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private void openChest(Player p, Supplier<MenuScreen> factory, int page) {
        MenuScreen s = factory.get();
        Chest chest = new Chest(factory, page);
        Inventory inv = Bukkit.createInventory(chest, 54, s.title());
        chest.inventory = inv;

        // Row 1: what the screen is about, and the display items
        inv.setItem(0, item(new ItemStack(Material.BOOK), s.title(), s.header()));
        for (int i = 0; i < s.items().size() && i < 7; i++) {
            MenuScreen.Item it = s.items().get(i);
            inv.setItem(2 + i, item(it.icon(), it.line(), List.of()));
        }

        // Row 2: inputs
        int slot = 9;
        for (MenuScreen.Input in : s.inputs()) {
            if (slot > 17) break;
            int at = slot++;
            inv.setItem(at, inputItem(p, in));
            chest.actions.put(at, click -> onInputClick(p, factory, page, in, click));
        }

        // Rows 3-5 (or 2-5 without inputs): buttons, paged
        int first = s.inputs().isEmpty() ? 9 : 18;
        int perPage = 45 - first;
        int pages = Math.max(1, (s.buttons().size() + perPage - 1) / perPage);
        int pg = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < perPage; i++) {
            int index = pg * perPage + i;
            if (index >= s.buttons().size()) break;
            MenuScreen.Button b = s.buttons().get(index);
            int at = first + i;
            inv.setItem(at, item(b.icon(), b.label(), b.tooltip()));
            chest.actions.put(at, click -> runButton(p, b));
        }

        // Bottom row: paging, then the exit button last
        LocaleManager lm = plugin.getLocaleManager();
        if (pg > 0) {
            inv.setItem(45, item(new ItemStack(Material.ARROW), legacy(lm.getString("menu.prev", p)), List.of()));
            chest.actions.put(45, click -> openChest(p, factory, pg - 1));
        }
        if (pages > 1) {
            inv.setItem(49, item(new ItemStack(Material.PAPER), legacy(lm.getString("menu.page", p,
                    Map.of("page", String.valueOf(pg + 1), "total", String.valueOf(pages)))), List.of()));
        }
        if (pg < pages - 1) {
            inv.setItem(51, item(new ItemStack(Material.ARROW), legacy(lm.getString("menu.next", p)), List.of()));
            chest.actions.put(51, click -> openChest(p, factory, pg + 1));
        }
        inv.setItem(53, item(s.exit().icon(), s.exit().label(), s.exit().tooltip()));
        chest.actions.put(53, click -> runButton(p, s.exit()));

        p.closeDialog();
        p.openInventory(inv);
        open.put(p.getUniqueId(), new State(Kind.CHEST, System.currentTimeMillis()));
    }

    private void runButton(Player p, MenuScreen.Button b) {
        p.closeInventory();
        b.onClick().accept(p);
    }

    private ItemStack inputItem(Player p, MenuScreen.Input in) {
        LocaleManager lm = plugin.getLocaleManager();
        List<Component> lore = new ArrayList<>();
        Component value = switch (in) {
            case MenuScreen.TextField f -> blank(f.get().get())
                    ? legacy(lm.getString("menu.empty", p)) : Component.text(f.get().get());
            case MenuScreen.Slider sl -> Component.text(format(sl.get().get(), sl.step()));
            case MenuScreen.Choice c -> c.options().stream().filter(o -> o.id().equals(c.get().get()))
                    .map(MenuScreen.Choice.Option::label).findFirst().orElse(Component.text("-"));
        };
        lore.add(legacy(lm.getString("menu.value", p)).append(value));
        switch (in) {
            case MenuScreen.TextField f -> lore.add(legacy(lm.getString("menu.text_hint", p)));
            case MenuScreen.Slider sl -> {
                lore.add(legacy(lm.getString("menu.slider_hint", p, Map.of("step", format(sl.step(), sl.step())))));
                lore.add(legacy(lm.getString("menu.slider_hint_shift", p, Map.of("big", format(sl.bigStep(), sl.step())))));
            }
            case MenuScreen.Choice c -> lore.add(legacy(lm.getString("menu.choice_hint", p)));
        }
        return item(in.icon(), in.label(), lore);
    }

    private void onInputClick(Player p, Supplier<MenuScreen> factory, int page, MenuScreen.Input in, ClickType click) {
        switch (in) {
            case MenuScreen.TextField f -> {
                p.closeInventory();
                synchronized (prompts) {
                    prompts.put(p.getUniqueId(), new Prompt(f, factory, System.currentTimeMillis() + PROMPT_TIMEOUT_MS));
                }
                p.sendMessage(plugin.getLocaleManager().getString("menu.prompt", p,
                        Map.of("label", PlainTextComponentSerializer.plainText().serialize(f.label()))));
                return;
            }
            case MenuScreen.Slider sl -> {
                float delta = click.isShiftClick() ? sl.bigStep() : sl.step();
                if (click.isRightClick()) delta = -delta;
                float v = sl.get().get() + delta;
                v = Math.round(v / sl.step()) * sl.step();
                sl.set().accept(Math.max(sl.min(), Math.min(sl.max(), v)));
            }
            case MenuScreen.Choice c -> {
                List<MenuScreen.Choice.Option> opts = c.options();
                int i = 0;
                for (int k = 0; k < opts.size(); k++) if (opts.get(k).id().equals(c.get().get())) i = k;
                i = Math.floorMod(i + (click.isRightClick() ? -1 : 1), opts.size());
                c.set().accept(opts.get(i).id());
            }
        }
        openChest(p, factory, page); // show the new value right away
    }

    // ------------------------------------------------------------------ events

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Chest chest)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        Consumer<ClickType> action = chest.actions.get(e.getRawSlot());
        if (action != null) action.accept(e.getClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Chest) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Chest) {
            State st = open.get(e.getPlayer().getUniqueId());
            if (st != null && st.kind() == Kind.CHEST) open.remove(e.getPlayer().getUniqueId());
        }
    }

    /** Esc on a dialog sends nothing to the server: moving means the dialog is gone. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        State st = open.get(e.getPlayer().getUniqueId());
        if (st != null && st.kind() == Kind.DIALOG && e.hasChangedPosition()) open.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Prompt prompt;
        synchronized (prompts) {
            prompt = prompts.get(id);
            if (prompt == null || prompt.expires() < System.currentTimeMillis()) return;
            prompts.remove(id);
        }
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        runSync(() -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null) return;
            if (text.equalsIgnoreCase("cancel")) {
                p.sendMessage(plugin.getLocaleManager().getString("menu.prompt_cancel", p));
            } else {
                MenuScreen.TextField f = prompt.field();
                f.set().accept(text.length() > f.maxLength() ? text.substring(0, f.maxLength()) : text);
            }
            openChest(p, prompt.factory(), 0);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        open.remove(e.getPlayer().getUniqueId());
        synchronized (prompts) {
            prompts.remove(e.getPlayer().getUniqueId());
        }
    }

    /** Anything not closed properly goes back to normal after a while. */
    private void expire() {
        long now = System.currentTimeMillis();
        open.values().removeIf(st -> now - st.since() > STATE_TIMEOUT_MS);
        synchronized (prompts) {
            prompts.entrySet().removeIf(en -> {
                if (en.getValue().expires() >= now) return false;
                Player p = Bukkit.getPlayer(en.getKey());
                if (p != null) p.sendMessage(plugin.getLocaleManager().getString("menu.prompt_timeout", p));
                return true;
            });
        }
    }

    // ------------------------------------------------------------------ helpers

    private void runSync(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }

    /** Client protocol via ViaVersion when it's installed; -1 (assume same as server) otherwise. */
    private static int clientProtocol(Player p) {
        try {
            Class<?> via = Class.forName("com.viaversion.viaversion.api.Via");
            Object api = via.getMethod("getAPI").invoke(null);
            Class<?> apiType = Class.forName("com.viaversion.viaversion.api.ViaAPI");
            return (int) apiType.getMethod("getPlayerVersion", UUID.class).invoke(api, p.getUniqueId());
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            return -1;
        }
    }

    private static ItemStack item(ItemStack base, Component name, List<Component> lore) {
        ItemStack item = base == null ? new ItemStack(Material.PAPER) : base.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore.stream().map(l -> l.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)).toList());
            }
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Component legacy(String sectionText) {
        return LegacyComponentSerializer.legacySection().deserialize(sectionText);
    }

    private static String format(float v, float step) {
        return step >= 1 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
