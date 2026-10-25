import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * A basic ray tracer: casts one (or a few, for anti-aliasing) rays per pixel,
 * shades hits with ambient + diffuse + specular (Blinn-Phong) lighting,
 * casts shadow rays, and optionally follows a limited number of mirror
 * reflection bounces. Renders a scene with two spheres sitting on a
 * checkered plane and saves the result as a PNG.
 */
public class RayTracer {

    // ---- Image / render settings ----
    static final int IMAGE_WIDTH = 800;
    static final int IMAGE_HEIGHT = 600;
    static final int SAMPLES_PER_PIXEL = 4; // simple anti-aliasing; set to 1 for a faster, blockier render
    static final int MAX_REFLECTION_DEPTH = 3;
    static final double AMBIENT_LIGHT = 1.0; // scales each material's own ambient coefficient
    static final Vec3 SHADOW_BIAS_NORMAL = null; // unused placeholder (bias handled inline)

    public static void main(String[] args) throws IOException {
        Scene scene = buildScene();
        Camera camera = new Camera(
                new Vec3(0, 2.5, -7),   // camera position
                new Vec3(0, 0.8, 0),    // look-at point
                new Vec3(0, 1, 0),      // world up
                50.0,                    // vertical field of view (degrees)
                (double) IMAGE_WIDTH / IMAGE_HEIGHT
        );

        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Random rng = new Random(42);

        for (int py = 0; py < IMAGE_HEIGHT; py++) {
            for (int px = 0; px < IMAGE_WIDTH; px++) {
                Vec3 colorSum = Vec3.ZERO;
                for (int s = 0; s < SAMPLES_PER_PIXEL; s++) {
                    double jitterX = SAMPLES_PER_PIXEL == 1 ? 0.5 : rng.nextDouble();
                    double jitterY = SAMPLES_PER_PIXEL == 1 ? 0.5 : rng.nextDouble();
                    double u = (px + jitterX) / IMAGE_WIDTH;
                    double v = (py + jitterY) / IMAGE_HEIGHT;
                    Ray ray = camera.getRay(u, v);
                    colorSum = colorSum.add(trace(ray, scene, 0));
                }
                Vec3 color = colorSum.scale(1.0 / SAMPLES_PER_PIXEL).clamp01();
                image.setRGB(px, py, toRGB(color));
            }
            if (py % 50 == 0) {
                System.out.println("Rendered row " + py + " / " + IMAGE_HEIGHT);
            }
        }

        File outFile = new File("output.png");
        ImageIO.write(image, "png", outFile);
        System.out.println("Saved " + outFile.getAbsolutePath());
    }

    /** Builds a scene with two spheres resting on an infinite checkered plane. */
    static Scene buildScene() {
        Scene scene = new Scene();

        Material checkerFloor = Material.checker(
                new Vec3(0.9, 0.9, 0.9), new Vec3(0.15, 0.15, 0.18), 1.0,
                0.3, 0.7, 0.1, 16, 0.05);
        scene.add(new Plane(new Vec3(0, 0, 0), new Vec3(0, 1, 0), checkerFloor));

        Material redSphereMat = Material.solid(
                new Vec3(0.85, 0.15, 0.15),
                0.15, 0.8, 0.6, 64, 0.15);
        scene.add(new Sphere(new Vec3(-1.1, 1.0, 0.5), 1.0, redSphereMat));

        Material blueSphereMat = Material.solid(
                new Vec3(0.15, 0.35, 0.9),
                0.15, 0.8, 0.9, 128, 0.35);
        scene.add(new Sphere(new Vec3(1.3, 0.7, -0.4), 0.7, blueSphereMat));

        scene.add(new Light(new Vec3(-4, 6, -3), new Vec3(0.9, 0.9, 0.85)));
        scene.add(new Light(new Vec3(5, 4, -5), new Vec3(0.35, 0.35, 0.4)));

        return scene;
    }

    /** Traces a ray through the scene, returning the shaded color it sees. */
    static Vec3 trace(Ray ray, Scene scene, int depth) {
        Hittable.Hit hit = scene.closestHit(ray, 0.001, Double.POSITIVE_INFINITY);
        if (hit == null) {
            return scene.backgroundColor(ray);
        }

        Material mat = hit.material;
        Vec3 baseColor = mat.colorAt(hit.point);
        Vec3 color = baseColor.scale(mat.ambient * AMBIENT_LIGHT);

        for (Light light : scene.lights) {
            Vec3 toLight = light.position.sub(hit.point);
            double distToLight = toLight.length();
            Vec3 lightDir = toLight.normalize();

            // Shadow check: is something blocking the path to this light?
            Ray shadowRay = new Ray(hit.point.add(hit.normal.scale(1e-4)), lightDir);
            Hittable.Hit shadowHit = scene.closestHit(shadowRay, 0.001, distToLight - 1e-3);
            if (shadowHit != null) {
                continue; // in shadow: this light contributes nothing here
            }

            // Diffuse (Lambertian) term.
            double diff = Math.max(0, hit.normal.dot(lightDir));
            color = color.add(baseColor.mul(light.intensity).scale(diff * mat.diffuse));

            // Specular (Blinn-Phong) term.
            Vec3 viewDir = ray.direction.negate().normalize();
            Vec3 halfDir = lightDir.add(viewDir).normalize();
            double specAngle = Math.max(0, hit.normal.dot(halfDir));
            double spec = Math.pow(specAngle, mat.shininess);
            color = color.add(light.intensity.scale(spec * mat.specular));
        }

        // Mirror reflection, up to a limited recursion depth.
        if (mat.reflectivity > 0 && depth < MAX_REFLECTION_DEPTH) {
            Vec3 reflectDir = ray.direction.reflect(hit.normal).normalize();
            Ray reflectRay = new Ray(hit.point.add(hit.normal.scale(1e-4)), reflectDir);
            Vec3 reflectColor = trace(reflectRay, scene, depth + 1);
            color = color.scale(1 - mat.reflectivity).add(reflectColor.scale(mat.reflectivity));
        }

        return color;
    }

    static int toRGB(Vec3 color) {
        // Simple gamma correction (gamma = 2.0) for a more natural-looking image.
        int r = (int) Math.round(Math.sqrt(color.x) * 255);
        int g = (int) Math.round(Math.sqrt(color.y) * 255);
        int b = (int) Math.round(Math.sqrt(color.z) * 255);
        r = Math.min(255, Math.max(0, r));
        g = Math.min(255, Math.max(0, g));
        b = Math.min(255, Math.max(0, b));
        return (r << 16) | (g << 8) | b;
    }
}
