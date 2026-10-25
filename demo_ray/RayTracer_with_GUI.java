import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import javax.swing.*;

public class RayTracer_with_GUI extends JFrame {

    static final int MAX_DEPTH = 4;
    static final int RENDER_WIDTH = 800;
    static final int RENDER_HEIGHT = 600;

    // --- 3D MATH ENGINE ---

    static class Vector3 {
        double x, y, z;

        Vector3(double x, double y, double z) {
            this.x = x; this.y = y; this.z = z;
        }

        Vector3 add(Vector3 v) { return new Vector3(x + v.x, y + v.y, z + v.z); }
        Vector3 sub(Vector3 v) { return new Vector3(x - v.x, y - v.y, z - v.z); }
        Vector3 mult(double c) { return new Vector3(x * c, y * c, z * c); }
        double dot(Vector3 v) { return x * v.x + y * v.y + z * v.z; }
        Vector3 cross(Vector3 v) {
            return new Vector3(
                y * v.z - z * v.y,
                z * v.x - x * v.z,
                x * v.y - y * v.x
            );
        }
        Vector3 normalize() {
            double len = Math.sqrt(dot(this));
            return len > 0 ? new Vector3(x / len, y / len, z / len) : this;
        }
    }

    static class Ray {
        Vector3 origin, direction;
        Ray(Vector3 origin, Vector3 direction) {
            this.origin = origin;
            this.direction = direction.normalize();
        }
    }

    abstract static class SceneObject {
        Vector3 center;
        Color color;
        double reflectivity;
        double transparency; // 0.0 (opaque) to 1.0 (clear glass)
        double ior = 1.5;     // Index of refraction (glass)
        String name;

        SceneObject(String name, Vector3 center, Color color, double reflectivity, double transparency) {
            this.name = name;
            this.center = center;
            this.color = color;
            this.reflectivity = reflectivity;
            this.transparency = transparency;
        }

        abstract void setPosition(Vector3 newPos);
        abstract double intersect(Ray ray);
        abstract Vector3 getNormal(Vector3 hitPoint);
        abstract Color getColorAt(Vector3 hitPoint);
    }

    // 1. Sphere
    static class Sphere extends SceneObject {
        double radius;

        Sphere(Vector3 center, double radius, Color color, double reflectivity, double transparency) {
            super("Sphere", center, color, reflectivity, transparency);
            this.radius = radius;
        }

