/**
 * A simple 3-component vector, used both for 3D points/directions and
 * for RGB colors (x=r, y=g, z=b).
 */
public class Vec3 {
    public final double x, y, z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 add(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public Vec3 sub(Vec3 o) {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    public Vec3 scale(double s) {
        return new Vec3(x * s, y * s, z * s);
    }

    /** Component-wise multiply, used for combining colors. */
    public Vec3 mul(Vec3 o) {
        return new Vec3(x * o.x, y * o.y, z * o.z);
    }

    public double dot(Vec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vec3 cross(Vec3 o) {
        return new Vec3(
                y * o.z - z * o.y,
                z * o.x - x * o.z,
                x * o.y - y * o.x
        );
    }

    public double length() {
        return Math.sqrt(dot(this));
    }

    public Vec3 normalize() {
        double len = length();
        if (len == 0) return this;
        return scale(1.0 / len);
    }

    public Vec3 negate() {
        return new Vec3(-x, -y, -z);
    }

    /** Reflects this vector (assumed to be an incoming direction) around a normal. */
    public Vec3 reflect(Vec3 normal) {
        return this.sub(normal.scale(2 * this.dot(normal)));
    }

    /** Clamp each component to [0,1], useful right before writing out a color. */
    public Vec3 clamp01() {
        return new Vec3(
                Math.min(1, Math.max(0, x)),
                Math.min(1, Math.max(0, y)),
                Math.min(1, Math.max(0, z))
        );
    }
}
