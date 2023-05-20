package edu.cornell.gdiac.render;

import com.badlogic.gdx.math.*;

/**
 * This class/struct is rendering information for a 2d sprite batch vertex.
 *
 * The class is intended to be used as a struct.  This struct has the basic
 * rendering information required by a {@link SpriteBatch} for rendering.
 *
 * Note that the exact meaning of these attributes can vary depending upon
 * the current drawing mode in the sprite batch.  For example, if the color
 * is a gradient, rather than a pure color, then the color attribute holds
 * the texture coordinates (plus additional modulation factors) for that
 * gradient, and not a real color value.
 */
public class CUSpriteVertex {
    /** The vertex position */
    public Vector2 position;
    /** The vertex color */
    public int color;
    /** The vertex texture coordinate */
    public Vector2 texcoord;
    /** The vertex gradient coordinate */
    public Vector2 gradcoord;

    /** The memory offset of the vertex position (a GL_FLOAT) in bytes */
    public static int positionOffset(int size)   { return 4 * size; }
    /** The memory offset of the vertex color (a GL_UNSIGNED_BYTE) in bytes */
    public static int colorOffset(int size)      { return size;}
    /** The memory offset of the vertex texture coordinate (a GL_FLOAT) in bytes */
    public static int texcoordOffset(int size)   { return 4 * size;  }
    /** The memory offset of the vertex texture coordinate (a GL_FLOAT) in bytes */
    public static int gradcoordOffset(int size)   { return 4 * size;  }

    public CUSpriteVertex() {}
};