        @Override
        void setPosition(Vector3 newPos) {
            this.center = newPos;
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

    // 2. Cube
    static class Cube extends SceneObject {
        double size;
        Vector3 min, max;

        Cube(Vector3 center, double size, Color color, double reflectivity, double transparency) {
            super("Cube", center, color, reflectivity, transparency);
            this.size = size;
            updateBounds();
        }

        void updateBounds() {
            double h = size / 2.0;
            this.min = new Vector3(center.x - h, center.y - h, center.z - h);
            this.max = new Vector3(center.x + h, center.y + h, center.z + h);
        }

        @Override
        void setPosition(Vector3 newPos) {
            this.center = newPos;
            updateBounds();
        }

        @Override
        double intersect(Ray ray) {
            double tmin = (min.x - ray.origin.x) / (Math.abs(ray.direction.x) < 1e-9 ? 1e-9 : ray.direction.x);
            double tmax = (max.x - ray.origin.x) / (Math.abs(ray.direction.x) < 1e-9 ? 1e-9 : ray.direction.x);
            if (tmin > tmax) { double tmp = tmin; tmin = tmax; tmax = tmp; }

            double tymin = (min.y - ray.origin.y) / (Math.abs(ray.direction.y) < 1e-9 ? 1e-9 : ray.direction.y);
            double tymax = (max.y - ray.origin.y) / (Math.abs(ray.direction.y) < 1e-9 ? 1e-9 : ray.direction.y);
            if (tymin > tymax) { double tmp = tymin; tymin = tymax; tymax = tmp; }

            if ((tmin > tymax) || (tymin > tmax)) return -1;
            if (tymin > tmin) tmin = tymin;
            if (tymax < tmax) tmax = tymax;

            double tzmin = (min.z - ray.origin.z) / (Math.abs(ray.direction.z) < 1e-9 ? 1e-9 : ray.direction.z);
            double tzmax = (max.z - ray.origin.z) / (Math.abs(ray.direction.z) < 1e-9 ? 1e-9 : ray.direction.z);
            if (tzmin > tzmax) { double tmp = tzmin; tzmin = tzmax; tzmax = tmp; }

            if ((tmin > tzmax) || (tzmin > tmax)) return -1;
            if (tzmin > tmin) tmin = tzmin;
            if (tzmax < tmax) tmax = tzmax;

            if (tmin > 0.001) return tmin;
            if (tmax > 0.001) return tmax;
            return -1;
        }

        @Override
        Vector3 getNormal(Vector3 hitPoint) {
            Vector3 p = hitPoint.sub(center);
            double ax = Math.abs(p.x);
            double ay = Math.abs(p.y);
            double az = Math.abs(p.z);
            if (ax >= ay && ax >= az) return new Vector3(Math.signum(p.x), 0, 0);
            if (ay >= ax && ay >= az) return new Vector3(0, Math.signum(p.y), 0);
            return new Vector3(0, 0, Math.signum(p.z));
        }

        @Override
        Color getColorAt(Vector3 hitPoint) {
            return this.color;
        }
    }

    // 3. Pyramid
    static class Pyramid extends SceneObject {
        double width, height;
        Vector3[][] faces;
        Vector3[] normals;

        Pyramid(Vector3 center, double width, double height, Color color, double reflectivity, double transparency) {
            super("Pyramid", center, color, reflectivity, transparency);
            this.width = width;
            this.height = height;
            updateFaces();
        }

        void updateFaces() {
            double hw = width / 2.0;
            double hh = height / 2.0;

            Vector3 apex = new Vector3(center.x, center.y + hh, center.z);
            Vector3 p0 = new Vector3(center.x - hw, center.y - hh, center.z - hw);
            Vector3 p1 = new Vector3(center.x + hw, center.y - hh, center.z - hw);
            Vector3 p2 = new Vector3(center.x + hw, center.y - hh, center.z + hw);
            Vector3 p3 = new Vector3(center.x - hw, center.y - hh, center.z + hw);

            faces = new Vector3[][]{
                { apex, p0, p1 },
                { apex, p1, p2 },
                { apex, p2, p3 },
                { apex, p3, p0 },
                { p0, p2, p1 },
                { p0, p3, p2 }
            };

            normals = new Vector3[6];
            for (int i = 0; i < 6; i++) {
                Vector3 e1 = faces[i][1].sub(faces[i][0]);
                Vector3 e2 = faces[i][2].sub(faces[i][0]);
                normals[i] = e1.cross(e2).normalize();
            }
        }

        @Override
        void setPosition(Vector3 newPos) {
            this.center = newPos;
            updateFaces();
        }

        private double intersectTri(Ray ray, Vector3 v0, Vector3 v1, Vector3 v2) {
            Vector3 edge1 = v1.sub(v0);
            Vector3 edge2 = v2.sub(v0);
            Vector3 h = ray.direction.cross(edge2);
            double a = edge1.dot(h);
            if (a > -1e-6 && a < 1e-6) return -1;
            double f = 1.0 / a;
            Vector3 s = ray.origin.sub(v0);
            double u = f * s.dot(h);
            if (u < 0.0 || u > 1.0) return -1;
            Vector3 q = s.cross(edge1);
            double v = f * ray.direction.dot(q);
            if (v < 0.0 || u + v > 1.0) return -1;
            double t = f * edge2.dot(q);
            return t > 0.001 ? t : -1;
        }

        @Override
        double intersect(Ray ray) {
            double closest = Double.MAX_VALUE;
            for (Vector3[] tri : faces) {
                double t = intersectTri(ray, tri[0], tri[1], tri[2]);
                if (t > 0.001 && t < closest) closest = t;
            }
            return closest == Double.MAX_VALUE ? -1 : closest;
        }

        @Override
        Vector3 getNormal(Vector3 hitPoint) {
            double minDist = Double.MAX_VALUE;
            Vector3 bestNormal = new Vector3(0, 1, 0);
            for (int i = 0; i < faces.length; i++) {
                double dist = Math.abs((hitPoint.sub(faces[i][0])).dot(normals[i]));
                if (dist < minDist) {
                    minDist = dist;
                    bestNormal = normals[i];
                }
            }
            return bestNormal;
        }

        @Override
        Color getColorAt(Vector3 hitPoint) {
            return this.color;
        }
    }

    // 4. Floor Plane
    static class Plane extends SceneObject {
        Vector3 normal;

        Plane(Vector3 point, Vector3 normal, Color color, double reflectivity) {
            super("Floor", point, color, reflectivity, 0.0);
            this.normal = normal.normalize();
        }

        @Override
        void setPosition(Vector3 newPos) { }

        @Override
        double intersect(Ray ray) {
            double denom = normal.dot(ray.direction);
            if (Math.abs(denom) > 1e-6) {
                double t = center.sub(ray.origin).dot(normal) / denom;
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
            int xCheck = (int) Math.floor(hitPoint.x + 1000) % 2;
            int zCheck = (int) Math.floor(hitPoint.z + 1000) % 2;
            return ((xCheck ^ zCheck) == 1) ? this.color : Color.LIGHT_GRAY;
        }
    }

    static class HitRecord {
        double t = -1;
        SceneObject obj = null;
    }

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

    // --- RAY TRACING WITH REFLECTION & SNELL'S LAW REFRACTION ---

    static Vector3 trace(Ray ray, List<SceneObject> objects, Vector3 lightPos, int depth, int maxDepth) {
        if (depth > maxDepth) return new Vector3(0, 0, 0);

        HitRecord hit = findClosestHit(ray, objects);
        if (hit.obj == null) {
            double t = 0.5 * (ray.direction.y + 1.0);
            return new Vector3(1.0 - t, 1.0 - t, 1.0).mult(0.5);
        }

        Vector3 hitPoint = ray.origin.add(ray.direction.mult(hit.t));
        Vector3 normal = hit.obj.getNormal(hitPoint);
        Vector3 toLight = lightPos.sub(hitPoint);
        double lightDist = Math.sqrt(toLight.dot(toLight));
        toLight = toLight.normalize();

        Ray shadowRay = new Ray(hitPoint.add(normal.mult(0.001)), toLight);
        HitRecord shadowHit = findClosestHit(shadowRay, objects);
        boolean inShadow = shadowHit.obj != null && shadowHit.t < lightDist && shadowHit.obj.transparency < 0.5;

        double diffuseIntensity = 0.2;
        if (!inShadow) {
            diffuseIntensity += Math.max(0.0, normal.dot(toLight)) * 0.8;
        }

        Color objColor = hit.obj.getColorAt(hitPoint);
        Vector3 baseColor = new Vector3(objColor.getRed() / 255.0, objColor.getGreen() / 255.0, objColor.getBlue() / 255.0);
        Vector3 localColor = baseColor.mult(diffuseIntensity);

        // Reflection
        if (maxDepth > 1 && hit.obj.reflectivity > 0) {
            double dot = ray.direction.dot(normal);
            Vector3 reflectDir = ray.direction.sub(normal.mult(2 * dot)).normalize();
            Ray reflectRay = new Ray(hitPoint.add(normal.mult(0.001)), reflectDir);
            Vector3 reflectedColor = trace(reflectRay, objects, lightPos, depth + 1, maxDepth);
            localColor = localColor.mult(1.0 - hit.obj.reflectivity).add(reflectedColor.mult(hit.obj.reflectivity));
        }

        // Transparency / Refraction (Snell's Law)
        if (maxDepth > 1 && hit.obj.transparency > 0) {
            double n1 = 1.0;
            double n2 = hit.obj.ior;
            Vector3 N = normal;
            double cosi = -ray.direction.dot(N);

            if (cosi < 0) { // Ray is exiting object
                cosi = -cosi;
                N = N.mult(-1);
                double temp = n1; n1 = n2; n2 = temp;
            }

            double eta = n1 / n2;
            double k = 1.0 - eta * eta * (1.0 - cosi * cosi);

            Vector3 refrColor;
            if (k >= 0) {
                Vector3 refrDir = ray.direction.mult(eta).add(N.mult(eta * cosi - Math.sqrt(k))).normalize();
                Ray refrRay = new Ray(hitPoint.sub(N.mult(0.002)), refrDir);
                refrColor = trace(refrRay, objects, lightPos, depth + 1, maxDepth);
            } else {
                // Total internal reflection
                Vector3 reflectDir = ray.direction.sub(N.mult(2 * ray.direction.dot(N))).normalize();
                Ray reflectRay = new Ray(hitPoint.add(N.mult(0.002)), reflectDir);
                refrColor = trace(reflectRay, objects, lightPos, depth + 1, maxDepth);
            }

            localColor = localColor.mult(1.0 - hit.obj.transparency).add(refrColor.mult(hit.obj.transparency));
        }

        return localColor;
    }

    // --- GUI & APPLICATION STATE ---

    private final List<SceneObject> sceneObjects = new ArrayList<>();
    private final DefaultListModel<String> objectListModel = new DefaultListModel<>();
    private Color selectedColor = Color.RED;
    private double currentTransparency = 0.0;

    private JLabel viewportLabel;
    private JButton colorButton;
    private JSlider transparencySlider;
    private JLabel transLabel;
    private JTextField xCoordField, yCoordField, zCoordField;
    private JLabel selectedObjectLabel;
    private BufferedImage currentRenderedImage;

    // Camera
    private Vector3 camTarget = new Vector3(0.0, 0.0, -4.5);
    private double camDistance = 4.5;
    private double camYaw = 0.0;
    private double camPitch = 0.0;
    private Point lastMousePoint = null;
    private boolean isOrbiting = false;

    // Interaction
    private SceneObject selectedObject = null;
    private SceneObject draggedObject = null;
    private Vector3 dragOffset = new Vector3(0, 0, 0);
    private String spawningShape = null;

    // Demo Mode: 3D Ray Path segments
    private boolean isDemoMode = false;
    static class RaySegment {
        Vector3 start, end;
        Color color;
        RaySegment(Vector3 start, Vector3 end, Color color) {
            this.start = start; this.end = end; this.color = color;
        }
    }
    private final List<RaySegment> demoRaySegments = new ArrayList<>();

    public RayTracer_with_GUI() {
        setTitle("3D Ray Tracer Studio - Ray Paths & Transparency (Press 'Q' to Quit)");
        setSize(1180, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 'Q' to quit
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0), "quitAction");
        getRootPane().getActionMap().put("quitAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });

        // Floor
        sceneObjects.add(new Plane(new Vector3(0, -1.0, 0), new Vector3(0, 1, 0), Color.DARK_GRAY, 0.2));

        // --- SIDEBAR PANEL ---
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        controlPanel.setPreferredSize(new Dimension(310, 850));

        // 1. Color Picker
        controlPanel.add(new JLabel("1. Color:"));
        colorButton = new JButton("Choose Color");
        colorButton.setBackground(selectedColor);
        colorButton.setForeground(Color.WHITE);
        colorButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        colorButton.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Select Color", selectedColor);
            if (chosen != null) {
                selectedColor = chosen;
                colorButton.setBackground(selectedColor);
                if (selectedObject != null && !(selectedObject instanceof Plane)) {
                    selectedObject.color = selectedColor;
                    renderScene(false);
                }
            }
        });
        controlPanel.add(colorButton);
        controlPanel.add(Box.createVerticalStrut(10));

        // 2. Transparency Slider
        transLabel = new JLabel("2. Transparency: 0% (Opaque)");
        controlPanel.add(transLabel);
        transparencySlider = new JSlider(0, 100, 0);
        transparencySlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        transparencySlider.addChangeListener(e -> {
            currentTransparency = transparencySlider.getValue() / 100.0;
            int pct = transparencySlider.getValue();
            transLabel.setText("2. Transparency: " + pct + "%" + (pct == 0 ? " (Opaque)" : pct == 100 ? " (Clear Glass)" : ""));
            if (selectedObject != null && !(selectedObject instanceof Plane)) {
                selectedObject.transparency = currentTransparency;
                renderScene(true);
            }
        });
        controlPanel.add(transparencySlider);
        controlPanel.add(Box.createVerticalStrut(10));

        // 3. Drag from Palette
        controlPanel.add(new JLabel("3. Drag Shape into Window:"));
        JPanel dragButtonsPanel = new JPanel(new GridLayout(3, 1, 4, 4));
        dragButtonsPanel.add(createPaletteButton("🔴 Drag Sphere", "Sphere"));
        dragButtonsPanel.add(createPaletteButton("🟦 Drag Cube", "Cube"));
        dragButtonsPanel.add(createPaletteButton("🔺 Drag Pyramid", "Pyramid"));
        dragButtonsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));
        controlPanel.add(dragButtonsPanel);
        controlPanel.add(Box.createVerticalStrut(10));

