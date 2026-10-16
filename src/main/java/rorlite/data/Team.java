package rorlite.data;

/**
 * Отряд (team) — как в RoR2: враждебные фракции не дерутся между собой,
 * а «нейтралы» (сундуки, лавки) не атакуются случайно.
 */
public enum Team {
    NONE(0),            // объекты окружения
    PLAYER(1),          // игрок и его союзники-питомцы
    ENEMY(2);           // волны врагов

    public final int id;
    Team(int id) { this.id = id; }

    public boolean hostileTo(Team other) {
        if (this == NONE || other == NONE) return false;
        return this != other;
    }
}
