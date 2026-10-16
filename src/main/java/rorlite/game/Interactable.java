package rorlite.game;

import rorlite.data.Item;
import rorlite.data.Items;
import rorlite.data.Team;
import rorlite.engine.Rng;
import rorlite.engine.Vec2;
import rorlite.engine.World;

/** Интерактивные объекты мира: сундуки, лавки, кусты, портал. */
public class Interactable {
    public enum Kind { CHEST, SHOP_1, SHOP_2, BOSS_CHALLENGE, BUSH, DOLL }

    public final Kind kind;
    public Vec2 pos;
    public double radius = 1.6;
    public boolean used = false;
    public double openTime = 0;     // прогресс открытия (нужно стоять рядом)
    public Item shopItem;           // для лавок — товар
    public double price;            // цена в лунных монетах
    public Enemy bossOwner;         // для вызова босса

    public Interactable(Kind kind, Vec2 pos) {
        this.kind = kind;
        this.pos = pos.copy();
        switch (kind) {
            case CHEST -> radius = 1.4;
            case SHOP_1 -> { radius = 1.5; rollShop(Item.Tier.TIER1); }
            case SHOP_2 -> { radius = 1.5; rollShop(Item.Tier.TIER2); }
            case BUSH -> radius = 1.0;
            case DOLL -> radius = 0.8;
            default -> radius = 2.0;
        }
    }

    private void rollShop(Item.Tier forcedTier) {
        // В лавке продаётся предмет заданного тира за цену от стоимости стаков
        Item it = Items.randomOfTier(forcedTier);
        int stacks = forcedTier == Item.Tier.TIER1 ? Rng.i(1, 3) : Rng.i(1, 2);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stacks; i++) if (i > 0) sb.append("+");
        shopItem = it;
        price = 15 * stacks * (int) Item.tierValue(forcedTier) / (forcedTier == Item.Tier.TIER1 ? 1 : 3);
        if (price < 10) price = 10 + stacks * 5;
    }

    /** Обновление: проверка близости игрока и активация по E. */
    public void update(float dt, Player player, Game game) {
        if (used) return;
        double d = pos.dist(player.pos());
        if (d < radius + 1.2 && game.keyInteractHeld()) {
            openTime += dt;
            double need = kind == Kind.CHEST ? 1.2 : 0.8;
            if (openTime >= need) {
                activate(game, player);
            }
        } else {
            openTime = Math.max(0, openTime - dt * 2);
        }
    }

    private void activate(Game game, Player player) {
        switch (kind) {
            case CHEST -> {
                used = true;
                Item drop = Items.rollDrop();
                player.addItem(drop, game);
                game.spawnChestBurst(pos);
            }
            case SHOP_1, SHOP_2 -> {
                if (player.money >= price && shopItem != null) {
                    player.money -= price;
                    player.addItem(shopItem, game);
                    used = true;
                    game.addLog("Куплено: " + shopItem.name + " за " + (int) price);
                } else {
                    game.addLog("Не хватает монет (" + (int) price + ")");
                }
            }
            case BOSS_CHALLENGE -> {
                if (!used) {
                    used = true;
                    game.spawnBossAt(pos);
                    game.addLog("Босс пробуждается!");
                }
            }
            case BUSH -> {
                used = true;
                if (Rng.chance(0.3)) {
                    player.addItem(Items.rollDrop(), game);
                } else {
                    player.money += Rng.i(3, 8);
                    game.addLog("В кусте найдены монеты!");
                }
                game.spawnChestBurst(pos);
            }
            case DOLL -> {
                used = true;
                Enemy e = game.spawnEnemyNear("munchkin", pos.x, pos.y - 1);
                if (e != null) e.makeElite();
                game.addLog("Медведь-охранитель ожил!");
            }
        }
    }
}
