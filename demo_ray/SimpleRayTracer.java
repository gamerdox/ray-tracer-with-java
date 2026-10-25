import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class SimpleRayTracer {

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
            return new Vector3(x / len, y / len, z / len);
        }
    }

    // Ray definition
    static class Ray {
        Vector3 origin, direction;
        Ray(Vector3 origin, Vector3 direction) {
            this.origin = origin;
            this.direction = direction.normalize();
        }
    }

    // Sphere object
    static class Sphere {
        Vector3 center;
        double radius;
        Color color;

        Sphere(Vector3 center, double radius, Color color) {
            this.center = center;
            this.radius = radius;
            this.color = color;
        }

        // Returns distance t along the ray if it hits, -1 otherwise
        double intersect(Ray ray) {
            Vector3 oc = ray.origin.sub(center);
            double b = oc.dot(ray.direction);
            double c = oc.dot(oc) - radius * radius;
            double discriminant = b * b - c;
            if (discriminant < 0) return -1;
            double t = -b - Math.sqrt(discriminant);
            return t > 0.001 ? t : -b + Math.sqrt(discriminant);
        }
    }

    public static void main(String[] args) {
        int width = 800;
        int height = 600;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Scene setup
        Sphere sphere = new Sphere(new Vector3(0, 0, -5), 1.5, Color.RED);
        Vector3 lightDir = new Vector3(1, 1, -1).normalize();

        // Camera setup
        double aspect = (double) width / height;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Map pixel to [-aspect, aspect] x [-1, 1]
                double u = (2.0 * (x + 0.5) / width - 1.0) * aspect;
                double v = (1.0 - 2.0 * (y + 0.5) / height);

                Ray ray = new Ray(new Vector3(0, 0, 0), new Vector3(u, v, -1));

                double t = sphere.intersect(ray);
                Color pixelColor = Color.BLACK;

                if (t > 0) {
                    // Compute intersection point and normal
                    Vector3 hitPoint = ray.origin.add(ray.direction.mult(t));
                    Vector3 normal = hitPoint.sub(sphere.center).normalize();

                    // Simple diffuse lighting (Lambertian)
                    double diff = Math.max(0.0, normal.dot(lightDir));
                    
                    int red = (int) (sphere.color.getRed() * diff);
                    int green = (int) (sphere.color.getGreen() * diff);
                    int blue = (int) (sphere.color.getBlue() * diff);

                    pixelColor = new Color(Math.min(255, red), Math.min(255, green), Math.min(255, blue));
                }

                image.setRGB(x, y, pixelColor.getRGB());
            }
        }

        // Save image to PNG
        try {
            File outputFile = new File("output.png");
            ImageIO.write(image, "png", outputFile);
            System.out.println("Render complete! Saved to output.png");
        } catch (IOException e) {
            System.out.println("Error saving image: " + e.getMessage());
        }
    }
}
