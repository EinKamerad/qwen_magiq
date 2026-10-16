package rorlite.engine;

/** Простой 2D-вектор (мутабельный, для производительности). */
public class Vec2 {
    public double x, y;

    public Vec2() {}
    public Vec2(double x, double y) { this.x = x; this.y = y; }

    public Vec2 copy() { return new Vec2(x, y); }
    public Vec2 set(double x, double y) { this.x = x; this.y = y; return this; }
    public Vec2 set(Vec2 v) { return set(v.x, v.y); }

    public Vec2 add(Vec2 o) { x += o.x; y += o.y; return this; }
    public Vec2 sub(Vec2 o) { x -= o.x; y -= o.y; return this; }
    public Vec2 scl(double s) { x *= s; y *= s; return this; }

    public double len() { return Math.hypot(x, y); }
    public double len2() { return x * x + y * y; }

    public double dist(Vec2 o) { return Math.hypot(x - o.x, y - o.y); }
    public double dist2(Vec2 o) { double dx = x - o.x, dy = y - o.y; return dx * dx + dy * dy; }

    public Vec2 norm() {
        double l = len();
        if (l > 1e-9) { x /= l; y /= l; }
        return this;
    }

    public static Vec2 fromAngle(double a) { return new Vec2(Math.cos(a), Math.sin(a)); }

    /** Отражение от нормализованной нормали с потерей энергии (bounce). */
    public void reflect(double nx, double ny, double bounce) {
        double dot = x * nx + y * ny;
        if (dot < 0) {
            x -= (1 + bounce) * dot * nx;
            y -= (1 + bounce) * dot * ny;
        }
    }
}
