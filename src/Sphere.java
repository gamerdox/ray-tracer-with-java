public class Sphere implements Hittable {
    public final Vec3 center;
    public final double radius;
    public final Material material;

    public Sphere(Vec3 center, double radius, Material material) {
        this.center = center;
        this.radius = radius;
        this.material = material;
    }

    @Override
    public Hit intersect(Ray ray, double tMin, double tMax) {
        Vec3 oc = ray.origin.sub(center);
        double a = ray.direction.dot(ray.direction);
        double b = 2.0 * oc.dot(ray.direction);
        double c = oc.dot(oc) - radius * radius;
        double discriminant = b * b - 4 * a * c;
        if (discriminant < 0) return null;

        double sqrtD = Math.sqrt(discriminant);
        double t = (-b - sqrtD) / (2.0 * a);
        if (t < tMin || t > tMax) {
            t = (-b + sqrtD) / (2.0 * a);
            if (t < tMin || t > tMax) return null;
        }

        Vec3 point = ray.pointAt(t);
        Vec3 normal = point.sub(center).normalize();
        return new Hit(t, point, normal, material);
    }
}