        // 4. Coordinates
        controlPanel.add(new JLabel("4. Coordinates (X, Y, Z):"));
        JPanel coordPanel = new JPanel(new GridLayout(1, 3, 4, 4));
        xCoordField = new JTextField("0.0");
        yCoordField = new JTextField("0.0");
        zCoordField = new JTextField("-4.5");
        coordPanel.add(xCoordField);
        coordPanel.add(yCoordField);
        coordPanel.add(zCoordField);
        coordPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        controlPanel.add(coordPanel);
        controlPanel.add(Box.createVerticalStrut(6));

        JButton addManualBtn = new JButton("+ Add at Coordinates");
        addManualBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        addManualBtn.addActionListener(e -> addModelAtCoordinates());
        controlPanel.add(addManualBtn);
        controlPanel.add(Box.createVerticalStrut(10));

        // 5. Scene Objects List & Selection
        selectedObjectLabel = new JLabel("Selected: (None)");
        selectedObjectLabel.setForeground(new Color(0, 102, 204));
        controlPanel.add(selectedObjectLabel);

        JList<String> objectList = new JList<>(objectListModel);
        objectList.addListSelectionListener(e -> {
            int idx = objectList.getSelectedIndex();
            int cur = 0;
            for (SceneObject obj : sceneObjects) {
                if (!(obj instanceof Plane)) {
                    if (cur == idx) {
                        selectObject(obj);
                        break;
                    }
                    cur++;
                }
            }
        });
        JScrollPane listScroller = new JScrollPane(objectList);
        listScroller.setPreferredSize(new Dimension(270, 75));
        controlPanel.add(listScroller);
        controlPanel.add(Box.createVerticalStrut(6));

