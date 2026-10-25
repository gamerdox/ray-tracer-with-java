import java.util.ArrayList;
import java.util.List;

/** Holds everything the renderer needs: objects, lights and a background. */
public class Scene {
    public final List<Hittable> objects = new ArrayList<>();
    public final List<Light> lights = new ArrayList<>();

    public void add(Hittable object) {
        objects.add(object);
    }

    public void add(Light light) {
        lights.add(light);
    }

    /** Finds the closest intersection along the ray within (tMin, tMax), or null. */
    public Hittable.Hit closestHit(Ray ray, double tMin, double tMax) {
        Hittable.Hit closest = null;
        double closestT = tMax;
        for (Hittable object : objects) {
            Hittable.Hit hit = object.intersect(ray, tMin, closestT);
            if (hit != null) {
                closest = hit;
                closestT = hit.t;
            }
        }
        return closest;
    }

    /** Simple sky gradient used when a ray hits nothing: pale blue at the top, white near the horizon. */
    public Vec3 backgroundColor(Ray ray) {
        Vec3 unitDir = ray.direction.normalize();
        double t = 0.5 * (unitDir.y + 1.0);
        Vec3 white = new Vec3(1.0, 1.0, 1.0);
        Vec3 skyBlue = new Vec3(0.5, 0.7, 1.0);
        return white.scale(1.0 - t).add(skyBlue.scale(t));
    }
}
