/** A simple pinhole perspective camera that generates rays through an image plane. */
public class Camera {
    private final Vec3 origin;
    private final Vec3 forward;
    private final Vec3 right;
    private final Vec3 up;
    private final double halfHeight;
    private final double halfWidth;

    /**
     * @param lookFrom      camera position
     * @param lookAt        point the camera is aimed at
     * @param worldUp       approximate "up" direction (usually (0,1,0))
     * @param verticalFovDeg vertical field of view, in degrees
     * @param aspectRatio   image width / image height
     */
    public Camera(Vec3 lookFrom, Vec3 lookAt, Vec3 worldUp, double verticalFovDeg, double aspectRatio) {
        this.origin = lookFrom;
        this.forward = lookAt.sub(lookFrom).normalize();
        this.right = forward.cross(worldUp).normalize();
        this.up = right.cross(forward).normalize();

        double theta = Math.toRadians(verticalFovDeg);
        this.halfHeight = Math.tan(theta / 2.0);
        this.halfWidth = halfHeight * aspectRatio;
    }

    /**
     * Builds the ray through normalized image coordinates u, v in [0,1],
     * where (0,0) is the top-left corner of the image and (1,1) is bottom-right.
     */
    public Ray getRay(double u, double v) {
        double x = (2 * u - 1) * halfWidth;
        double y = (1 - 2 * v) * halfHeight;
        Vec3 direction = forward.add(right.scale(x)).add(up.scale(y)).normalize();
        return new Ray(origin, direction);
    }
}
