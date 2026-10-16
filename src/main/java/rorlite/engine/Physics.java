package rorlite.engine;

/**
 * Простейший AABB-физический движок поверх тайловой карты.
 * Поддержка: гравитация, acc/force/maxSpeed (как в RoR2), bounce, трение о землю.
 */
public final class Physics {

    public static final double GRAVITY = 46.0;      // ед/с^2
    public static final double GROUND_FRICTION = 14.0;
    public static final double AIR_DRAG = 0.35;

    private Physics() {}

    /** Результат движения тела за шаг. */
    public static class MoveResult {
        public boolean onGround;
        public boolean hitWall;
        public boolean landed;       // только что приземлился
        public double landImpact;    // вертикальная скорость при приземлении
    }

    /**
     * Двигает тело (центр x,y; половины hw,hh) за dt секунд.
     * vel - ускоряется через applyForce/applyAccel до maxSpeed по горизонтали.
     */
    public static void move(World w, Body b, double dt, MoveResult res) {
        b.pos.y -= b.vel.y * dt; // положительный vel.y = вверх, а экранная Y растёт вниз
        b.pos.x += b.vel.x * dt;

        // --- коллизии по X ---
        if (b.vel.x != 0) {
            double dir = Math.signum(b.vel.x);
            double edgeX = b.pos.x + dir * b.hw;
            int tx = World.tileIndex(edgeX);
            for (int ty = World.tileIndex(b.pos.y - b.hh + 0.01); ty <= World.tileIndex(b.pos.y + b.hh - 0.01); ty++) {
                if (w.solid(tx, ty)) {
                    double nx = dir > 0 ? tx * World.TILE : (tx + 1) * World.TILE;
                    b.pos.x = nx - dir * (b.hw + 0.001);
                    if (b.bounce > 0.05) {
                        b.vel.x = -b.vel.x * b.bounce;
                    } else {
                        b.vel.x = 0;
                    }
                    res.hitWall = true;
                    break;
                }
            }
        }

        // --- коллизии по Y ---
        b.onGroundPrev = res.onGround;
        res.onGround = false;
        if (b.vel.y <= 0) { // падаем (vel.y отрицательный = вниз)
            int ty = World.tileIndex(b.pos.y + b.hh);
            for (int tx = World.tileIndex(b.pos.x - b.hw + 0.01); tx <= World.tileIndex(b.pos.x + b.hw - 0.01); tx++) {
                if (w.solid(tx, ty)) {
                    double topY = ty * World.TILE;
                    b.pos.y = topY - b.hh - 0.001;
                    res.landImpact = -b.vel.y;
                    if (b.bounce > 0.05 && -b.vel.y > 4.0) {
                        b.vel.y = -b.vel.y * b.bounce;
                    } else {
                        b.vel.y = 0;
                        res.onGround = true;
                        res.landed = !b.onGroundPrev;
                    }
                    break;
                }
            }
        } else { // летим вверх — проверяем потолок
            int ty = World.tileIndex(b.pos.y - b.hh);
            for (int tx = World.tileIndex(b.pos.x - b.hw + 0.01); tx <= World.tileIndex(b.pos.x + b.hw - 0.01); tx++) {
                if (w.solid(tx, ty)) {
                    double botY = (ty + 1) * World.TILE;
                    b.pos.y = botY + b.hh + 0.001;
                    if (b.bounce > 0.05) b.vel.y = -b.vel.y * b.bounce;
                    else b.vel.y = 0;
                    break;
                }
            }
        }

        // --- ограничение скорости / трение ---
        if (res.onGround || b.kinematicGroundFriction) {
            // трение о землю гасит горизонтальную скорость
            double f = b.dragOnGround * dt;
            if (Math.abs(b.vel.x) <= f) b.vel.x = 0; else b.vel.x -= Math.signum(b.vel.x) * f;
        }
        if (b.maxSpeed > 0 && !b.ignoreMaxSpeedWhileAirborne) {
            // RoR2: maxSpeed ограничивает только горизонтальную составляющую
            b.vel.x = Math.max(-b.maxSpeed, Math.min(b.maxSpeed, b.vel.x));
        }
        // терминальная скорость падения
        if (b.vel.y < -60) b.vel.y = -60;
    }

    /** Тело, на которое действует физика. */
    public static class Body {
        public Vec2 pos = new Vec2();   // центр
        public Vec2 vel = new Vec2();
        public double hw = 0.5, hh = 0.9;
        public double mass = 1.0;
        public double bounce = 0.0;
        public double dragOnGround = GROUND_FRICTION;
        public double maxSpeed = 0;     // 0 = без ограничения
        public boolean onGroundPrev;
        public boolean kinematicGroundFriction = false;
        public boolean ignoreMaxSpeedWhileAirborne = false;
    }

    /** Применить ускорение (ед/с^2) — как accel в RoR2. */
    public static void applyAccel(Body b, double ax, double ay, double dt) {
        b.vel.x += ax * dt;
        b.vel.y += ay * dt;
    }

    /** Применить импульс силы (Н*с) с учётом массы. */
    public static void applyImpulse(Body b, double ix, double iy) {
        b.vel.x += ix / b.mass;
        b.vel.y += iy / b.mass;
    }

    public static void applyGravity(Body b, double dt) {
        b.vel.y -= GRAVITY * dt;
    }
}