        // Delete Selected Object Button
        JButton deleteBtn = new JButton("🗑 Delete Selected");
        deleteBtn.setForeground(new Color(178, 34, 34));
        deleteBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        deleteBtn.addActionListener(e -> deleteSelectedObject());
        controlPanel.add(deleteBtn);
        controlPanel.add(Box.createVerticalStrut(10));

        // 6. Action Buttons
        JButton renderButton = new JButton("▶ RENDER SCENE (Save PNG)");
        renderButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        renderButton.setBackground(new Color(34, 139, 34));
        renderButton.setForeground(Color.WHITE);
        renderButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        renderButton.addActionListener(e -> renderAndSavePNG());
        controlPanel.add(renderButton);
        controlPanel.add(Box.createVerticalStrut(6));

        JButton resetCamBtn = new JButton("Reset Camera (0°)");
        resetCamBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        resetCamBtn.addActionListener(e -> {
            camYaw = 0.0; camPitch = 0.0; camDistance = 4.5;
            renderScene(false);
        });
        controlPanel.add(resetCamBtn);
        controlPanel.add(Box.createVerticalStrut(6));

        JButton clearButton = new JButton("Clear All");
        clearButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        clearButton.addActionListener(e -> {
            sceneObjects.clear();
            sceneObjects.add(new Plane(new Vector3(0, -1.0, 0), new Vector3(0, 1, 0), Color.DARK_GRAY, 0.2));
            selectedObject = null;
            isDemoMode = false;
            selectedObjectLabel.setText("Selected: (None)");
            updateObjectListDisplay();
            renderScene(false);
        });
        controlPanel.add(clearButton);
        controlPanel.add(Box.createVerticalStrut(6));

