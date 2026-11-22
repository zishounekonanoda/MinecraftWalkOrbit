package org.neko.walkorbit;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Identifies this plugin's menus and remembers which page is open. */
public final class MenuHolder implements InventoryHolder {
    public enum Type { MAIN, LANGUAGE, STYLE }

    private final Type type;
    private final int page;
    private Inventory inventory;

    public MenuHolder(Type type, int page) {
        this.type = type;
        this.page = page;
    }

    public Type type() {
        return type;
    }

    public int page() {
        return page;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
