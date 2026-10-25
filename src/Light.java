/** A simple point light: a position plus an RGB intensity (0..1 range, can exceed 1 for a bright light). */
public class Light {
    public final Vec3 position;
    public final Vec3 intensity;

    public Light(Vec3 position, Vec3 intensity) {
        this.position = position;
        this.intensity = intensity;
    }
}