        JButton quitButton = new JButton("Quit ('q')");
        quitButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        quitButton.addActionListener(e -> System.exit(0));
        controlPanel.add(quitButton);
        controlPanel.add(Box.createVerticalStrut(12));

        // 7. Ray Tracer Demo Button (Below All Buttons)
        JButton demoButton = new JButton("✨ RAY TRACER DEMO (Rays Path)");
        demoButton.setFont(new Font("SansSerif", Font.BOLD, 12));
        demoButton.setBackground(new Color(75, 0, 130)); // Indigo
        demoButton.setForeground(Color.WHITE);
        demoButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        demoButton.addActionListener(e -> setupRayTracerDemo());
        controlPanel.add(demoButton);

        JScrollPane sidebarScroll = new JScrollPane(controlPanel);
        sidebarScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(sidebarScroll, BorderLayout.WEST);

        // Center Viewport
        viewportLabel = new JLabel();
        viewportLabel.setBackground(Color.BLACK);
        viewportLabel.setOpaque(true);
        viewportLabel.setHorizontalAlignment(SwingConstants.CENTER);
        setupMouseInteractions(viewportLabel);
        add(new JScrollPane(viewportLabel), BorderLayout.CENTER);

        // Initial Scene Setup
        addObjectToScene(new Sphere(new Vector3(-1.8, 0.2, -5.0), 1.2, Color.RED, 0.3, 0.0));
        addObjectToScene(new Cube(new Vector3(1.6, -0.1, -4.5), 1.4, Color.BLUE, 0.2, 0.0));
        addObjectToScene(new Pyramid(new Vector3(0.0, 0.0, -3.8), 1.2, 1.6, Color.YELLOW, 0.1, 0.0));

