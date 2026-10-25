import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class RayTracer2 {

    static final int MAX_DEPTH = 4; // Max reflection depth

    // Simple 3D Vector class
    static class Vector3 {
        double x, y, z;

        Vector3(double x, double y, double z) {
            this.x = x; this.y = y; this.z = z;
        }

        Vector3 add(Vector3 v) { return new Vector3(x + v.x, y + v.y, z + v.z); }
        Vector3 sub(Vector3 v) { return new Vector3(x - v.x, y - v.y, z - v.z); }
        Vector3 mult(double c) { return new Vector3(x * c, y * c, z * c); }
        double dot(Vector3 v) { return x * v.x + y * v.y + z * v.z; }
        Vector3 normalize() {
            double len = Math.sqrt(dot(this));
            return len > 0 ? new Vector3(x / len, y / len, z / len) : this;
        }
    }
    
    // Ray class
    static class Ray {
        Vector3 origin, direction;
        Ray(Vector3 origin, Vector3 direction) {
            this.origin = origin;
            this.direction = direction.normalize();
        }
    }

    // Base class for scene geometry
    abstract static class SceneObject {
        Color color;
        double reflectivity; // 0.0 (matte) to 1.0 (mirror)

        SceneObject(Color color, double reflectivity) {
            this.color = color;
            this.reflectivity = reflectivity;
        }

        abstract double intersect(Ray ray);
        abstract Vector3 getNormal(Vector3 hitPoint);
        abstract Color getColorAt(Vector3 hitPoint);
    }

    // Sphere Implementation
    static class Sphere extends SceneObject {
        Vector3 center;
        double radius;

        Sphere(Vector3 center, double radius, Color color, double reflectivity) {
            super(color, reflectivity);
            this.center = center;
            this.radius = radius;
        }

        @Override
        double intersect(Ray ray) {
            Vector3 oc = ray.origin.sub(center);
            double b = oc.dot(ray.direction);
            double c = oc.dot(oc) - radius * radius;
            double discriminant = b * b - c;
            if (discriminant < 0) return -1;
            double t = -b - Math.sqrt(discriminant);
            return t > 0.001 ? t : -b + Math.sqrt(discriminant);
        }

        @Override
        Vector3 getNormal(Vector3 hitPoint) {
            return hitPoint.sub(center).normalize();
        }

        @Override
        Color getColorAt(Vector3 hitPoint) {
            return this.color;
        }
    }

    // Plane Implementation (infinite flat surface)
    static class Plane extends SceneObject {
        Vector3 point, normal;

        Plane(Vector3 point, Vector3 normal, Color color, double reflectivity) {
            super(color, reflectivity);
            this.point = point;
            this.normal = normal.normalize();
        }

        @Override
        double intersect(Ray ray) {
            double denom = normal.dot(ray.direction);
            if (Math.abs(denom) > 1e-6) {
                double t = point.sub(ray.origin).dot(normal) / denom;
                if (t > 0.001) return t;
            }
            return -1;
        }

        @Override
        Vector3 getNormal(Vector3 hitPoint) {
            return normal;
        }

        @Override
        Color getColorAt(Vector3 hitPoint) {
            // Procedural checkerboard floor pattern
            int xCheck = (int) Math.floor(hitPoint.x + 1000) % 2;
            int zCheck = (int) Math.floor(hitPoint.z + 1000) % 2;
            if ((xCheck ^ zCheck) == 1) {
                return this.color;
            } else {
                return Color.LIGHT_GRAY;
            }
        }
    }

    // Holds intersection data
    static class HitRecord {
        double t = -1;
        SceneObject obj = null;
    }

    // Find the closest object a ray intersects with
    static HitRecord findClosestHit(Ray ray, List<SceneObject> objects) {
        HitRecord record = new HitRecord();
        double closestT = Double.MAX_VALUE;

        for (SceneObject obj : objects) {
            double t = obj.intersect(ray);
            if (t > 0.001 && t < closestT) {
                closestT = t;
                record.t = t;
                record.obj = obj;
            }
        }
        return record;
    }

    // Core tracing algorithm evaluating shadows, reflections, and diffuse shading
    static Vector3 trace(Ray ray, List<SceneObject> objects, Vector3 lightPos, int depth) {
        if (depth > MAX_DEPTH) return new Vector3(0, 0, 0); // Base case for reflection limit

        HitRecord hit = findClosestHit(ray, objects);
        if (hit.obj == null) {
            // Background gradient sky color
            double t = 0.5 * (ray.direction.y + 1.0);
            return new Vector3(1.0 - t, 1.0 - t, 1.0).mult(0.5); 
        }

        // Geometric variables
        Vector3 hitPoint = ray.origin.add(ray.direction.mult(hit.t));
        Vector3 normal = hit.obj.getNormal(hitPoint);
        Vector3 toLight = lightPos.sub(hitPoint);
        double lightDist = Math.sqrt(toLight.dot(toLight));
        toLight = toLight.normalize();

        // Shadow check
        Ray shadowRay = new Ray(hitPoint.add(normal.mult(0.001)), toLight); // Offset origin to prevent self-intersection
        HitRecord shadowHit = findClosestHit(shadowRay, objects);
        boolean inShadow = shadowHit.obj != null && shadowHit.t < lightDist;

        // Diffuse (Lambertian) Shading
        double diffuseIntensity = 0.2; // Ambient baseline
        if (!inShadow) {
            diffuseIntensity += Math.max(0.0, normal.dot(toLight)) * 0.8;
        }

        Color objColor = hit.obj.getColorAt(hitPoint);
        Vector3 baseColor = new Vector3(objColor.getRed() / 255.0, objColor.getGreen() / 255.0, objColor.getBlue() / 255.0);
        Vector3 localColor = baseColor.mult(diffuseIntensity);

        // Reflection handling
        if (hit.obj.reflectivity > 0) {
            double dot = ray.direction.dot(normal);
            Vector3 reflectDir = ray.direction.sub(normal.mult(2 * dot)).normalize();
            Ray reflectRay = new Ray(hitPoint.add(normal.mult(0.001)), reflectDir);
            
            Vector3 reflectedColor = trace(reflectRay, objects, lightPos, depth + 1);
            
            // Blend local diffuse color with incoming reflection
            localColor = localColor.mult(1.0 - hit.obj.reflectivity).add(reflectedColor.mult(hit.obj.reflectivity));
        }

        return localColor;
    }

    public static void main(String[] args) {
        int width = 1024;
        int height = 768;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Define Scene Objects
        List<SceneObject> objects = new ArrayList<>();
        objects.add(new Sphere(new Vector3(-2.0, 0.5, -6.0), 1.5, Color.RED, 0.4));     // Red reflective sphere
        objects.add(new Sphere(new Vector3(1.5, 0.0, -5.0), 1.0, Color.GREEN, 0.3));    // Green reflective sphere
        objects.add(new Sphere(new Vector3(0.0, -0.6, -3.5), 0.4, Color.BLUE, 0.6));    // Highly reflective blue sphere
        objects.add(new Plane(new Vector3(0, -1.0, 0), new Vector3(0, 1, 0), Color.DARK_GRAY, 0.2)); // Floor

        // Static Light Position
        Vector3 lightPos = new Vector3(5.0, 8.0, 0.0);
        double aspect = (double) width / height;

        // Render loop
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double u = (2.0 * (x + 0.5) / width - 1.0) * aspect;
                double v = (1.0 - 2.0 * (y + 0.5) / height);

                Ray ray = new Ray(new Vector3(0, 0, 0), new Vector3(u, v, -1));
                Vector3 finalColor = trace(ray, objects, lightPos, 1);

                // Convert [0,1] normalized colour channels back to 8-bit integer space
                int r = (int) (Math.min(1.0, finalColor.x) * 255);
                int g = (int) (Math.min(1.0, finalColor.y) * 255);
                int b = (int) (Math.min(1.0, finalColor.z) * 255);

                Color pixelColor = new Color(r, g, b);
                image.setRGB(x, y, pixelColor.getRGB());
            }
        }

        try {
            File outputFile = new File("output.png");
            ImageIO.write(image, "png", outputFile);
            System.out.println("Multi-object render complete! Saved to output.png");
        } catch (IOException e) {
            System.out.println("Error saving image: " + e.getMessage());
        }
    }
}
