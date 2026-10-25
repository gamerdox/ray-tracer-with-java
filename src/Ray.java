/** A ray defined by an origin point and a (normalized) direction. */
public class Ray {
    public final Vec3 origin;
    public final Vec3 direction;

    public Ray(Vec3 origin, Vec3 direction) {
        this.origin = origin;
        this.direction = direction;
    }

    public Vec3 pointAt(double t) {
        return origin.add(direction.scale(t));
    }
}
