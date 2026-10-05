package viewpoint.world;

import org.joml.Vector3f;

/** Test double: a 2:1 isometric projection like the one Viewpoint is assumed to use. */
public final class MeshBuilder {
    static Vector3f TO_ISO_CAMERA = new Vector3f(0.612f, 0.5f, 0.612f);
    static float UV_INSET = 0.001f;

    private MeshBuilder() {}

    static float frameX(Vector3f p) {
        return 64f * (p.x - p.z);
    }

    static float frameY(Vector3f p) {
        return 32f * (p.x + p.z) - 78.38f * p.y;
    }
}
