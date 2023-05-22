package edu.cornell.gdiac.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;

/**
 * The class Spritebatch does support a basic stencil effects.  In order to
 * support SVG files, these effects became fairly elaborate, as they are
 * splitting the stencil space in half, and coordinating between the two
 * halves. Therefore, we decided to pull the functionality out of Spritebatch
 * into its own module.
 */
public class CUStencilEffect {

    /** Neither stencil effect buffer */
    public static final int STENCIL_NONE = 0;
    /** Lower buffer */
    public static final int STENCIL_LOWER = 1;
    /** Upper buffer */
    public static final int STENCIL_UPPER = 2;
    /** Both buffers */
    public static final int STENCIL_BOTH = 3;

    /**
     * Clears the stencil buffer specified
     *
     * @param buffer    The stencil buffer (lower, upper, both)
     */
    public static void clearBuffer(int buffer) {
        GL30 gl = Gdx.gl30;
        switch (buffer) {
            case STENCIL_NONE:
                return;
            case STENCIL_LOWER:
                gl.glStencilMask(0xf0);
                gl.glClear(gl.GL_STENCIL_BUFFER_BIT);
                gl.glStencilMask(0xff);
                return;
            case STENCIL_UPPER:
                gl.glStencilMask(0x0f);
                gl.glClear(gl.GL_STENCIL_BUFFER_BIT);
                gl.glStencilMask(0xff);
                return;
            case STENCIL_BOTH:
                gl.glStencilMask(0xff);
                gl.glClear(gl.GL_STENCIL_BUFFER_BIT);
                return;
        }
    }

