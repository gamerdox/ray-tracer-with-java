# Project Report: Ray Tracer using JAVA

**Project type:** Course-Based Project (Java Programming)
**Language / Platform:** Java SE (Swing, AWT, ImageIO), no third-party libraries
**Main source file:** `demo_ray/RayTracer_with_GUI.java` (~1,060 lines)

---

## Table of Contents

1. [Abstract](#1-abstract)
2. [Introduction](#2-introduction)
3. [Objectives](#3-objectives)
4. [Background and Theory](#4-background-and-theory)
5. [System Requirements](#5-system-requirements)
6. [System Design](#6-system-design)
7. [Implementation](#7-implementation)
8. [User Interface](#8-user-interface)
9. [Results](#9-results)
10. [Testing](#10-testing)
11. [Performance](#11-performance)
12. [Limitations](#12-limitations)
13. [Future Enhancements](#13-future-enhancements)
14. [Conclusion](#14-conclusion)
15. [References](#15-references)
16. [Appendix](#appendix-a-key-code-excerpts)

---

## 1. Abstract

This project is a ray tracer written in Java with an interactive graphical
interface. Ray tracing creates an image by following rays of light from a
virtual camera through each pixel into a 3D scene and working out what color
each ray sees. The program renders spheres, cubes and pyramids on a
checkered floor. It supports diffuse lighting, hard shadows, recursive mirror
reflection and refraction through transparent objects using Snell's law.

Users build a scene with drag-and-drop, change each object's color and
transparency, orbit the camera 360° and export the result as a PNG. A
**Ray Path Demo** mode draws the rays the algorithm follows (primary,
reflected, refracted and exit rays) over the rendered image. This makes the
project a teaching tool as well as a renderer. The project uses only the
Java standard library.

## 2. Introduction

Computer graphics offers two main ways to turn a 3D scene into a 2D image:

- **Rasterization** projects each triangle onto the screen and fills in its
  pixels. It is fast, which is why GPUs and games use it, but reflections,
  refraction and shadows need extra techniques.
- **Ray tracing** reverses this process. For every pixel it asks *"what does
  the camera see in this direction?"* by casting a ray and following it as it
  bounces around the scene. Shadows, reflections and refraction come
  naturally from following more rays.

The project started as a minimal single-sphere renderer
(`SimpleRayTracer.java`). It was extended to a console renderer with
reflections (`RayTracer2.java`) and a modular, multi-file renderer with
Blinn-Phong shading and anti-aliasing (`src/`). The final version is the
interactive application `RayTracer_with_GUI.java`, which is the main subject
of this report.

## 3. Objectives

1. Implement the core ray tracing algorithm from first principles in Java.
2. Support several geometric primitives: sphere, cube, pyramid and plane.
3. Implement physically motivated lighting: diffuse shading, shadows,
   reflection and refraction.
4. Provide a GUI that lets the user build and edit a scene interactively
   without changing any code.
5. Allow free camera movement (360° orbit and zoom).
6. Export the final image to a standard file format (PNG).
7. Visualize the ray paths to explain how ray tracing works.
8. Keep the GUI responsive using a fast preview mode and multithreading.

## 4. Background and Theory

### 4.1 Rays

A ray is defined by an origin **O** and a unit direction **D**:

```
P(t) = O + t·D,   t > 0
```

Every intersection routine solves for the smallest positive `t` at which the
ray meets a surface. A small epsilon (`t > 0.001`) prevents a ray from
hitting the surface it just left ("shadow acne").

### 4.2 Camera model

The program uses a pinhole camera that orbits a target point. Given yaw θ,
pitch φ and distance d, the camera position is:

```
x = target.x + d·cos φ·sin θ
y = target.y + d·sin φ
z = target.z + d·cos φ·cos θ
```

A camera basis (forward, right, up) is built with cross products. Each pixel
(px, py) is mapped to normalized screen coordinates:

```
u = (2(px + 0.5)/W − 1) · aspect
v =  1 − 2(py + 0.5)/H
D = normalize(forward + u·right + v·up)
```

### 4.3 Ray-object intersection

**Sphere** (center C, radius r): substitute P(t) into |P − C|² = r². With a
normalized D, this gives:

```
b = (O − C)·D,   c = |O − C|² − r²,   Δ = b² − c
t = −b ± √Δ     (no hit if Δ < 0)
```

**Plane** (point P₀, normal N):

```
t = ((P₀ − O)·N) / (D·N)      (no hit if D·N ≈ 0)
```

**Cube (axis-aligned box):** the *slab method*. For each axis, compute the
`t` values where the ray enters and leaves the two parallel planes, then take
the largest entry and the smallest exit. The ray hits the box if
`t_enter ≤ t_exit`.

**Pyramid:** made of 6 triangles (4 sides and 2 for the square base), each
tested with the **Möller–Trumbore** algorithm. This algorithm computes `t`
and barycentric coordinates (u, v) directly. A hit is valid if
`u ≥ 0, v ≥ 0, u + v ≤ 1`.

### 4.4 Shading

**Lambertian diffuse:** light reflected from a matte surface is proportional
to the cosine of the angle between the surface normal N and the direction to
the light L:

```
intensity = 0.2 (ambient) + 0.8 · max(0, N·L)   (diffuse only if not in shadow)
```

**Shadows:** a *shadow ray* is cast from the hit point toward the light. If
it hits an opaque object closer than the light, the point is in shadow.

### 4.5 Reflection

The mirror direction of an incoming ray D about normal N is:

```
R = D − 2(D·N)·N
```

The reflected ray is traced recursively, and its color is blended with the
local color by the object's reflectivity k_r:

```
color = (1 − k_r)·local + k_r·reflected
```

### 4.6 Refraction (Snell's law)

When light passes between materials with refractive indices n₁ and n₂, it
bends according to **n₁ sin θ₁ = n₂ sin θ₂**. In vector form, with
η = n₁/n₂ and cos θᵢ = −D·N:

```
k = 1 − η²(1 − cos²θᵢ)
T = η·D + (η·cos θᵢ − √k)·N        if k ≥ 0
```

If `k < 0` there is no refracted ray. The light undergoes **total internal
reflection** and is reflected instead. When a ray is *leaving* an object
(D·N > 0), the normal is flipped and n₁ and n₂ are swapped. The project uses
n = 1.5 (typical glass), and the refracted color is blended by the object's
transparency.

### 4.7 Recursion depth

Every reflection or refraction spawns a new ray, so the algorithm is
recursive. It stops at `MAX_DEPTH = 4` bounces, which bounds the cost and
prevents infinite recursion between facing mirrors.

## 5. System Requirements

### Software

| Component | Requirement |
|-----------|-------------|
| JDK       | Java 11 or newer (tested on a current JDK) |
| OS        | Any OS with a JVM and a desktop (Windows, Linux, macOS) |
| Libraries | Java standard library only (`javax.swing`, `java.awt`, `javax.imageio`, `java.util.stream`) |

### Hardware

| Component | Recommendation |
|-----------|----------------|
| CPU       | A multi-core processor (rendering is parallelized across cores) |
| RAM       | 512 MB free is enough; an 800×600 image uses about 2 MB |
| Input     | A mouse with a middle button (or use Alt + left-drag to orbit) |

### Functional requirements

- FR1: Add spheres, cubes and pyramids by drag-and-drop or by typing coordinates.
- FR2: Select, move and delete objects.
- FR3: Change an object's color and transparency.
- FR4: Orbit and zoom the camera.
- FR5: Render with shadows, reflection and refraction.
- FR6: Save the render as a PNG file.
- FR7: Show a ray path visualization.

### Non-functional requirements

- NFR1: The GUI must stay responsive while objects are dragged.
- NFR2: No external dependencies, so the project builds with only `javac`.
- NFR3: The source must be readable for teaching purposes.

## 6. System Design

### 6.1 Architecture

The application is a single Java source file organized into three layers:

```
┌──────────────────────────────────────────────────────────────┐
│                   Presentation layer (Swing)                 │
│  JFrame · sidebar controls · viewport JLabel · mouse input   │
└───────────────────────────────┬──────────────────────────────┘
                                │ user actions → scene edits
┌───────────────────────────────▼──────────────────────────────┐
│                   Scene / application state                  │
│  List<SceneObject> · selected/dragged object · camera params │
│  demo ray segments                                           │
└───────────────────────────────┬──────────────────────────────┘
                                │ renderScene()
┌───────────────────────────────▼──────────────────────────────┐
│                     Ray tracing engine                       │
│  Vector3 · Ray · intersection · findClosestHit · trace()     │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 Class diagram

```
                    ┌──────────────────────────┐
                    │  «abstract» SceneObject  │
                    ├──────────────────────────┤
                    │ center: Vector3          │
                    │ color: Color             │
                    │ reflectivity: double     │
                    │ transparency: double     │
                    │ ior: double = 1.5        │
                    │ name: String             │
                    ├──────────────────────────┤
                    │ setPosition(Vector3)     │
                    │ intersect(Ray): double   │
                    │ getNormal(Vector3)       │
                    │ getColorAt(Vector3)      │
                    └────────────┬─────────────┘
        ┌──────────────┬─────────┴────┬──────────────┐
  ┌─────▼────┐   ┌─────▼────┐   ┌─────▼─────┐   ┌────▼─────┐
  │  Sphere  │   │   Cube   │   │  Pyramid  │   │  Plane   │
  │ radius   │   │ size,    │   │ width,    │   │ normal   │
  │          │   │ min, max │   │ height,   │   │ checker  │
  │          │   │          │   │ faces[6]  │   │ pattern  │
  └──────────┘   └──────────┘   └───────────┘   └──────────┘

  Vector3 (x,y,z; add, sub, mult, dot, cross, normalize)
  Ray (origin, direction)          HitRecord (t, obj)
  RaySegment (start, end, color)   — used by the demo overlay
```

All shapes share the same four-method interface. The renderer only works
with `SceneObject`, so a new primitive needs just one new subclass. This is
the **polymorphism** and **open/closed** principles in practice.

### 6.3 Rendering flow

```
renderScene(fastPreview)
 ├─ step     = fastPreview ? 4 : 1        (pixel block size)
 ├─ maxDepth = fastPreview ? 1 : 4        (bounces)
 └─ parallel for each row y:
      for each column x:
         ray   = getRayFromPixel(x, y)
         color = trace(ray, objects, light, 1, maxDepth)
         write pixel (or a step×step block)
 └─ if demo mode: project ray segments to 2D and draw overlay + legend
 └─ show the image in the viewport
```

```
trace(ray, depth)
 ├─ depth > maxDepth?            → black
 ├─ hit = findClosestHit(ray)
 ├─ no hit?                      → sky gradient
 ├─ shadow ray → diffuse term
 ├─ reflectivity > 0?            → blend trace(reflected ray, depth+1)
 ├─ transparency > 0?            → blend trace(refracted / TIR ray, depth+1)
 └─ return color
```

### 6.4 Interaction design

| Mouse input                 | Effect                                                                 |
|-----------------------------|------------------------------------------------------------------------|
| Press on palette button     | Arm a "spawn" of that shape (crosshair cursor)                         |
| Release / press in viewport | Place the new shape on the plane through the camera target, facing the camera |
| Left-press on object        | Pick with a ray (`findClosestHit`), select it, record the drag offset   |
| Left-drag                   | Move the object on a plane parallel to the screen at its current depth |
| Middle-drag / Alt+left-drag | Change yaw and pitch (pitch clamped to ±1.45 rad)                      |
| Wheel over object           | Move the object along the camera's view axis                           |
| Wheel over background       | Change camera distance (1.5 to 15)                                     |

Object picking reuses the ray tracer: the program casts a ray through the
clicked pixel and takes the closest hit.

## 7. Implementation

### 7.1 Vector math

`Vector3` is a small immutable-style class whose operations return new
vectors. This keeps the math code readable and close to the textbook
formulas. Normalizing a zero-length vector returns it unchanged instead of
producing NaNs.

### 7.2 Primitives

- **Sphere:** the quadratic solution in §4.3. It returns the near root, or
  the far root when the ray starts inside the sphere, which is needed for
  refraction rays traveling inside glass.
- **Cube:** stores `min` and `max` corners, which are recomputed in
  `updateBounds()` when the cube moves. Division by near-zero direction
  components is avoided by clamping to `1e-9`. The normal is the axis with
  the largest component of `(hit − center)`.
- **Pyramid:** builds 6 triangles from the apex and the 4 base corners and
  precomputes their face normals. `getNormal` returns the normal of the face
  plane closest to the hit point.
- **Plane:** a checkerboard of `DARK_GRAY` and `LIGHT_GRAY` computed from
  `floor(x + 1000) % 2 xor floor(z + 1000) % 2`. The +1000 offset avoids
  sign problems with negative coordinates.

### 7.3 The `trace` function

`trace()` implements §4.4–§4.7. Notable details:

- Shadow rays and reflected rays start at `hitPoint + 0.001·N` to avoid
  self-intersection. Refracted rays start at `hitPoint − 0.002·N`, just
  *inside* the surface.
- Transparent objects (transparency ≥ 0.5) do not block shadow rays, so glass
  objects do not cast solid black shadows.
- Reflection is blended first, then refraction, so an object can be both
  reflective and transparent (for example, the glass sphere has
  reflectivity 0.15 and transparency 0.9).

### 7.4 Rendering and multithreading

`renderScene` uses `IntStream.range(0, H).parallel()`, so the rows of the
image are traced on all CPU cores through the common fork/join pool. Each
pixel is independent, so no locking is needed. Separate rows write to
separate pixels of the `BufferedImage`.

**Fast preview mode** is used while dragging or orbiting. It traces only one
pixel in every 4×4 block (1/16 of the work) with a single bounce. When the
mouse is released, a full-quality render runs. This keeps the editor
responsive (NFR1).

### 7.5 PNG export

`renderAndSavePNG()` renders at full quality and writes the image with
`ImageIO.write(image, "png", new File("rendered_scene.png"))`. A dialog then
shows the absolute file path, or an error message if saving failed.

### 7.6 Ray Path Demo

`setupRayTracerDemo()` replaces the scene with two spheres and positions the
camera at an angle:

- a **solid red sphere** (reflectivity 0.5) at (−1.4, 0, −4.5)
- a **clear glass sphere** (transparency 0.9, IOR 1.5) at (1.4, 0, −4.5)

`generateDemoRayPaths()` computes 3D line segments from a virtual emitter at
(0, 0.3, −1.5):

- 4 rays to the red sphere: a **primary** ray (yellow) and its **reflection**
  (orange), computed with `R = D − 2(D·N)N`.
- 4 rays to the glass sphere: a **primary** ray (yellow), the **refracted
  inner ray** (cyan) from Snell's law with η = 1/1.5, and the **exit ray**
  (magenta) refracted again with η = 1.5/1.

During rendering, each segment endpoint is projected to the screen with
`projectToScreen()`, the inverse of the camera mapping in §4.2. The
segments are drawn with anti-aliased `Graphics2D` lines along with a color
legend. The segments are true 3D geometry, so orbiting the camera shows the
rays from any angle.

### 7.7 GUI construction

The sidebar is a `BoxLayout` `JPanel` inside a `JScrollPane` and contains:

1. Color chooser button (`JColorChooser`)
2. Transparency slider (0–100%, labeled "Opaque" or "Clear Glass" at the ends)
3. Palette buttons: Drag Sphere, Drag Cube, Drag Pyramid
4. X, Y, Z text fields and **+ Add at Coordinates**
5. Selected-object label and a `JList` of all objects with their positions
6. **Delete Selected**, **RENDER SCENE (Save PNG)**, **Reset Camera**,
   **Clear All**, **Quit**
7. **RAY TRACER DEMO (Rays Path)**

The viewport is a `JLabel` that displays the rendered `BufferedImage` as an
`ImageIcon`. A key binding on the root pane maps **Q** to quit.

## 8. User Interface

![GUI scene editor](docs/images/gui_scene_editor.png)

*Figure 1. The main window. The sidebar (left) holds the scene controls, and
the viewport (right) shows the live render. This scene has three cubes and
two spheres at different transparency levels.*

Typical workflow:

1. Pick a color and transparency.
2. Press a **Drag** button and drop the shape into the viewport.
3. Drag objects to arrange them, and middle-drag to view the scene from
   other angles.
4. Select any object to change its color, transparency or position later.
5. Click **RENDER SCENE (Save PNG)** to export.

## 9. Results

### 9.1 Rendered scene

![Rendered scene](docs/images/rendered_scene.png)

*Figure 2. The exported 800×600 `rendered_scene.png`.*

Effects visible in Figure 2:

- **Reflection:** the pink and cyan spheres mirror the checkered floor, and
  the floor reflects all objects.
- **Transparency and refraction:** the blue cubes are partly see-through.
  The floor pattern seen through the cyan sphere is bent by refraction.
- **Shadows:** the objects cast shadows on the floor in the direction away
  from the light at (5, 8, 0).
- **Perspective:** the checkerboard shrinks toward the horizon.
- **Sky gradient:** rays that hit nothing get a vertical blue gradient.

### 9.2 Ray Path Demo

![Ray path demo](docs/images/ray_path_demo.png)

*Figure 3. Ray Path Demo with the camera orbited. The reflective red sphere
(left) and the 88%-transparent glass sphere (right).*

- **Yellow** primary rays leave the emitter and hit both spheres.
- At the red sphere, **orange** reflected rays leave at the mirror angle about
  each surface normal, spreading out because of the sphere's curvature.
- At the glass sphere, the rays bend toward the normal on entry (**cyan**),
  as Snell's law predicts for n₂ > n₁. They bend again on exit (**magenta**).
- The floor and the other sphere are visible in each sphere's surface,
  which shows the recursive reflection.

### 9.3 Feature checklist

| Feature                                 | Status |
|-----------------------------------------|--------|
| Sphere, cube, pyramid, plane primitives | Done   |
| Diffuse shading + ambient light         | Done   |
| Hard shadows                            | Done   |
| Recursive reflection                    | Done   |
| Refraction + total internal reflection  | Done   |
| Drag-and-drop editing, selection, delete | Done  |
| Color + transparency per object         | Done   |
| 360° orbit + zoom camera                | Done   |
| Multithreaded rendering + fast preview  | Done   |
| PNG export                              | Done   |
| Ray path visualization                  | Done   |

## 10. Testing

The project was tested manually through the GUI. Fill in the **Result** column (Pass/Fail) after running each test case.

| # | Test case | Steps | Expected result | Result |
|---|-----------|-------|-----------------|--------|
| 1 | Launch | Run `java RayTracer_with_GUI` | Window opens with a default sphere, cube and pyramid |        |
| 2 | Drag-spawn | Press **Drag Cube**, drop it in the viewport | A cube appears at the drop point and is listed |        |
| 3 | Coordinates | Enter (1.3, 2.16, −7.2) → **Add** | A sphere appears at that position |        |
| 4 | Invalid input | Enter `abc` in X → **Add** | "Please enter valid numeric coordinates" error |        |
| 5 | Select | Click an object | Label, coordinates, color and slider update |        |
| 6 | Move | Drag an object | It follows the cursor and the coordinates update |        |
| 7 | Depth move | Scroll over an object | It moves toward or away from the camera |        |
| 8 | Transparency | Select an object, set slider to 88% | The object becomes see-through and shows refraction |        |
| 9 | Color | Select an object, choose a new color | The object is re-rendered in the new color |        |
| 10 | Delete (no selection) | Click **Delete Selected** with nothing selected | Information dialog |        |
| 11 | Delete | Select an object → **Delete Selected** | It is removed from the scene and the list |        |
| 12 | Orbit | Middle-drag / Alt+left-drag | The camera orbits and pitch stays within limits |        |
| 13 | Zoom | Scroll on the background | Camera distance changes (1.5–15) |        |
| 14 | Reset camera | **Reset Camera (0°)** | The view returns to the default |        |
| 15 | Export | **RENDER SCENE (Save PNG)** | `rendered_scene.png` is written and its path shown |        |
| 16 | Clear | **Clear All** | Only the floor remains |        |
| 17 | Demo | **RAY TRACER DEMO** | Two spheres with colored ray paths and a legend |        |
| 18 | Demo orbit | Middle-drag in demo mode | The rays stay attached to the spheres in 3D |        |
| 19 | Quit | Press **Q** | The application exits |        |

**Visual correctness checks:**

- The reflected rays in the demo obey *angle of incidence = angle of
  reflection* with respect to the sphere normal.
- Refracted rays bend toward the normal when entering glass and away from it
  when leaving, as expected for IOR 1.5.
- Shadows point away from the light source.

## 11. Performance

| Setting | Pixels traced | Max bounces | Used when |
|---------|---------------|-------------|-----------|
| Full render | 800 × 600 = 480,000 | 4 | Release mouse, render button, color change |
| Fast preview | 200 × 150 = 30,000 (4×4 blocks) | 1 | Dragging, orbiting, transparency slider |

Factors that affect speed:

- **Linear object search:** every ray is tested against every object, so the
  cost per ray grows as O(n) with the number of objects.
- **Ray tree growth:** an object that is both reflective and transparent
  spawns two secondary rays per hit. In the worst case this is up to 2⁴
  rays per pixel at depth 4.
- **Parallelism:** row-level parallelism scales close to linearly with the
  number of CPU cores, because pixels are independent.
- **Allocation:** `Vector3` creates a new object per operation. The JVM's
  escape analysis and fast young-generation garbage collection keep this
  manageable at this resolution.

## 12. Limitations

1. **One point light** with hard shadows. There are no soft shadows or area
   lights.
2. **No specular highlights** in the GUI version (the `src/` version has
   Blinn-Phong highlights).
3. **No anti-aliasing** in the GUI renderer (1 sample per pixel), so object
   edges and distant checkerboard squares are jagged. This is visible near
   the horizon in Figure 2.
4. **No Fresnel term:** the reflection and refraction weights are fixed per
   object instead of depending on the viewing angle.
5. **Fixed orientation and size:** cubes are axis-aligned, and objects cannot
   be rotated or resized from the GUI.
6. **Add at Coordinates always adds a sphere.**
7. **The demo ray paths are illustrative:** the inner (cyan) segment uses a
   fixed length of 1.8 instead of an exact second intersection with the
   sphere. The directions follow Snell's law, but the exit point is an
   approximation.
8. **Transparent shadows are binary:** an object either blocks light
   (transparency < 50%) or does not. Shadows are not tinted or partial.
9. **No acceleration structure,** so large scenes render slowly.

## 13. Future Enhancements

- Schlick's approximation for angle-dependent Fresnel reflection and
  refraction.
- Blinn-Phong specular highlights and multiple colored lights.
- Supersampling or adaptive anti-aliasing.
- Soft shadows from area lights, and colored shadows from tinted glass.
- Object rotation, scaling, a per-object IOR slider, and textures.
- Triangle-mesh import (OBJ) with a BVH for fast intersection.
- Saving and loading scenes (JSON).
- An exact ray-path demo that traces each segment with the real
  intersection code.
- Path tracing (global illumination) as an optional high-quality mode.

## 14. Conclusion

The project shows that a complete ray tracer with reflection, refraction and
shadows can be built from first principles in standard Java. Each part
follows from the mathematics: ray-sphere quadratics, the slab method,
Möller–Trumbore triangle tests, Lambert's cosine law, the reflection formula
and Snell's law.

The Swing interface makes the renderer interactive. Users can build scenes,
change materials and orbit the camera, while fast preview and parallel
rendering keep the interface responsive. The Ray Path Demo shows the rays
the algorithm follows, so the project also works as a learning aid.

The project also applies object-oriented design: an abstract `SceneObject`
with polymorphic subclasses makes the engine easy to extend. It also uses
Java concurrency through parallel streams, event-driven GUI programming and
image file output. These are the main topics of a Java programming course.

## 15. References

1. Whitted, T. (1980). *An Improved Illumination Model for Shaded Display.*
   Communications of the ACM, 23(6), 343–349.
2. Shirley, P., Black, T. D., & Hollasch, S. *Ray Tracing in One Weekend*
   (book series). https://raytracing.github.io/
3. Pharr, M., Jakob, W., & Humphreys, G. *Physically Based Rendering: From
   Theory to Implementation* (4th ed.). MIT Press. https://pbr-book.org/
4. Möller, T., & Trumbore, B. (1997). *Fast, Minimum Storage Ray-Triangle
   Intersection.* Journal of Graphics Tools, 2(1), 21–28.
5. Williams, A., Barrus, S., Morley, R. K., & Shirley, P. (2005). *An
   Efficient and Robust Ray-Box Intersection Algorithm.* Journal of Graphics
   Tools, 10(1).
6. Scratchapixel. *Introduction to Ray Tracing.* https://www.scratchapixel.com/
7. Oracle. *Java SE API Documentation: javax.swing, java.awt,
   javax.imageio, java.util.stream.*

---

## Appendix A: Key Code Excerpts

### A.1 Ray-sphere intersection

```java
double intersect(Ray ray) {
    Vector3 oc = ray.origin.sub(center);
    double b = oc.dot(ray.direction);
    double c = oc.dot(oc) - radius * radius;
    double discriminant = b * b - c;
    if (discriminant < 0) return -1;
    double t = -b - Math.sqrt(discriminant);
    return t > 0.001 ? t : -b + Math.sqrt(discriminant);
}
```

### A.2 Reflection

```java
double dot = ray.direction.dot(normal);
Vector3 reflectDir = ray.direction.sub(normal.mult(2 * dot)).normalize();
Ray reflectRay = new Ray(hitPoint.add(normal.mult(0.001)), reflectDir);
Vector3 reflectedColor = trace(reflectRay, objects, lightPos, depth + 1, maxDepth);
localColor = localColor.mult(1.0 - hit.obj.reflectivity)
                       .add(reflectedColor.mult(hit.obj.reflectivity));
```

### A.3 Refraction with Snell's law

```java
double n1 = 1.0, n2 = hit.obj.ior;
Vector3 N = normal;
double cosi = -ray.direction.dot(N);
if (cosi < 0) {               // ray is exiting the object
    cosi = -cosi;
    N = N.mult(-1);
    double temp = n1; n1 = n2; n2 = temp;
}
double eta = n1 / n2;
double k = 1.0 - eta * eta * (1.0 - cosi * cosi);
if (k >= 0) {
    Vector3 refrDir = ray.direction.mult(eta)
                         .add(N.mult(eta * cosi - Math.sqrt(k))).normalize();
    // trace refracted ray ...
} else {
    // total internal reflection ...
}
```

### A.4 Parallel render loop

```java
IntStream.range(0, RENDER_HEIGHT / step).parallel().forEach(sy -> {
    int y = sy * step;
    for (int sx = 0; sx < RENDER_WIDTH / step; sx++) {
        int x = sx * step;
        Ray ray = getRayFromPixel(x, y);
        Vector3 finalColor = trace(ray, sceneObjects, lightPos, 1, maxDepth);
        // clamp, convert to RGB, write step×step block ...
    }
});
```

## Appendix B: Build and Run

```bash
cd demo_ray
javac -encoding UTF-8 RayTracer_with_GUI.java
java RayTracer_with_GUI
```

## Appendix C: Project Files

| File | Description |
|------|-------------|
| `demo_ray/RayTracer_with_GUI.java` | Final interactive ray tracer (this report) |
| `demo_ray/RayTracer2.java` | Console ray tracer with spheres, plane and reflections |
| `demo_ray/SimpleRayTracer.java` | First prototype: a single shaded sphere |
| `src/*.java` | Modular ray tracer with Blinn-Phong shading, multiple lights and anti-aliasing |
| `Ray_Tracing_Presentation.pptx` | Project presentation slides |
| `rendered_scene.png` | Exported render from the GUI |
| `output.png` | Output of the `src/` ray tracer |
| `docs/images/` | Screenshots used in the README and this report |