        renderScene(false);
    }

    // --- SELECTION & DELETION ---

    private void selectObject(SceneObject obj) {
        selectedObject = obj;
        if (selectedObject != null && !(selectedObject instanceof Plane)) {
            selectedObjectLabel.setText("Selected: " + selectedObject.name);
            xCoordField.setText(String.format("%.2f", selectedObject.center.x));
            yCoordField.setText(String.format("%.2f", selectedObject.center.y));
            zCoordField.setText(String.format("%.2f", selectedObject.center.z));
            selectedColor = selectedObject.color;
            colorButton.setBackground(selectedColor);
            transparencySlider.setValue((int) (selectedObject.transparency * 100));
        } else {
            selectedObjectLabel.setText("Selected: (None)");
        }
    }

    private void deleteSelectedObject() {
        if (selectedObject != null && !(selectedObject instanceof Plane)) {
            sceneObjects.remove(selectedObject);
            selectedObject = null;
            selectedObjectLabel.setText("Selected: (None)");
            updateObjectListDisplay();
            renderScene(false);
        } else {
            JOptionPane.showMessageDialog(this, "Please click on an object to select it before deleting.",
                                          "No Object Selected", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // --- RAY TRACER DEMO (2 SPHERES + RAY PATHS) ---

    private void setupRayTracerDemo() {
        isDemoMode = true;
        sceneObjects.clear();
        sceneObjects.add(new Plane(new Vector3(0, -1.0, 0), new Vector3(0, 1, 0), Color.DARK_GRAY, 0.2));

        // Sphere 1: Solid & Reflective Red
        Sphere solidSphere = new Sphere(new Vector3(-1.4, 0.0, -4.5), 1.1, new Color(220, 20, 60), 0.5, 0.0);
        addObjectToScene(solidSphere);

        // Sphere 2: Clear Transparent Glass Sphere (transparency = 0.9, IOR = 1.5)
        Sphere glassSphere = new Sphere(new Vector3(1.4, 0.0, -4.5), 1.1, new Color(240, 248, 255), 0.15, 0.9);
        addObjectToScene(glassSphere);

        // Set angled camera view so you can see ray trajectories clearly in 3D
        camYaw = 0.35;
        camPitch = 0.22;
        camDistance = 6.0;
        camTarget = new Vector3(0.0, 0.0, -4.5);

        generateDemoRayPaths();
        renderScene(false);
    }

    private void generateDemoRayPaths() {
        demoRaySegments.clear();
        Vector3 camOrigin = new Vector3(0.0, 0.3, -1.5); // Virtual camera emitter

        // 4 Rays targeting the solid sphere (Reflection demo)
        double[][] solidTargets = {
            {-1.7, 0.5, -4.2},
            {-1.4, 0.1, -4.0},
            {-1.1, -0.3, -4.1},
            {-1.4, 0.7, -4.3}
        };

        for (double[] target : solidTargets) {
            Vector3 hitPoint = new Vector3(target[0], target[1], target[2]);
            demoRaySegments.add(new RaySegment(camOrigin, hitPoint, Color.YELLOW)); // Primary ray

            Vector3 norm = hitPoint.sub(new Vector3(-1.4, 0.0, -4.5)).normalize();
            Vector3 inDir = hitPoint.sub(camOrigin).normalize();
            Vector3 reflectDir = inDir.sub(norm.mult(2 * inDir.dot(norm))).normalize();
            Vector3 reflectEnd = hitPoint.add(reflectDir.mult(2.5));
            demoRaySegments.add(new RaySegment(hitPoint, reflectEnd, new Color(255, 140, 0))); // Reflected ray
        }

        // 4 Rays targeting the transparent sphere (Refraction demo)
        double[][] glassTargets = {
            {1.1, 0.4, -4.1},
            {1.4, 0.1, -3.9},
            {1.7, -0.2, -4.1},
            {1.4, 0.6, -4.2}
        };

        for (double[] target : glassTargets) {
            Vector3 hitPoint = new Vector3(target[0], target[1], target[2]);
            demoRaySegments.add(new RaySegment(camOrigin, hitPoint, Color.YELLOW)); // Primary ray

            Vector3 sphereCenter = new Vector3(1.4, 0.0, -4.5);
            Vector3 inDir = hitPoint.sub(camOrigin).normalize();
            Vector3 norm = hitPoint.sub(sphereCenter).normalize();

            // Refract inside
            double eta = 1.0 / 1.5;
            double cosi = -inDir.dot(norm);
            double k = 1.0 - eta * eta * (1.0 - cosi * cosi);
            if (k >= 0) {
                Vector3 refrDir = inDir.mult(eta).add(norm.mult(eta * cosi - Math.sqrt(k))).normalize();
                Vector3 exitPoint = hitPoint.add(refrDir.mult(1.8)); // Approximate traversal across sphere
                demoRaySegments.add(new RaySegment(hitPoint, exitPoint, Color.CYAN)); // Inside glass ray

                // Refract exiting
                Vector3 exitNorm = exitPoint.sub(sphereCenter).normalize().mult(-1);
                double exitEta = 1.5 / 1.0;
                double exitCosi = -refrDir.dot(exitNorm);
                double exitK = 1.0 - exitEta * exitEta * (1.0 - exitCosi * exitCosi);
                Vector3 finalDir = exitK >= 0 ? refrDir.mult(exitEta).add(exitNorm.mult(exitEta * exitCosi - Math.sqrt(exitK))).normalize() : refrDir;
                Vector3 finalEnd = exitPoint.add(finalDir.mult(2.5));
                demoRaySegments.add(new RaySegment(exitPoint, finalEnd, new Color(255, 0, 255))); // Exiting ray
            }
        }
    }

    // --- PROJECTION & RENDERING ---

    private Point projectToScreen(Vector3 p) {
        Vector3 camPos = getCameraPosition();
        Vector3 forward = camTarget.sub(camPos).normalize();
        Vector3 worldUp = new Vector3(0, 1, 0);
        Vector3 right = forward.cross(worldUp).normalize();
        Vector3 up = right.cross(forward).normalize();

        Vector3 diff = p.sub(camPos);
        double zDepth = diff.dot(forward);
        if (zDepth <= 0.1) return null; // Behind camera

        double aspect = (double) RENDER_WIDTH / RENDER_HEIGHT;
        double xCam = diff.dot(right);
        double yCam = diff.dot(up);

        double u = xCam / zDepth;
        double v = yCam / zDepth;

        int px = (int) Math.round(((u / aspect + 1.0) * 0.5) * RENDER_WIDTH - 0.5);
        int py = (int) Math.round(((1.0 - v) * 0.5) * RENDER_HEIGHT - 0.5);
        return new Point(px, py);
    }

    private void renderAndSavePNG() {
        renderScene(false);
        try {
            File outputFile = new File("rendered_scene.png");
            ImageIO.write(currentRenderedImage, "png", outputFile);
            JOptionPane.showMessageDialog(this,
                "Render completed!\nSaved image to:\n" + outputFile.getAbsolutePath(),
                "PNG Export Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save PNG: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void renderScene(boolean fastPreview) {
        BufferedImage image = new BufferedImage(RENDER_WIDTH, RENDER_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Vector3 lightPos = new Vector3(5.0, 8.0, 0.0);
        int step = fastPreview ? 4 : 1;
        int maxDepth = fastPreview ? 1 : MAX_DEPTH;

        IntStream.range(0, RENDER_HEIGHT / step).parallel().forEach(sy -> {
            int y = sy * step;
            for (int sx = 0; sx < RENDER_WIDTH / step; sx++) {
                int x = sx * step;
                Ray ray = getRayFromPixel(x, y);
                Vector3 finalColor = trace(ray, sceneObjects, lightPos, 1, maxDepth);

                int r = (int) (Math.min(1.0, finalColor.x) * 255);
                int g = (int) (Math.min(1.0, finalColor.y) * 255);
                int b = (int) (Math.min(1.0, finalColor.z) * 255);
                int rgb = new Color(r, g, b).getRGB();

                for (int dy = 0; dy < step && (y + dy) < RENDER_HEIGHT; dy++) {
                    for (int dx = 0; dx < step && (x + dx) < RENDER_WIDTH; dx++) {
                        image.setRGB(x + dx, y + dy, rgb);
                    }
                }
            }
        });

        // Overlay 3D Ray paths if Demo Mode is active
        if (isDemoMode && !demoRaySegments.isEmpty()) {
            Graphics2D g2 = image.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            for (RaySegment seg : demoRaySegments) {
                Point p1 = projectToScreen(seg.start);
                Point p2 = projectToScreen(seg.end);
                if (p1 != null && p2 != null) {
                    g2.setColor(seg.color);
                    g2.setStroke(new BasicStroke(2.5f));
                    g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                    g2.fillOval(p2.x - 3, p2.y - 3, 6, 6);
                }
            }

            // Legend Overlay
            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRoundRect(15, 15, 480, 55, 10, 10);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            g2.drawString("RAY PATH DEMO (Hold Middle-Click to Orbit 360° around rays):", 25, 33);
            g2.setColor(Color.YELLOW);
            g2.drawString("■ Primary Ray   ", 25, 53);
            g2.setColor(new Color(255, 140, 0));
            g2.drawString("■ Reflection (Solid)   ", 135, 53);
            g2.setColor(Color.CYAN);
            g2.drawString("■ Internal Refraction   ", 280, 53);
            g2.setColor(new Color(255, 0, 255));
            g2.drawString("■ Exit Ray", 415, 53);

            g2.dispose();
        }

        currentRenderedImage = image;
        viewportLabel.setIcon(new ImageIcon(image));
    }

    // --- MOUSE & CAMERA HELPERS ---

    private Vector3 getCameraPosition() {
        double x = camTarget.x + camDistance * Math.cos(camPitch) * Math.sin(camYaw);
        double y = camTarget.y + camDistance * Math.sin(camPitch);
        double z = camTarget.z + camDistance * Math.cos(camPitch) * Math.cos(camYaw);
        return new Vector3(x, y, z);
    }

    private Ray getRayFromPixel(int px, int py) {
        Vector3 camPos = getCameraPosition();
        Vector3 forward = camTarget.sub(camPos).normalize();
        Vector3 worldUp = new Vector3(0, 1, 0);
        Vector3 right = forward.cross(worldUp).normalize();
        Vector3 up = right.cross(forward).normalize();

        double aspect = (double) RENDER_WIDTH / RENDER_HEIGHT;
        double u = (2.0 * (px + 0.5) / RENDER_WIDTH - 1.0) * aspect;
        double v = (1.0 - 2.0 * (py + 0.5) / RENDER_HEIGHT);

        Vector3 rayDir = forward.add(right.mult(u)).add(up.mult(v)).normalize();
        return new Ray(camPos, rayDir);
    }

    private void setupMouseInteractions(JLabel viewport) {
        viewport.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMousePoint = e.getPoint();

                // 360 Orbit: Middle Mouse Button OR Alt + Left Click
                if (SwingUtilities.isMiddleMouseButton(e) || (e.isAltDown() && SwingUtilities.isLeftMouseButton(e))) {
                    isOrbiting = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    return;
                }

                Point p = getRenderCoordinates(e.getPoint());
                if (p == null) return;
                Ray ray = getRayFromPixel(p.x, p.y);

                // Spawning from palette
                if (spawningShape != null) {
                    Vector3 camPos = getCameraPosition();
                    Vector3 forward = camTarget.sub(camPos).normalize();
                    double t = camTarget.sub(camPos).dot(forward) / ray.direction.dot(forward);
                    Vector3 dropPos = camPos.add(ray.direction.mult(t));

                    SceneObject newObj = createShape(spawningShape, dropPos);
                    addObjectToScene(newObj);
                    selectObject(newObj);
                    draggedObject = newObj;
                    dragOffset = new Vector3(0, 0, 0);
                    spawningShape = null;
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    renderScene(true);
                    return;
                }

                // Pick/Select existing object
                HitRecord hit = findClosestHit(ray, sceneObjects);
                if (hit.obj != null && !(hit.obj instanceof Plane)) {
                    selectObject(hit.obj);
                    draggedObject = hit.obj;

                    Vector3 camPos = getCameraPosition();
                    Vector3 forward = camTarget.sub(camPos).normalize();
                    double t = draggedObject.center.sub(camPos).dot(forward) / ray.direction.dot(forward);
                    Vector3 hitPlanePoint = camPos.add(ray.direction.mult(t));
                    dragOffset = draggedObject.center.sub(hitPlanePoint);
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    selectObject(null);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (isOrbiting) {
                    isOrbiting = false;
                    setCursor(Cursor.getDefaultCursor());
                    renderScene(false);
                }
                if (draggedObject != null) {
                    draggedObject = null;
                    updateObjectListDisplay();
                    renderScene(false);
                }
                spawningShape = null;
                setCursor(Cursor.getDefaultCursor());
            }
        });

        viewport.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastMousePoint == null) return;
                Point cur = e.getPoint();
                int dx = cur.x - lastMousePoint.x;
                int dy = cur.y - lastMousePoint.y;
                lastMousePoint = cur;

                if (isOrbiting) {
                    camYaw -= dx * 0.008;
                    camPitch += dy * 0.008;
                    camPitch = Math.max(-1.45, Math.min(1.45, camPitch));
                    renderScene(true);
                    return;
                }

                Point p = getRenderCoordinates(cur);
                if (p == null || draggedObject == null) return;

                Ray ray = getRayFromPixel(p.x, p.y);
                Vector3 camPos = getCameraPosition();
                Vector3 forward = camTarget.sub(camPos).normalize();
                double t = draggedObject.center.sub(camPos).dot(forward) / ray.direction.dot(forward);
                Vector3 newPos = camPos.add(ray.direction.mult(t)).add(dragOffset);

                draggedObject.setPosition(newPos);
                xCoordField.setText(String.format("%.2f", newPos.x));
                yCoordField.setText(String.format("%.2f", newPos.y));
                zCoordField.setText(String.format("%.2f", newPos.z));

                renderScene(true);
            }
        });

        viewport.addMouseWheelListener(e -> {
            Point p = getRenderCoordinates(e.getPoint());
            if (p == null) return;

            Ray ray = getRayFromPixel(p.x, p.y);
            HitRecord hit = findClosestHit(ray, sceneObjects);

            if (hit.obj != null && !(hit.obj instanceof Plane)) {
                Vector3 dir = getCameraPosition().sub(camTarget).normalize();
                Vector3 newPos = hit.obj.center.add(dir.mult(e.getWheelRotation() * 0.3));
                hit.obj.setPosition(newPos);
                updateObjectListDisplay();
            } else {
                camDistance = Math.max(1.5, Math.min(15.0, camDistance + e.getWheelRotation() * 0.4));
            }
            renderScene(false);
        });
    }

    private Point getRenderCoordinates(Point mousePoint) {
        Icon icon = viewportLabel.getIcon();
        if (icon == null) return null;
        int imgX = (viewportLabel.getWidth() - icon.getIconWidth()) / 2;
        int imgY = (viewportLabel.getHeight() - icon.getIconHeight()) / 2;
        int x = mousePoint.x - imgX;
        int y = mousePoint.y - imgY;
        if (x < 0 || x >= RENDER_WIDTH || y < 0 || y >= RENDER_HEIGHT) return null;
        return new Point(x, y);
    }

    private JButton createPaletteButton(String text, String shapeType) {
        JButton btn = new JButton(text);
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                spawningShape = shapeType;
                setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            }
        });
        return btn;
    }

    private SceneObject createShape(String shape, Vector3 pos) {
        switch (shape) {
            case "Cube":
                return new Cube(pos, 1.4, selectedColor, 0.2, currentTransparency);
            case "Pyramid":
                return new Pyramid(pos, 1.4, 1.7, selectedColor, 0.2, currentTransparency);
            default:
                return new Sphere(pos, 1.1, selectedColor, 0.3, currentTransparency);
        }
    }

    private void addObjectToScene(SceneObject obj) {
        sceneObjects.add(obj);
        updateObjectListDisplay();
    }

    private void updateObjectListDisplay() {
        objectListModel.clear();
        for (SceneObject obj : sceneObjects) {
            if (!(obj instanceof Plane)) {
                objectListModel.addElement(String.format("%s @ (%.1f, %.1f, %.1f)",
                        obj.name, obj.center.x, obj.center.y, obj.center.z));
            }
        }
    }

    private void addModelAtCoordinates() {
        try {
            double x = Double.parseDouble(xCoordField.getText().trim());
            double y = Double.parseDouble(yCoordField.getText().trim());
            double z = Double.parseDouble(zCoordField.getText().trim());
            SceneObject obj = createShape("Sphere", new Vector3(x, y, z));
            addObjectToScene(obj);
            selectObject(obj);
            renderScene(false);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter valid numeric coordinates.",
                                          "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            RayTracer_with_GUI gui = new RayTracer_with_GUI();
            gui.setVisible(true);
        });
    }
}