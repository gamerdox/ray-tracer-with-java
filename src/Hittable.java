/** Anything a ray can intersect: spheres, planes, etc. */
public interface Hittable {

    /**
     * Tests for an intersection with the given ray in the parametric range
     * (tMin, tMax). Returns null if there is no hit in that range.
     */
    Hit intersect(Ray ray, double tMin, double tMax);

    /** Result of a successful ray/object intersection. */
    class Hit {
        public final double t;
        public final Vec3 point;
        public final Vec3 normal; // unit length, points away from the surface
        public final Material material;

        public Hit(double t, Vec3 point, Vec3 normal, Material material) {
            this.t = t;
            this.point = point;
            this.normal = normal;
            this.material = material;
        }
    }
}
