/**
 * Surface appearance for an object: a base color (or a checkerboard of two
 * colors), plus Phong-style shading coefficients.
 */
public class Material {
    public final Vec3 color1;
    public final Vec3 color2;   // null unless this is a checker material
    public final boolean checker;
    public final double checkerScale;

    public final double ambient;
    public final double diffuse;
    public final double specular;
    public final double shininess;
    public final double reflectivity; // 0 = matte, 1 = mirror

    private Material(Vec3 color1, Vec3 color2, boolean checker, double checkerScale,
                      double ambient, double diffuse, double specular,
                      double shininess, double reflectivity) {
        this.color1 = color1;
        this.color2 = color2;
        this.checker = checker;
        this.checkerScale = checkerScale;
        this.ambient = ambient;
        this.diffuse = diffuse;
        this.specular = specular;
        this.shininess = shininess;
        this.reflectivity = reflectivity;
    }

    public static Material solid(Vec3 color, double ambient, double diffuse,
                                  double specular, double shininess, double reflectivity) {
        return new Material(color, null, false, 1.0, ambient, diffuse, specular, shininess, reflectivity);
    }

    public static Material checker(Vec3 color1, Vec3 color2, double scale,
                                    double ambient, double diffuse, double specular,
                                    double shininess, double reflectivity) {
        return new Material(color1, color2, true, scale, ambient, diffuse, specular, shininess, reflectivity);
    }

    /** Returns the base color at a given world-space point (handles the checker pattern). */
    public Vec3 colorAt(Vec3 point) {
        if (!checker) return color1;
        double cx = Math.floor(point.x / checkerScale);
        double cz = Math.floor(point.z / checkerScale);
        boolean even = ((long) (cx + cz)) % 2 == 0;
        return even ? color1 : color2;
    }
}