    /**
     * Configures the OpenGL settings to apply the given effect.
     *
     * @param effect    The stencil effect
     */
    public static void applyEffect(Effect effect) {
        GL30 gl = Gdx.gl30;
        switch(effect) {
            case NATIVE:
                // Nothing more to do
                break;
            case NONE:
                gl.glDisable(gl.GL_STENCIL_TEST);
                gl.glColorMask(true, true, true, true);
                break;
            case CLIP:
            case CLIP_JOIN:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xff);
                gl.glStencilFunc(gl.GL_NOTEQUAL, 0x00, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_KEEP);
                gl.glColorMask(true, true, true, true);
                break;
            case MASK:
            case MASK_JOIN:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xff);
                gl.glStencilFunc(gl.GL_EQUAL, 0x00, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_KEEP);
                gl.glColorMask(true, true, true, true);
                break;
            case FILL:
            case FILL_JOIN:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xff);
                gl.glStencilFunc(gl.GL_NOTEQUAL, 0x00, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_ZERO);
                gl.glColorMask(true, true, true, true);
                break;
            case WIPE:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xff);
                gl.glStencilFunc(gl.GL_ALWAYS, 0x00, 0xff);
                gl.glStencilOp(gl.GL_ZERO, gl.GL_ZERO, gl.GL_ZERO);
                gl.glColorMask(false, false, false, false);
                break;
            case STAMP:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xff);
                gl.glStencilFunc(gl.GL_ALWAYS, 0x00, 0xff);
                gl.glStencilOpSeparate(gl.GL_FRONT, gl.GL_KEEP, gl.GL_KEEP, gl.GL_INCR_WRAP);
                gl.glStencilOpSeparate(gl.GL_BACK, gl.GL_KEEP, gl.GL_KEEP, gl.GL_DECR_WRAP);
                gl.glColorMask(false, false, false, false);
                break;
            case CARVE:
            case CARVE_NONE:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xf0);
                gl.glStencilFunc(gl.GL_EQUAL, 0x00, 0xf0);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(false, false, false, false);
                break;
            case CLAMP:
            case CLAMP_NONE:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xf0);
                gl.glStencilFunc(gl.GL_EQUAL, 0x00, 0xf0);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(true, true, true, true);
                break;
            case NONE_CLIP:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0x0f);
                gl.glStencilFunc(gl.GL_NOTEQUAL, 0x00, 0x0f);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_KEEP);
                gl.glColorMask(true, true, true, true);
                break;
            case NONE_MASK:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0x0f);
                gl.glStencilFunc(gl.GL_EQUAL, 0x00, 0x0f);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_KEEP);
                gl.glColorMask(true, true, true, true);
                break;
            case NONE_FILL:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0x0f);
                gl.glStencilFunc(gl.GL_NOTEQUAL, 0x00, 0x0f);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_ZERO);
                gl.glColorMask(true, true, true, true);
                break;
            case NONE_WIPE:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0x0f);
                gl.glStencilFunc(gl.GL_ALWAYS, 0x00, 0x0f);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_ZERO);
                gl.glColorMask(false, false, false, false);
                break;
            case NONE_STAMP:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0x0f);
                gl.glStencilFunc(gl.GL_ALWAYS, 0x00, 0x0f);
                gl.glStencilOpSeparate(gl.GL_FRONT, gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glStencilOpSeparate(gl.GL_BACK, gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(false, false, false, false);
                break;
            case NONE_CARVE:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0x0f);
                gl.glStencilFunc(gl.GL_EQUAL, 0x00, 0x0f);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(false, false, false, false);
                break;
            case NONE_CLAMP:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0x0f);
                gl.glStencilFunc(gl.GL_EQUAL, 0x00, 0x0f);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(true, true, true, true);
                break;
            case CLIP_NONE:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xff);
                gl.glStencilFunc(gl.GL_NOTEQUAL, 0x00, 0xf0);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_KEEP);
                gl.glColorMask(true, true, true, true);
                break;
            case CLIP_MEET:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xff);
                gl.glStencilFunc(gl.GL_EQUAL, 0xff, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_KEEP);
                gl.glColorMask(true, true, true, true);
                break;
            case CLIP_MASK:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xff);
                gl.glStencilFunc(gl.GL_EQUAL, 0xf0, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_KEEP);
                gl.glColorMask(true, true, true, true);
                break;
            case CLIP_FILL:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0x0f);
                gl.glStencilFunc(gl.GL_NOTEQUAL, 0x00, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_ZERO);
                gl.glColorMask(true, true, true, true);
                break;
            case CLIP_WIPE:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0x0f);
                gl.glStencilFunc(gl.GL_NOTEQUAL, 0x00, 0xf0);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_ZERO);
                gl.glColorMask(false, false, false, false);
                break;
            case CLIP_STAMP:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0x0f);
                gl.glStencilFunc(gl.GL_NOTEQUAL, 0x00, 0xf0);
                gl.glStencilOpSeparate(gl.GL_FRONT, gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glStencilOpSeparate(gl.GL_BACK, gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(false, false, false, false);
                break;
            case CLIP_CARVE:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0x0f);
                gl.glStencilFunc(gl.GL_EQUAL, 0xf0, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(false, false, false, false);
                break;
            case CLIP_CLAMP:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0x0f);
                gl.glStencilFunc(gl.GL_EQUAL, 0xf0, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(true, true, true, true);
                break;
            case MASK_NONE:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xff);
                gl.glStencilFunc(gl.GL_EQUAL, 0x00, 0xf0);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_KEEP);
                gl.glColorMask(true, true, true, true);
                break;
            case MASK_MEET:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xff);
                gl.glStencilFunc(gl.GL_NOTEQUAL, 0xff, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_KEEP);
                gl.glColorMask(true, true, true, true);
                break;
            case MASK_CLIP:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xff);
                gl.glStencilFunc(gl.GL_EQUAL, 0x0f, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_KEEP);
                gl.glColorMask(true, true, true, true);
                break;
            case MASK_FILL:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0x0f);
                gl.glStencilFunc(gl.GL_EQUAL, 0x0f, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_ZERO);
                gl.glColorMask(true, true, true, true);
                break;
            case MASK_WIPE:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0x0f);
                gl.glStencilFunc(gl.GL_EQUAL, 0x00, 0xf0);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_ZERO);
                gl.glColorMask(false, false, false, false);
                break;
            case MASK_STAMP:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0x0f);
                gl.glStencilFunc(gl.GL_EQUAL, 0x00, 0xf0);
                gl.glStencilOpSeparate(gl.GL_FRONT, gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glStencilOpSeparate(gl.GL_BACK, gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(false, false, false, false);
                break;
            case MASK_CARVE:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0x0f);
                gl.glStencilFunc(gl.GL_EQUAL, 0x0, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(false, false, false, false);
                break;
            case MASK_CLAMP:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0x0f);
                gl.glStencilFunc(gl.GL_EQUAL, 0x00, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(true, true, true, true);
                break;
            case FILL_NONE:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xf0);
                gl.glStencilFunc(gl.GL_NOTEQUAL, 0x00, 0xf0);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_ZERO);
                gl.glColorMask(true, true, true, true);
                break;
            case FILL_MEET:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xf0);
                gl.glStencilFunc(gl.GL_EQUAL, 0xff, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_ZERO);
                gl.glColorMask(true, true, true, true);
                break;
            case FILL_CLIP:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xf0);
                gl.glStencilFunc(gl.GL_EQUAL, 0xff, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_ZERO);
                gl.glColorMask(true, true, true, true);
                break;
            case FILL_MASK:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xf0);
                gl.glStencilFunc(gl.GL_EQUAL, 0xf0, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_ZERO);
                gl.glColorMask(true, true, true, true);
                break;
            case WIPE_NONE:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xf0);
                gl.glStencilFunc(gl.GL_ALWAYS, 0x00, 0xf0);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_ZERO);
                gl.glColorMask(false, false, false, false);
                break;
            case WIPE_CLIP:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xf0);
                gl.glStencilFunc(gl.GL_NOTEQUAL, 0x00, 0x0f);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_ZERO);
                gl.glColorMask(false, false, false, false);
                break;
            case WIPE_MASK:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xf0);
                gl.glStencilFunc(gl.GL_EQUAL, 0x00, 0x0f);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_ZERO);
                gl.glColorMask(false, false, false, false);
                break;
            case STAMP_NONE:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xf0);
                gl.glStencilFunc(gl.GL_ALWAYS, 0x00, 0x0f);
                gl.glStencilOpSeparate(gl.GL_FRONT, gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glStencilOpSeparate(gl.GL_BACK, gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(false, false, false, false);
                break;
            case STAMP_CLIP:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xf0);
                gl.glStencilFunc(gl.GL_NOTEQUAL, 0x00, 0x0f);
                gl.glStencilOpSeparate(gl.GL_FRONT, gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glStencilOpSeparate(gl.GL_BACK, gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(false, false, false, false);
                break;
            case STAMP_MASK:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xf0);
                gl.glStencilFunc(gl.GL_EQUAL, 0x00, 0x0f);
                gl.glStencilOpSeparate(gl.GL_FRONT, gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glStencilOpSeparate(gl.GL_BACK, gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(false, false, false, false);
                break;
            case STAMP_BOTH:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xff);
                gl.glStencilFunc(gl.GL_ALWAYS, 0x00, 0xff);
                gl.glStencilOpSeparate(gl.GL_FRONT, gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glStencilOpSeparate(gl.GL_BACK, gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(false, false, false, false);
                break;
            case CARVE_CLIP:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xf0);
                gl.glStencilFunc(gl.GL_NOTEQUAL, 0x0f, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(false, false, false, false);
                break;
            case CARVE_MASK:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xf0);
                gl.glStencilFunc(gl.GL_EQUAL, 0x0f, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(false, false, false, false);
                break;
            case CARVE_BOTH:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xff);
                gl.glStencilFunc(gl.GL_EQUAL, 0x00, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(false, false, false, false);
                break;
            case CLAMP_CLIP:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xf0);
                gl.glStencilFunc(gl.GL_EQUAL, 0x0f, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl.glColorMask(true, true, true, true);
                break;
            case CLAMP_MASK:
                gl.glEnable(gl.GL_STENCIL_TEST);
                gl.glStencilMask(0xf0);
                gl.glStencilFunc(gl.GL_EQUAL, 0x00, 0xff);
                gl.glStencilOp(gl.GL_KEEP, gl.GL_KEEP, gl.GL_INVERT);
                gl. glColorMask(true, true, true, true);
                break;
        }
    }

    public enum Effect {
        /**
         * Differs the to the existing OpenGL stencil settings. (DEFAULT)
         * <p>
         * This effect neither enables nor disables the stencil buffer. Instead
         * it uses the existing OpenGL settings.  This is the effect that you
         * should use when you need to manipulate the stencil buffer directly.
         */
        NATIVE,

        /**
         * Disables any stencil effects.
         *
         * This effect directs a {@link CUSpriteBatch} to ignore the stencil buffer
         * (both halves) when drawing.  However, it does not clear the contents
         * of the stencil buffer.  To clear the stencil buffer, you will need to
         * call {@link CUStencilEffect#clearBuffer}.
         */
        NONE,

        /**
         * Restrict all drawing to the unified stencil region.
         *
         * In order for this effect to do anything, you must have created a
         * stencil region with {@link Effect#STAMP} or one of its variants.
         * This effect will process the drawing commands normally, but restrict all
         * drawing to the stencil region. This can be used to quickly draw
         * non-convex shapes by making a stencil and drawing a rectangle over
         * the stencil.
         *
         * This effect is the same as {@link Effect#CLIP_JOIN} in that it respects
         * the union of the two halves of the stencil buffer.
         */
        CLIP,

        /**
         * Prohibits all drawing to the unified stencil region.
         *
         * In order for this effect to do anything, you must have created a
         * stencil region with {@link Effect#STAMP} or one of its variants.
         * This effect will process the drawing commands normally, but reject any
         * attempts to draw to the stencil region. This can be used to quickly
         * draw shape borders on top of a solid shape.
         *
         * This effect is the same as {@link Effect#MASK_JOIN} in that it respects
         * the union of the two halves of the stencil buffer.
         */
        MASK,

        /**
         * Restrict all drawing to the unified stencil region.
         *
         * In order for this effect to do anything, you must have created a
         * stencil region with {@link Effect#STAMP} or one of its variants.
         * This effect will process the drawing commands normally, but restrict all
         * drawing to the stencil region. This can be used to quickly draw
         * non-convex shapes by making a stencil and drawing a rectangle over
         * the stencil.
         *
         * This effect is different from {@link Effect#CLIP} in that it will
         * zero out the pixels it draws in the stencil buffer, effectively removing
         * them from the stencil region. In many applications, this is a fast
         * way to clear the stencil buffer once it is no longer needed.
         *
         * This effect is the same as {@link Effect#FILL_JOIN} in that it respects
         * the union of the two halves of the stencil buffer.
         */
        FILL,

        /**
         * Erases from the unified stencil region.
         *
         * This effect will not draw anything to the screen. Instead, it will
         * only draw to the stencil buffer directly. Any pixel drawn will be
         * zeroed in the buffer, removing it from the stencil region. The
         * effect {@link Effect#FILL} is a combination of this and
         * {@link Effect#CLIP}. Again, this is a potential optimization
         * for clearing the stencil buffer. However, on most tiled-based GPUs,
         * it is probably faster to simply clear the whole buffer.
         */
        WIPE,

        /**
         * Adds a stencil region the unified buffer
         *
         * This effect will not have any immediate visible effects. Instead it
         * creates a stencil region for the effects such as {@link Effect#CLIP},
         * {@link Effect#MASK}, and the like.
         *
         * The shapes are drawn to the stencil buffer using a nonzero fill
         * rule. This has the advantage that (unlike an even-odd fill rule)
         * stamps are additive and can be drawn on top of each other. However,
         * it has the disadvantage that it requires both halves of the stencil
         * buffer to store the stamp (which part of the stamp is in which half
         * is undefined).
         *
         * While this effect implements a nonzero fill rule faithfully, there
         * are technical limitations. The size of the stencil buffer means
         * that more than 256 overlapping polygons of the same orientation
         * will cause unpredictable effects. If this is a problem, use an
         * even odd fill rule instead like {@link Effect#STAMP_NONE}
         * (which has no such limitations).
         */
        STAMP,

        /**
         * Adds a stencil region the lower buffer
         *
         * This effect will not have any immediate visible effects. Instead it
         * creates a stencil region for the effects such as {@link Effect#CLIP},
         * {@link Effect#MASK}, and the like.
         *
         * Like {@link Effect#STAMP}, shapes are drawn to the stencil buffer
         * instead of the screen. But unlike stamp, this effect is always additive.
         * It ignores path orientation, and does not support holes. This allows
         * the effect to implement a nonzero fill rule while using only half
         * of the buffer. This effect is equivalent to {@link Effect#CARVE_NONE}
         * in that it uses only the lower half.
         *
         * The primary application of this effect is to create stencils from
         * extruded paths so that overlapping sections are not drawn twice
         * (which has negative effects on alpha blending).
         */
        CARVE,

        /**
         * Limits drawing so that each pixel is updated once.
         *
         * This effect is a variation of {@link Effect#CARVE} that also draws
         * as it writes to the stencil buffer.  This guarantees that each pixel is
         * updated exactly once. This is used by extruded paths so that
         * overlapping sections are not drawn twice (which has negative
         * effects on alpha blending).
         *
         * This effect is equivalent to {@link Effect#CLAMP_NONE} in that
         * it uses only the lower half.
         */
        CLAMP,

        /**
         * Applies {@link Effect#CLIP} using the upper stencil buffer only.
         *
         * As with {@link Effect#CLIP}, this effect restricts drawing to
         * the stencil region. However, this effect only uses the stencil region
         * present in the upper stencil buffer.
         *
         * This effect is designed to be used with stencil regions created by
         * {@link Effect#NONE_STAMP}. While it can be used by a stencil
         * region created by {@link Effect#STAMP}, the lower stencil buffer
         * is ignored, and hence the results are unpredictable.
         */
        NONE_CLIP,

        /**
         * Applies {@link Effect#MASK} using the upper stencil buffer only.
         *
         * As with {@link Effect#MASK}, this effect prohibits drawing to
         * the stencil region. However, this effect only uses the stencil region
         * present in the upper stencil buffer.
         *
         * This effect is designed to be used with stencil regions created by
         * {@link Effect#NONE_STAMP}. While it can be used by a stencil
         * region created by {@link Effect#STAMP}, the lower stencil buffer
         * is ignored, and hence the results are unpredictable.
         */
        NONE_MASK,

        /**
         * Applies {@link Effect#FILL} using the upper stencil buffer only.
         *
         * As with {@link Effect#FILL}, this effect limits drawing to
         * the stencil region. However, this effect only uses the stencil region
         * present in the upper stencil buffer.  It also only zeroes out the upper
         * stencil buffer.
         *
         * This effect is designed to be used with stencil regions created by
         * {@link Effect#NONE_STAMP}. While it can be used by a stencil
         * region created by {@link Effect#STAMP}, the lower stencil buffer
         * is ignored, and hence the results are unpredictable.
         */
        NONE_FILL,

        /**
         * Applies {@link Effect#WIPE} using the upper stencil buffer only.
         *
         * As with {@link Effect#WIPE}, this effect zeroes out the stencil
         * region, erasing parts of it. However, its effects are limited to the upper
         * stencil region.
         *
         * This effect is designed to be used with stencil regions created by
         * {@link Effect#NONE_STAMP}. While it can be used by a stencil
         * region created by {@link Effect#STAMP}, the lower stencil buffer
         * is ignored, and hence the results are unpredictable.
         */
        NONE_WIPE,

        /**
         * Adds a stencil region to the upper buffer
         *
         * This effect will not have any immediate visible effect on the screen
         * screen. Instead, it creates a stencil region for the effects such as
         * {@link Effect#CLIP}, {@link Effect#MASK}, and the like.
         *
         * Unlike {@link Effect#STAMP}, the region created is limited to
         * the upper half of the stencil buffer. That is because the shapes are
         * drawn to the buffer with an even-odd fill rule (which does not require
         * the full stencil buffer to implement). This has the disadvantage
         * that stamps drawn on top of each other have an "erasing" effect.
         * However, it has the advantage that the this stamp supports a wider
         * array of effects than the simple stamp effect.
         *
         * Use {@link Effect#NONE_CLAMP} if you have an simple stencil with
         * no holes that you wish to write to the upper half of the buffer.
         */
        NONE_STAMP,

        /**
         * Adds a stencil region to the upper buffer
         *
         * This value will not have any immediate visible effect on the screen.
         * Instead, it creates a stencil region for the effects such as
         * {@link Effect#CLIP}, {@link Effect#MASK}, and the like.
         * Like {@link Effect#STAMP}, shapes are drawn to the stencil buffer
         * instead of the screen. But unlike stamp, this effect is always additive.
         * It ignores path orientation, and does not support holes. This allows
         * the effect to implement a nonzero fill rule while using only the
         * upper half of the buffer.
         *
         * The primary application of this effect is to create stencils from
         * extruded paths so that overlapping sections are not drawn twice
         * (which has negative effects on alpha blending).
         */
        NONE_CARVE,

        /**
         * Uses the upper buffer to limit each pixel to single update.
         *
         * This effect is a variation of {@link Effect#NONE_CARVE} that
         * also draws as it writes to the upper stencil buffer. This guarantees
         * that each pixel is updated exactly once. This is used by extruded paths
         * so that overlapping sections are not drawn twice (which has negative
         * effects on alpha blending).
         */
        NONE_CLAMP,

        /**
         * Restrict all drawing to the unified stencil region.
         *
         * This effect is the same as {@link Effect#CLIP} in that it respects
         * the union of the two halves of the stencil buffer.
         */
        CLIP_JOIN,

        /**
         * Restrict all drawing to the intersecting stencil region.
         *
         * This effect is the same as {@link Effect#CLIP}, except that
         * it limits drawing to the intersection of the stencil regions in the
         * two halves of the stencil buffer. If a unified stencil region was
         * created by {@link Effect#STAMP}, then the results of this
         * effect are unpredictable.
         */
        CLIP_MEET,

        /**
         * Applies {@link Effect#CLIP} using the lower stencil buffer only.
         *
         * As with {@link Effect#CLIP}, this effect restricts drawing to
         * the stencil region. However, this effect only uses the stencil region
         * present in the lower stencil buffer.
         *
         * This effect is designed to be used with stencil regions created by
         * {@link Effect#NONE_STAMP}. While it can be used by a stencil
         * region created by {@link Effect#STAMP}, the lower stencil buffer
         * is ignored, and hence the results are unpredictable.
         */
        CLIP_NONE,

        /**
         * Applies a lower buffer {@link Effect#CLIP} with an upper {@link Effect#MASK}.
         *
         * This command restricts drawing to the stencil region in the lower
         * buffer while prohibiting any drawing to the stencil region in the
         * upper buffer. If this effect is applied to a unified stencil region
         * created by {@link Effect#STAMP}, then the results are unpredictable.
         */
        CLIP_MASK,

        /**
         * Applies a lower buffer {@link Effect#CLIP} with an upper {@link Effect#FILL}.
         *
         * This command restricts drawing to the stencil region in the unified
         * stencil region of the two buffers. However, it only zeroes pixels in
         * the stencil region of the upper buffer; the lower buffer is untouched.
         * If this effect is applied to a unified stencil region created by
         * {@link Effect#STAMP}, then the results are unpredictable.
         */
        CLIP_FILL,

        /**
         * Applies a lower buffer {@link Effect#CLIP} with an upper {@link Effect#WIPE}.
         *
         * As with {@link Effect#WIPE}, this command does not do any drawing
         * on screen. Instead, it zeroes out the upper stencil buffer. However, it is
         * clipped by the stencil region in the lower buffer, so that it does not zero
         * out any pixel outside this region. Hence this is a way to erase the lower
         * buffer stencil region from the upper buffer stencil region.
         */
        CLIP_WIPE,

        /**
         * Applies a lower buffer {@link Effect#CLIP} with an upper {@link Effect#STAMP}.
         *
         * As with {@link Effect#NONE_CLAMP}, this writes a shape to the upper
         * stencil buffer using an even-odd fill rule. This means that adding a shape
         * on top of existing shape has an erasing effect. However, it also restricts
         * its operation to the stencil region in the lower stencil buffer. Note
         * that if a pixel is clipped while drawing, it will not be added the
         * stencil region in the upper buffer.
         */
        CLIP_STAMP,

        /**
         * Applies a lower buffer {@link Effect#CLIP} with an upper {@link Effect#CARVE}.
         *
         * As with {@link Effect#NONE_CARVE}, this writes an additive shape
         * to the upper stencil buffer. However, it also restricts its operation to
         * the stencil region in the lower stencil buffer. Note that if a pixel
         * is clipped while drawing, it will not be added the stencil region in
         * the upper buffer. Hence this is a way to copy the lower buffer stencil
         * region into the upper buffer.
         */
        CLIP_CARVE,

        /**
         * Applies a lower buffer {@link Effect#CLIP} with an upper {@link Effect#CLAMP}.
         *
         * As with {@link Effect#NONE_CLAMP}, this draws a nonoverlapping
         * shape using the upper stencil buffer. However, it also restricts its
         * operation to the stencil region in the lower stencil buffer. Note that
         * if a pixel is clipped while drawing, it will not be added the stencil
         * region in the upper buffer.
         */
        CLIP_CLAMP,

        /**
         * Prohibits all drawing to the unified stencil region.
         *
         * This effect is the same as {@link Effect#MASK} in that it respects
         * the union of the two halves of the stencil buffer.
         */
        MASK_JOIN,

        /**
         * Prohibits all drawing to the intersecting stencil region.
         *
         * This effect is the same as {@link Effect#MASK}, except that
         * it limits drawing to the intersection of the stencil regions in the
         * two halves of the stencil buffer. If a unified stencil region was
         * created by {@link Effect#STAMP}, then the results of this effect
         * are unpredictable.
         */
        MASK_MEET,

        /**
         * Applies {@link Effect#MASK} using the lower stencil buffer only.
         *
         * As with {@link Effect#MASK}, this effect prohibits drawing to
         * the stencil region. However, this effect only uses the stencil region
         * present in the lower stencil buffer.
         *
         * This effect is designed to be used with stencil regions created by
         * {@link Effect#STAMP_NONE}. While it can be used by a stencil
         * region created by {@link Effect#STAMP}, the upper stencil buffer
         * is ignored, and hence the results are unpredictable.
         */
        MASK_NONE,

        /**
         * Applies a lower buffer {@link Effect#MASK} with an upper {@link Effect#CLIP}.
         *
         * This command restricts drawing to the stencil region in the upper
         * buffer while prohibiting any drawing to the stencil region in the
         * lower buffer. If this effect is applied to a unified stencil region
         * created by {@link Effect#STAMP}, then the results are unpredictable.
         */
        MASK_CLIP,

        /**
         * Applies a lower buffer {@link Effect#MASK} with an upper {@link Effect#FILL}.
         *
         * This command restricts drawing to the stencil region in the upper
         * buffer while prohibiting any drawing to the stencil region in the
         * lower buffer. However, it only zeroes the stencil region in the
         * upper buffer; the lower buffer is untouched. In addition, it will
         * only zero those pixels that were drawn.
         *
         * If this effect is applied to a unified stencil region created by
         * {@link Effect#STAMP}, then the results are unpredictable.
         */
        MASK_FILL,

        /**
         * Applies a lower buffer {@link Effect#MASK} with an upper {@link Effect#WIPE}.
         *
         * As with {@link Effect#WIPE}, this command does not do any drawing
         * on screen. Instead, it zeroes out the upper stencil buffer. However, it
         * is masked by the stencil region in the lower buffer, so that it does not
         * zero out any pixel inside this region.
         */
        MASK_WIPE,

        /**
         * Applies a lower buffer {@link Effect#MASK} with an upper {@link Effect#STAMP}.
         *
         * As with {@link Effect#NONE_STAMP}, this writes a shape to the
         * upper stencil buffer using an even-odd fill rule. This means that adding
         * a shape on top of existing shape has an erasing effect. However, it also
         * masks its operation by the stencil region in the lower stencil buffer. Note
         * that if a pixel is masked while drawing, it will not be added the
         * stencil region in the upper buffer.
         */
        MASK_STAMP,

        /**
         * Applies a lower buffer {@link Effect#MASK} with an upper {@link Effect#CARVE}.
         *
         * As with {@link Effect#NONE_CARVE}, this writes an additive shape
         * to the upper stencil buffer. However, it also prohibits any drawing to
         * the stencil region in the lower stencil buffer. Note that if a pixel is
         * masked while drawing, it will not be added the stencil region in
         * the upper buffer.
         */
        MASK_CARVE,

        /**
         * Applies a lower buffer {@link Effect#MASK} with an upper {@link Effect#CLAMP}.
         *
         * As with {@link Effect#NONE_CLAMP}, this draws a nonoverlapping
         * shape using the upper stencil buffer. However, it also prohibits any
         * drawing to the stencil region in the lower stencil buffer. Note that
         * if a pixel is masked while drawing, it will not be added the stencil
         * region in the upper buffer.
         */
        MASK_CLAMP,

        /**
         * Restrict all drawing to the unified stencil region.
         *
         * This effect is the same as {@link Effect#FILL} in that it respects
         * the union of the two halves of the stencil buffer.
         */
        FILL_JOIN,

        /**
         * Restrict all drawing to the intersecting stencil region.
         *
         * This effect is the same as {@link Effect#FILL}, except that it
         * limits drawing to the intersection of the stencil regions in the two
         * halves of the stencil buffer.
         *
         * When zeroing out pixels, this operation zeroes out both halves of
         * the stencil buffer. If a unified stencil region was created by
         * {@link Effect#STAMP}, the results of this effect are unpredictable.
         */
        FILL_MEET,

        /**
         * Applies {@link Effect#FILL} using the lower stencil buffer only.
         *
         * As with {@link Effect#FILL}, this effect restricts drawing to
         * the stencil region. However, this effect only uses the stencil region
         * present in the lower stencil buffer. It also only zeroes the stencil
         * region in this lower buffer.
         *
         * This effect is designed to be used with stencil regions created by
         * {@link Effect#NONE_STAMP}. While it can be used by a stencil
         * region created by {@link Effect#STAMP}, the lower stencil buffer
         * is ignored, and hence the results are unpredictable.
         */
        FILL_NONE,

        /**
         * Applies a lower buffer {@link Effect#FILL} with an upper {@link Effect#MASK}.
         *
         * This command restricts drawing to the stencil region in the lower
         * buffer while prohibiting any drawing to the stencil region in the
         * upper buffer.
         *
         * When zeroing out the stencil region, this part of the effect is only
         * applied to the lower buffer. If this effect is applied to a unified
         * stencil region created by {@link Effect#STAMP}, then the results
         * are unpredictable.
         */
        FILL_MASK,

        /**
         * Applies a lower buffer {@link Effect#FILL} with an upper {@link Effect#CLIP}.
         *
         * This command restricts drawing to the stencil region in the unified
         * stencil region of the two buffers. However, it only zeroes pixels in
         * the stencil region of the lower buffer; the lower buffer is untouched.
         * If this effect is applied to a unified stencil region created by
         * {@link Effect#STAMP}, then the results are unpredictable.
         */
        FILL_CLIP,

        /**
         * Applies {@link Effect#WIPE} using the lower stencil buffer only.
         *
         * As with {@link Effect#WIPE}, this effect zeroes out the stencil
         * region, erasing parts of it. However, its effects are limited to the lower
         * stencil region.
         *
         * This effect is designed to be used with stencil regions created by
         * {@link Effect#NONE_STAMP}. While it can be used by a stencil
         * region created by {@link Effect#STAMP}, the lower stencil buffer
         * is ignored, and hence the results are unpredictable.
         */
        WIPE_NONE,

        /**
         * Applies a lower buffer {@link Effect#WIPE} with an upper {@link Effect#MASK}.
         *
         * This command erases from the stencil region in the lower buffer.
         * However, it limits its erasing to locations that are not masked by
         * the stencil region in the upper buffer. If this effect is applied
         * to a unified stencil region created by {@link Effect#STAMP},
         * the results are unpredictable.
         */
        WIPE_MASK,

        /**
         * Applies a lower buffer {@link Effect#WIPE} with an upper {@link Effect#CLIP}.
         *
         * This command erases from the stencil region in the lower buffer.
         * However, it limits its erasing to locations that are contained in
         * the stencil region in the upper buffer. If this effect is applied
         * to a unified stencil region created by {@link Effect#STAMP},
         * the results are unpredictable.
         */
        WIPE_CLIP,

        /**
         * Adds a stencil region to the lower buffer
         *
         * This effect will not have any immediate visible effect on the screen
         * screen. Instead, it creates a stencil region for the effects such as
         * {@link Effect#CLIP}, {@link Effect#MASK}, and the like.
         *
         * Unlike {@link Effect#STAMP}, the region created is limited to
         * the lower half of the stencil buffer. That is because the shapes are
         * drawn to the buffer with an even-odd fill rule (which does not require
         * the full stencil buffer to implement). This has the disadvantage
         * that stamps drawn on top of each other have an "erasing" effect.
         * However, it has the advantage that the this stamp supports a wider
         * array of effects than the simple stamp effect.
         */
        STAMP_NONE,

        /**
         * Applies a lower buffer {@link Effect#STAMP} with an upper {@link Effect#CLIP}.
         *
         * As with {@link Effect#STAMP_NONE}, this writes a shape to the
         * lower stencil buffer using an even-odd fill rule. This means that adding
         * a shape on top of existing shape has an erasing effect. However, it also
         * restricts its operation to the stencil region in the upper stencil buffer.
         * Note that if a pixel is clipped while drawing, it will not be added the
         * stencil region in the lower buffer.
         */
        STAMP_CLIP,

        /**
         * Applies a lower buffer {@link Effect#STAMP} with an upper {@link Effect#MASK}.
         *
         * As with {@link Effect#STAMP_NONE}, this writes a shape to the lower
         * stencil buffer using an even-odd fill rule. This means that adding a shape
         * on top of existing shape has an erasing effect. However, it also masks
         * its operation by the stencil region in the upper stencil buffer. Note
         * that if a pixel is masked while drawing, it will not be added the
         * stencil region in the lower buffer.
         */
        STAMP_MASK,

        /**
         * Adds a stencil region to both the lower and the upper buffer
         *
         * This effect will not have any immediate visible effect on the screen
         * screen. Instead, it creates a stencil region for the effects such as
         * {@link Effect#CLIP}, {@link Effect#MASK}, and the like.
         *
         * Unlike {@link Effect#STAMP}, the region is create twice and put in
         * both the upper and the lower stencil buffer. That is because the shapes
         * are drawn to the buffer with an even-odd fill rule (which does not require
         * the full stencil buffer to implement). This has the disadvantage that
         * stamps drawn on top of each other have an "erasing" effect. However, it
         * has the advantage that the this stamp supports a wider array of effects
         * than the simple stamp effect.
         *
         * The use of both buffers to provide a greater degree of flexibility.
         */
        STAMP_BOTH,

        /**
         * Adds a stencil region to the lower buffer
         *
         * This effect is equivalent to {@link Effect#CARVE}, since it only uses
         * half of the stencil buffer.
         */
        CARVE_NONE,

        /**
         * Applies a lower buffer {@link Effect#CARVE} with an upper {@link Effect#CLIP}.
         *
         * As with {@link Effect#CARVE_NONE}, this writes an additive shape
         * to the lower stencil buffer. However, it also restricts its operation to
         * the stencil region in the upper stencil buffer. Note that if a pixel
         * is clipped while drawing, it will not be added the stencil region in
         * the lower buffer. Hence this is a way to copy the upper buffer stencil
         * region into the lower buffer.
         */
        CARVE_CLIP,

        /**
         * Applies a lower buffer {@link Effect#CARVE} with an upper {@link Effect#MASK}.
         *
         * As with {@link Effect#CARVE_NONE}, this writes an additive shape
         * to the lower stencil buffer. However, it also prohibits any drawing to
         * the stencil region in the upper stencil buffer. Note that if a pixel is
         * masked while drawing, it will not be added the stencil region in
         * the lower buffer.
         */
        CARVE_MASK,

        /**
         * Adds a stencil region to both the lower and upper buffer
         *
         * This effect is similar to {@link Effect#CARVE}, except that it uses
         * both buffers. This is to give a wider degree of flexibility.
         */
        CARVE_BOTH,

        /**
         * Uses the lower buffer to limit each pixel to single update.
         *
         * This effect is equivalent to {@link Effect#CLAMP}, since it only uses
         * half of the stencil buffer.
         */
        CLAMP_NONE,

        /**
         * Applies a lower buffer {@link Effect#CLAMP} with an upper {@link Effect#CLIP}.
         *
         * As with {@link Effect#CLAMP_NONE}, this draws a nonoverlapping
         * shape using the lower stencil buffer. However, it also restricts its
         * operation to the stencil region in the upper stencil buffer. Note that
         * if a pixel is clipped while drawing, it will not be added the stencil
         * region in the lower buffer.
         */
        CLAMP_CLIP,

        /**
         * Applies a lower buffer {@link Effect#CLAMP} with an upper {@link Effect#MASK}.
         *
         * As with {@link Effect#CLAMP_NONE}, this draws a nonoverlapping
         * shape using the lower stencil buffer. However, it also prohibits any
         * drawing to the stencil region in the upper stencil buffer. Note that
         * if a pixel is masked while drawing, it will not be added the stencil
         * region in the lower buffer.
         */
        CLAMP_MASK
    }
}
