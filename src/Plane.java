/** An infinite flat plane defined by a point on it and a unit normal. */
public class Plane implements Hittable {
    public final Vec3 point;
    public final Vec3 normal;
    public final Material material;

    public Plane(Vec3 point, Vec3 normal, Material material) {
        this.point = point;
        this.normal = normal.normalize();
        this.material = material;
    }

    @Override
    public Hit intersect(Ray ray, double tMin, double tMax) {
        double denom = normal.dot(ray.direction);
        if (Math.abs(denom) < 1e-9) return null; // ray parallel to plane

        double t = point.sub(ray.origin).dot(normal) / denom;
        if (t < tMin || t > tMax) return null;

        Vec3 hitPoint = ray.pointAt(t);
        // Always return the normal facing the ray origin, so shading works
        // whichever side the camera views the plane from.
        Vec3 n = denom < 0 ? normal : normal.negate();
        return new Hit(t, hitPoint, n, material);
    }
}
