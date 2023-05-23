package edu.cornell.gdiac.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.*;

import com.badlogic.gdx.utils.*;
import edu.cornell.gdiac.math.*;
import edu.cornell.gdiac.render.shaders.SpriteShader;

/** This module provides one-stop shopping for basic 2d graphics.  Despite the
 * name, it is also capable of drawing solid shapes, as well as wireframes.
 * It also has support for color gradients and (rotational) scissor masks.
 *
 * While it is possible to swap out the shader for this class, the shader is
 * very peculiar in how it uses uniforms.  You should study SpriteShader.frag
 * and SpriteShader.vert before making any shader changes to this class.
 */
public class CUSpriteBatch implements Batch {

    /** Array to hold vertex data **/
    final float[] vertices;
    /** Number of numbers in each vertex */
    final int numsInVertex;
    /** Array to hold index data **/
    final short[] indxData;
    /** Index into vertices for where to add the next float */
    int idx = 0;
    /** Whether we are currently drawing */
    boolean drawing = false;
    /** The projection matrix */
    private final Matrix4 projectionMatrix = new Matrix4();

    /** The shader */
    private ShaderProgram shader;
    /** Whether this sprite batch owns the shader */
    private boolean ownsShader;
    /** Color to tint the sprites */
    private final Color color = new Color(1, 1, 1, 1);
    /** The packed color */
    float colorPacked = Color.WHITE_FLOAT_BITS;

    /** The number of vertices drawn in this pass (so far) */
    public int vertTotal;
    /** Number of render calls since the last {@link #begin()}. **/
    public int renderCalls = 0;
    /** Number of rendering calls, ever. Will not be reset unless set manually. **/
    public int totalRenderCalls = 0;

    /** The uniform buffer for this sprite batch */
    private CUUniformBuffer unifbuff;
    /** The vertex buffer for this sprite batch */
    private CUVertexBuffer vertbuff;
    /** The maximum number of vertices **/
    private int vertMax;
    /** The number of vertices in the current mesh **/
    private int vertSize;
    /** The maximum number of indices **/
    private int indxMax;
    /** The number of indices in the current mesh **/
    private int indxSize;

    /** The active drawing context */
    private Context context;
    /** Whether the current context has been used. */
    private boolean inflight;
    /** The drawing context history */
    private PooledLinkedList<Context> history;

    /** The active gradient */
    private CUGradient gradient;
    /** The active scissor mask */
    private CUScissor scissor;

    /** Cache for making the affine transform in draw methods */
    private final Affine2 transformCache;
    /** Cache for the rect poly in draw methods */
    private final Poly2 polyCache;
    /** Cache for vertices in the polyCache */
    private final float[] verticesCache;
    /** Cache for making the indices in the polyCache */
    private final short[] indicesCache;
    /** Cache for uniform data */
    private final float[] uniformBlockData;
    /** Cache for chunkify offsets */
    private final IntIntMap offsets;

    /** Constructs a new SpriteBatch with a size of 1000, one buffer, and the default shader.
     * @see CUSpriteBatch#CUSpriteBatch(int, CUShader) */
    public CUSpriteBatch () {
        this(1000, null);
    }

    /** Constructs a SpriteBatch with one buffer and the default shader.
     * @see CUSpriteBatch#CUSpriteBatch(int, CUShader) */
    public CUSpriteBatch (int size) {
        this(size, null);
    }

    /** Constructs a SpriteBatch with one buffer, the default shader, and assumes 5 floats for each vertex */
    public CUSpriteBatch (int size, CUShader defaultShader) {
        this(size, defaultShader, 5);
    }

    /** Constructs a new SpriteBatch. Sets the projection matrix to an orthographic projection with y-axis point upwards, x-axis
     * point to the right and the origin being in the bottom left corner of the screen. The projection will be pixel perfect with
     * respect to the current screen resolution.
     * <p>
     * The defaultShader specifies the shader to use. Note that the names for uniforms for this default shader are different than
     * the ones expect for shaders set with {@link #setShader(ShaderProgram)}.
     * @param size The max number of sprites in a single batch. Max of 8191.
     * @param defaultShader The default shader to use. This is not owned by the SpriteBatch and must be disposed separately. */
    public CUSpriteBatch (int size, CUShader defaultShader, int numsInVertex) {
        // 32767 is max vertex index, so 32767 / 4 vertices per sprite = 8191 sprites max.
        if (size > 8191) throw new IllegalArgumentException("Can't have more than 8191 sprites per batch: " + size);

        projectionMatrix.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        if (defaultShader == null) {
            shader = SpriteShader.createShader(numsInVertex);
            ownsShader = true;
        } else
            shader = defaultShader;

        // Set up data arrays
        vertMax = size;
        this.numsInVertex = numsInVertex;
        vertices = new float[size * numsInVertex];

        indxMax = size*6;
		indxData = new short[indxMax];

        if (numsInVertex == 7) {
            vertbuff = new CUVertexBuffer(SpriteShader.ATTRIBUTE_OFFSET[4], vertMax * 4, indxMax);
            vertbuff.setupAttribute(CUShader.GRADCOORD_ATTRIBUTE + "0", 2, GL30.GL_FLOAT, false, SpriteShader.ATTRIBUTE_OFFSET[3]);
        } else {
            vertbuff = new CUVertexBuffer(SpriteShader.ATTRIBUTE_OFFSET[3], vertMax * 4, indxMax);
        }
        vertbuff.setupAttribute(ShaderProgram.POSITION_ATTRIBUTE, 2, GL30.GL_FLOAT, false, SpriteShader.ATTRIBUTE_OFFSET[0]);
        vertbuff.setupAttribute(ShaderProgram.COLOR_ATTRIBUTE, 4, GL30.GL_UNSIGNED_BYTE, true, SpriteShader.ATTRIBUTE_OFFSET[1]);
        vertbuff.setupAttribute(ShaderProgram.TEXCOORD_ATTRIBUTE + "0", 2, GL30.GL_FLOAT, false, SpriteShader.ATTRIBUTE_OFFSET[2]);
        vertbuff.attach(shader);

        unifbuff = new CUUniformBuffer(40 * Float.SIZE, size/4);
        // Layout std140 format
        unifbuff.setOffset("scMatrix", 0);
        unifbuff.setOffset("scExtent", 48);
        unifbuff.setOffset("scScale",  56);
        unifbuff.setOffset("gdMatrix", 64);
        unifbuff.setOffset("gdInner",  112);
        unifbuff.setOffset("gdOuter",  128);
        unifbuff.setOffset("gdExtent", 144);
        unifbuff.setOffset("gdRadius", 152);
        unifbuff.setOffset("gdFeathr", 156);
        ((CUShader)shader).setUniformBlock(SpriteShader.CONTEXT_UNIFORM,unifbuff);
        uniformBlockData = new float[40];

        scissor = null;
        gradient = null;
        context = new Context();
        context.dirty = DIRTY_ALL_VALS;
        history = new PooledLinkedList<>(size);

        transformCache = new Affine2();
        verticesCache = new float[8];
        indicesCache = new short[8];
        polyCache = new Poly2(verticesCache, indicesCache);
        offsets = new IntIntMap();
    }

    /**
     * Deletes the vertex buffers and resets all attributes.
     *
     * You must reinitialize the sprite batch to use it.
     */
    @Override
    public void dispose () {
        if (ownsShader && shader != null) shader.dispose();
        if (context != null) {
            context.dispose();
            context = null;
        }

        if (vertbuff != null) {
            vertbuff.dispose();
            vertbuff = null;
        }

        if (unifbuff != null) {
            unifbuff.dispose();
            unifbuff = null;
        }

        if (history != null) {
            history.clear();
            history = null;
        }
        gradient = null;
        scissor = null;

        inflight = false;
    }

    //region Attributes
    @Override
    public void setColor (Color tint) {
        color.set(tint);
        colorPacked = tint.toFloatBits();
    }

    @Override
    public void setColor (float r, float g, float b, float a) {
        color.set(r, g, b, a);
        colorPacked = color.toFloatBits();
    }

    @Override
    public Color getColor () {
        return color;
    }

    @Override
    public void setPackedColor (float packedColor) {
        Color.abgr8888ToColor(color, packedColor);
        this.colorPacked = packedColor;
    }

    @Override
    public float getPackedColor () {
        return colorPacked;
    }

    /**
     * Sets the shader for this sprite batch
     *
     * This value may NOT be changed during a drawing pass.
     *
     * @param shader The active shader for this sprite batch
     */
    public void setShader(CUShader shader) {
        if (drawing) {
            throw new IllegalStateException("Attempt to reassign shader while drawing is active");
        } else if (shader == null) {
            throw new NullPointerException("Shader cannot be null");
        }
        vertbuff.detach();
        this.shader = shader;
        vertbuff.attach(this.shader);
        ((CUShader)this.shader).setUniformBlock(SpriteShader.CONTEXT_UNIFORM, unifbuff);
    }

    /**
     * Sets the shader for this sprite batch
     *
     * This value may NOT be changed during a drawing pass.
     *
     * @param shader The active shader for this sprite batch
     */
    @Override
    public void setShader (ShaderProgram shader) {
        if (this.shader == shader)
            return;
        CUShader gl30Shader;
        try {
            gl30Shader = (CUShader)shader;
        } catch (Exception e) {
            throw new IllegalArgumentException("Shader "+shader+" is not a GL30 compliant shader");
        }
        setShader(gl30Shader);
    }

    @Override
    public boolean isBlendingEnabled() {
        return context.blending;
    }

    @Override
    public void enableBlending() {
        if (!context.blending) {
            if (inflight) { record(); }
            context.blending = true;
            context.dirty = context.dirty | DIRTY_BLENDSTATE;
        }
    }

    @Override
    public void disableBlending() {
        if (context.blending) {
            if (inflight) { record(); }
            context.blending = false;
            context.dirty = context.dirty | DIRTY_BLENDSTATE;
        }
    }

    /**
     * Sets the blending equation for this sprite batch
     *
     * The enum must be a standard ones supported by OpenGL.  See
     *
     *      https://www.opengl.org/sdk/docs/man/html/glBlendEquation.xhtml
     *
     * However, this setter does not do any error checking to verify that
     * the input is valid.  By default, the equation is GL_FUNC_ADD.
     *
     * @param equation  Specifies how source and destination colors are combined
     */
    public void setBlendEquation(int equation) {
        if (context.blendEquation != equation) {
            if (inflight) { record(); }
            context.blendEquation = equation;
            context.dirty = context.dirty | DIRTY_BLENDSTATE;
        }
    }

    /**
     * Returns the blending equation for this sprite batch
     *
     * By default this value is GL_FUNC_ADD. For other options, see
     *
     *      https://www.opengl.org/sdk/docs/man/html/glBlendEquation.xhtml
     *
     * @return the blending equation for this sprite batch
     */
    public int getBlendEquation() { return context.blendEquation; }

    /**
     * Sets the blending function for this sprite batch
     *
     * The enums are the standard ones supported by OpenGL.  See
     *
     *      https://www.opengl.org/sdk/docs/man/html/glBlendFunc.xhtml
     *
     * However, this setter does not do any error checking to verify that
     * the enums are valid.  By default, srcFactor is GL_SRC_ALPHA while
     * dstFactor is GL_ONE_MINUS_SRC_ALPHA. This corresponds to non-premultiplied
     * alpha blending.
     *
     * @param srcFactor Specifies how the source blending factors are computed
     * @param dstFactor Specifies how the destination blending factors are computed.
     */
    @Override
    public void setBlendFunction(int srcFactor, int dstFactor) {
        if (context.srcFactor != srcFactor || context.dstFactor != dstFactor) {
            if (inflight) { record(); }
            context.srcFactor = srcFactor;
            context.dstFactor = dstFactor;
            context.dirty = context.dirty | DIRTY_BLENDFACTOR;
        }
    }

    @Override
    public void setBlendFunctionSeparate(int srcFactor, int dstFactor, int srcFactorAlpha, int dstFactorAlpha) {
        if (context.srcFactor != srcFactor || context.dstFactor != dstFactor ||
                context.srcFactor != srcFactorAlpha || context.dstFactorAlpha != dstFactorAlpha) {
            if (inflight) { record(); }
            context.srcFactor = srcFactor;
            context.dstFactor = dstFactor;
            context.srcFactorAlpha = srcFactorAlpha;
            context.dstFactorAlpha = dstFactorAlpha;
            context.dirty = context.dirty | DIRTY_BLENDFACTOR;
        }
    }

    /**
     * Returns the source blending factor
     *
     * By default this value is GL_SRC_ALPHA. For other options, see
     *
     *      https://www.opengl.org/sdk/docs/man/html/glBlendFunc.xhtml
     *
     * @return the source blending factor
     */
    @Override
    public int getBlendSrcFunc () {
        return context.srcFactor;
    }

    /**
     * Returns the destination blending factor
     *
     * By default this value is GL_ONE_MINUS_SRC_ALPHA. For other options, see
     *
     *      https://www.opengl.org/sdk/docs/man/html/glBlendFunc.xhtml
     *
     * @return the destination blending factor
     */
    @Override
    public int getBlendDstFunc () {
        return context.dstFactor;
    }

    /**
     * Returns the separate alpha source blending factor
     *
     * By default this value agrees with {@link #getBlendSrcFunc}.  To set this
     * as a separate value, use {@link #setBlendFunctionSeparate}.
     *
     * @return the separate alpha source blending factor
     */
    @Override
    public int getBlendSrcFuncAlpha() {
        if (context.srcFactorAlpha != -1) {
            return context.srcFactorAlpha;
        }
        return context.srcFactor;
    }

    /**
     * Returns the separate alpha destination blending factor
     *
     * By default this value agrees with {@link #getBlendDstFunc}.  To set this
     * as a separate value, use {@link #setBlendFunctionSeparate}.
     *
     * @return the separate alpha destination blending factor
     */
    @Override
    public int getBlendDstFuncAlpha() {
        if (context.dstFactorAlpha != -1) {
            return context.dstFactorAlpha;
        }
        return context.dstFactor;
    }

    /**
     * Sets the blur step in pixels (0 if there is no blurring).
     *
     * This sprite batch supports a simple 9-step blur. The blur samples
     * from the center pixel and 8 other pixels around it in a box. The
     * blur step is the number of pixels away to sample. So a 1-step
     * blur samples from the immediate neighbor pixels. On most textures
     * a 5-step blur has very noticeable affects.
     *
     * This is not a full-featured Gaussian blur. In particular, large
     * step values will start to produce a pixellation effect. But it
     * can produce acceptable blur effects with little cost to performance.
     * It is especially ideal for font-blur effects on font atlases.
     *
     * Setting this value to 0 will disable texture blurring.  This
     * value is 0 by default.
     *
     * @param step  The blur step in pixels
     */
    public void setBlurStep(int step) {
        if (context.blurstep == step) {
            return;
        }

        if (inflight) { record(); }
        if (step == 0) {
            // Active gradient is not null
            context.dirty = context.dirty | DIRTY_BLURSTEP | DIRTY_DRAWTYPE;
            context.type = context.type & ~TYPE_GAUSSBLUR;
        } else if (context.blurstep == 0){
            context.dirty = context.dirty | DIRTY_BLURSTEP | DIRTY_DRAWTYPE;
            context.type = context.type | TYPE_GAUSSBLUR;
        } else {
            context.dirty = context.dirty | DIRTY_BLURSTEP;
        }
        context.blurstep = step;
    }

    /**
     * Returns the blur step in pixels (0 if there is no blurring).
     *
     * This sprite batch supports a simple 9-step blur. The blur samples
     * from the center pixel and 8 other pixels around it in a box. The
     * blur step is the number of pixels away to sample. So a 1-step
     * blur samples from the immediate neighbor pixels. On most textures
     * a 5-step blur has very noticeable affects.
     *
     * This is not a full-featured Gaussian blur. In particular, large
     * step values will start to produce a pixellation effect. But it
     * can produce acceptable blur effects with little cost to performance.
     * It is especially ideal for font-blur effects on font atlases.
     *
     * Setting this value to 0 will disable texture blurring.  This
     * value is 0 by default.
     *
     * @return the blur step in pixels (0 if there is no blurring).
     */
    public int getBlurStep() { return context.blurstep; }

    @Override
    public Matrix4 getProjectionMatrix () {
        return context.perspective;
    }

    @Override
    public Matrix4 getTransformMatrix () {
        return context.transform;
    }

    @Override
    public void setProjectionMatrix (Matrix4 projection) {
        if (projection == null) {
            projectionMatrix.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            projection = projectionMatrix;
        }
        if (!context.perspective.equals(projection)) {
            if (inflight) { record(); }
            context.perspective = projection;
            context.dirty = context.dirty | DIRTY_MATRIX;
        }
    }

    @Override
    public void setTransformMatrix (Matrix4 transform) {
        if (transform == null) {
            transform = new Matrix4();
        }
        if (!context.transform.equals(transform)) {
            if (inflight) { record(); }
            context.transform = transform;
            context.dirty = context.dirty | DIRTY_MATRIX;
        }
    }

    /**
     * Returns the active gradient of this sprite batch
     *
     * Gradients may be used in the place of (and together with) colors.
     *
     * By default, the gradient will not be used (as it is slower than
     * solid colors).
     *
     * All gradients are tinted by the active color. Unless you explicitly
     * want this tinting, you should set the active color to white before
     * drawing with an active gradient.
     *
     * This method returns a copy of the internal gradient. Changes to this
     * object have no effect on the sprite batch.
     *
     * @return The active gradient for this sprite batch
     */
    public CUGradient getGradient()  {
        if (gradient != null) {
            return new CUGradient(gradient);
        }
        return null;
    }

    /**
     * Sets the active gradient of this sprite batch
     *
     * Gradients may be used in the place of (and together with) colors.
     *
     * If this value is nullptr, then no gradient is active. In that case,
     * the color vertex attribute will be interpretted as normal (e.g. a
     * traditional color vector).  This value is nullptr by default.
     *
     * All gradients are tinted by the active color. Unless you explicitly
     * want this tinting, you should set the active color to white before
     * drawing with an active gradient.
     *
     * This method acquires a copy of the gradient. Changes to the original
     * gradient after calling this method have no effect.
     *
     * @param gradient   The active gradient for this sprite batch
     */
    public void setGradient(CUGradient gradient) {
        if (gradient == this.gradient) {
            return;
        }
        if (inflight) record();
        if (gradient == null) {
            // Active gradient is not null
            context.dirty = context.dirty | DIRTY_UNIBLOCK | DIRTY_DRAWTYPE;
            context.type = context.type & ~TYPE_GRADIENT;
            this.gradient = null;
        } else {
            context.dirty = context.dirty | DIRTY_UNIBLOCK | DIRTY_DRAWTYPE;
            context.type = context.type | TYPE_GRADIENT;
            this.gradient = new CUGradient(gradient);
        }
    }


    /**
     * Returns the active scissor mask of this sprite batch
     *
     * Scissor masks may be combined with all types of drawing (colors,
     * textures, and gradients).
     *
     * If this value is nullptr, then no scissor mask is active. This value
     * is nullptr by default.
     *
     * This method returns a copy of the internal scissor. Changes to this
     * object have no effect on the sprite batch.
     *
     * @return The active scissor mask for this sprite batch
     */
    public CUScissor getScissor () {
        if (scissor != null) {
            return new CUScissor(scissor);
        }
        return null;
    }

    /**
     * Sets the active scissor mask of this sprite batch
     *
     * Scissor masks may be combined with all types of drawing (colors,
     * textures, and gradients).
     *
     * If this value is nullptr, then no scissor mask is active. This value
     * is nullptr by default.
     *
     * This method acquires a copy of the scissor. Changes to the original
     * scissor mask after calling this method have no effect.
     *
     * @param scissor   The active scissor mask for this sprite batch
     */
    public void setScissor(CUScissor scissor) {
        if (scissor == this.scissor) {
            return;
        }

        if (inflight) { record(); }

        if (scissor == null) {
            // Active gradient is not null
            context.dirty = context.dirty | DIRTY_UNIBLOCK | DIRTY_DRAWTYPE;
            context.type = context.type & ~TYPE_SCISSOR;
            this.scissor = null;
        } else {
            context.dirty = context.dirty | DIRTY_UNIBLOCK | DIRTY_DRAWTYPE;
            context.type = context.type | TYPE_SCISSOR;
            this.scissor = new CUScissor(scissor);
        }
    }

    /**
     * Sets the current drawing command.
     *
     * The value must be one of GL_TRIANGLES or GL_LINES.  Changing this value
     * during a drawing pass will flush the buffer.
     */
    public void setCommand(int command) {
        if (context.command != command) {
            if (inflight) { record(); }
            context.command = command;
            context.dirty = context.dirty | DIRTY_COMMAND;
        }
    }

    /**
     * Sets the active texture of this sprite batch
     *
     * All subsequent shapes and outlines drawn by this sprite batch will use
     * this texture.  If the value is nullptr, all shapes and outlines will be
     * draw with a solid color instead.  This value is nullptr by default.
     *
     * Changing this value will cause the sprite batch to flush.  However, a
     * subtexture will not cause a pipeline flush.  This is an important
     * argument for using texture atlases.
     *
     * @param texture   The active texture for this sprite batch
     */
    public void setTexture(Texture texture) {
        if (texture == context.texture) {
            return;
        }

        if (inflight) { record(); }
        if (texture == null) {
            // Active texture is not null
            context.dirty = context.dirty | DIRTY_DRAWTYPE;
            context.texture = null;
            context.type = context.type & ~TYPE_TEXTURE;
        } else if (context.texture == null) {
            // Texture is not null
            context.dirty = context.dirty | DIRTY_DRAWTYPE | DIRTY_TEXTURE;
            context.texture = texture;
            context.type = context.type | TYPE_TEXTURE;
        } else {
            // Both must be not nullptr
            context.dirty = context.dirty | DIRTY_TEXTURE;
            context.texture = texture;
        }
    }

    @Override
    public ShaderProgram getShader () {
        return shader;
    }

    @Override
    public boolean isDrawing () {
        return drawing;
    }

    /**
     * Sets the current stencil effect
     *
     * Stencil effects can be used to restrict the drawing region and
     * are generally used to speed up the processing of non-convex
     * shapes. See {@link CUStencilEffect} for the list of supported
     * effects, as well as a discussion of how the two halves of the
     * stencil buffer work.
     *
     * This value should be set to {@link CUStencilEffect.Effect#NATIVE} (the
     * default) if you wish to directly manipulate the OpenGL stencil.
     * This is sometimes necessary for more complex effects.
     *
     * @param effect    The current stencil effect
     */
    public void setStencilEffect(CUStencilEffect.Effect effect) {
        if (context.stencil != effect) {
            if (inflight) { record(); }
            context.stencil = effect;
            context.dirty = context.dirty | DIRTY_STENCIL_EFFECT;
        }
    }

    /**
     * Returns the current stencil effect
     *
     * Stencil effects can be used to restrict the drawing region and
     * are generally used to speed up the processing of non-convex
     * shapes. See {@link CUStencilEffect} for the list of supported
     * effects, as well as a discussion of how the two halves of the
     * stencil buffer work.
     *
     * This value should be set to {@link CUStencilEffect.Effect#NATIVE} (the
     * default) if you wish to directly manipulate the OpenGL stencil.
     * This is sometimes necessary for more complex effects.
     *
     * @return the current stencil effect
     */
    public CUStencilEffect.Effect getStencilEffect() {
        return context.stencil;
    }

    /**
     * Clears the stencil buffer.
     *
     * This method clears both halves of the stencil buffer: both upper
     * and lower. See {@link CUStencilEffect} for a discussion of how the
     * two halves of the stencil buffer work.
     */
    public void clearStencil() {
        if (context.cleared != CUStencilEffect.STENCIL_BOTH) {
            if (inflight) { record(); }
            context.cleared = CUStencilEffect.STENCIL_BOTH;
            context.dirty = context.dirty | DIRTY_STENCIL_CLEAR;
        }
    }

    /**
     * Clears half of the stencil buffer.
     *
     * This method clears only one of the two halves of the stencil
     * buffer. See {@link CUStencilEffect} for a discussion of how the
     * two halves of the stencil buffer work.
     *
     * @param lower     Whether to clear the lower stencil buffer
     */
    public void clearHalfStencil(boolean lower) {
        int state = lower ? CUStencilEffect.STENCIL_LOWER : CUStencilEffect.STENCIL_UPPER;
        if (context.cleared != state) {
            if (inflight) { record(); }
            context.cleared = context.cleared | state;
            context.dirty = context.dirty | DIRTY_STENCIL_CLEAR;
        }
    }

    //endregion

    //region Rendering

    @Override
    public void begin () {
        if (drawing) throw new IllegalStateException("SpriteBatch.end must be called before begin.");
        renderCalls = 0;
        vertTotal = 0;

        Gdx.gl30.glDepthMask(false);
        shader.bind();
        vertbuff.bind();
        unifbuff.bind(false);
        unifbuff.deactivate();

        context.dirty = DIRTY_ALL_VALS;

        drawing = true;
    }

    @Override
    public void end () {
        if (!drawing) throw new IllegalStateException("SpriteBatch.begin must be called before end.");
        if (idx > 0) flush();
        drawing = false;

        CUStencilEffect.applyEffect(CUStencilEffect.Effect.NONE);

        GL20 gl = Gdx.gl;
        gl.glDepthMask(true);
        if (isBlendingEnabled()) gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void flush () {
        if (idx == 0) {
            return;
        } else if (context.first != indxSize) {
            record();
        }

        vertbuff.loadVertexData(vertices, idx);
        vertbuff.loadIndexData(indxData, indxSize);

        unifbuff.activate();
        unifbuff.flush();

        Texture previous = context.texture;
        history.iter();
        Context next = null;
        GL30 gl = Gdx.gl30;
        while ((next = history.next()) != null) {
            if ((next.dirty & DIRTY_BLENDSTATE) == DIRTY_BLENDSTATE) {
                if (next.blending) {
                    gl.glEnable(GL30.GL_BLEND);
                    gl.glBlendEquation(next.blendEquation);
                } else {
                    gl.glDisable(GL30.GL_BLEND);
                }
            }
            if ((next.dirty & DIRTY_BLENDFACTOR) == DIRTY_BLENDFACTOR) {
                if (next.srcFactorAlpha != -1 && next.dstFactorAlpha != -1) {
                    Gdx.gl30.glBlendFuncSeparate( next.srcFactor, next.dstFactor,
                            next.srcFactorAlpha, next.dstFactorAlpha );
                } else {
                    Gdx.gl30.glBlendFunc( next.srcFactor, next.dstFactor );
                }
            }
            if ((next.dirty & DIRTY_DRAWTYPE) == DIRTY_DRAWTYPE) {
                shader.setUniformi("u_drawtype", next.type);
            }
            if ((next.dirty & DIRTY_MATRIX) == DIRTY_MATRIX){
                projectionMatrix.set(next.perspective).mul(next.transform);
                shader.setUniformMatrix("u_projTrans", projectionMatrix);
            }
            if ((next.dirty & DIRTY_TEXTURE) == DIRTY_TEXTURE) {
                previous = next.texture;
                if (previous != null) {
                    previous.bind();
                }
            }
            if ((next.dirty & DIRTY_UNIBLOCK) == DIRTY_UNIBLOCK) {
                unifbuff.setBlock(next.blockptr);
            }
            if ((next.dirty & DIRTY_BLURSTEP) == DIRTY_BLURSTEP) {
                blurTexture(next.texture,next.blurstep);
            }
            if ((next.dirty & DIRTY_STENCIL_CLEAR) == DIRTY_STENCIL_CLEAR) {
                CUStencilEffect.clearBuffer(next.cleared);
            }
            if ((next.dirty & DIRTY_STENCIL_EFFECT) == DIRTY_STENCIL_EFFECT) {
                CUStencilEffect.applyEffect(next.stencil);
            }

            int amt = next.last-next.first;
            vertbuff.draw(next.command, amt, next.first);
            renderCalls++;
            totalRenderCalls++;
        }

        vertTotal += indxSize;
        vertSize = indxSize = 0;

        unifbuff.deactivate();
        unwind();
        context.first = 0;
        context.last = 0;
        context.blockptr = -1;
        idx = 0;
    }

    //endregion

    //region Solid Shapes

    /**
     * Draws the given rectangle (described by the parameters) filled with the
     * current color and texture, with the transformations on it.
     *
     * The texture will fill the entire rectangle with texture coordinate
     * (0,1) at the bottom left corner identified by rect,origin. Alternatively, you can use a {@link Poly2}
     * for more fine-tuned control.
     *
     * If depth testing is on, all vertices will use the current sprite
     * batch depth.
     *
     * @param x         The x-coordinate in screen space
     * @param y         The y-coordinate in screen space
     * @param originX   The rotation origin x-coord
     * @param originY   The rotation origin y-coord
     * @param width     The width of the rectangle to draw
     * @param height    The height of the rectangle to draw
     * @param scaleX    The scale factor in the x-direction
     * @param scaleY    The scale factor in the y-direction
     * @param rotation  The amount to rotate in degrees
     * @param srcX      The x-coordinate in texel space
     * @param srcY      The y-coordinate in texel space
     * @param srcWidth  The source width in texels
     * @param srcHeight The source height in texels
     * @param tWidth    The texture width
     * @param tHeight   The texture height
     * @param flipX     Whether to flip the sprite horizontally
     * @param flipY     Whether to flip the sprite vertically
     */
    public void fill(float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY,
                     float rotation, int srcX, int srcY, int srcWidth, int srcHeight, float tWidth, float tHeight, boolean flipX, boolean flipY) {
        transformCache.idt();
        transformCache.preTranslate(-originX, -originY);
        transformCache.preScale(scaleX, scaleY);
        transformCache.preRotate(rotation);
        transformCache.preTranslate(x + originX, y + originY);

        setCommand(GL30.GL_TRIANGLES);
        makeRect(0, 0, width, height, context.command == GL30.GL_TRIANGLES);
        prepare(srcX, srcY, srcWidth, srcHeight, tWidth, tHeight, flipX, flipY);
    }

    /** Draws the given rectangle filled with the current color and texture. */
    public void fill(float x, float y, float width, float height, int srcX, int srcY, int srcWidth, int srcHeight,
                     float tWidth, float tHeight, boolean flipX, boolean flipY) {
        transformCache.idt();
        transformCache.preTranslate(x, y);

        setCommand(GL30.GL_TRIANGLES);
        makeRect(0, 0, width, height, context.command == GL30.GL_TRIANGLES);
        prepare(srcX, srcY, srcWidth, srcHeight, tWidth, tHeight, flipX, flipY);
    }

    /** Draws the given rectangle filled with the current color and texture. The portion of the
     * {@link Texture} given by u, v and u2, v2 are used. */
    public void fill(float x, float y, float width, float height, float u, float v, float u2, float v2) {
        transformCache.idt();

        setCommand(GL30.GL_TRIANGLES);
        makeRect(x, y, width, height, context.command == GL30.GL_TRIANGLES);
        prepare(width, height, u, v, u2, v2);
    }

    /** Draws the given rectangle filled with the current color and texture. */
    public void fill(float x, float y, float width, float height) {
        transformCache.idt();

        setCommand(GL30.GL_TRIANGLES);
        makeRect(x, y, width, height, context.command == GL30.GL_TRIANGLES);
        prepare(x, y, width, height);
    }

    /** Draws the given rectangle filled with the current color and texture, with transforms described.
     * The portion of the {@link Texture} given by u, v and u2, v2 are used. */
    public void fill(float x, float y, float originX, float originY, float width, float height, float scaleX,
                     float scaleY, float rotation, float u, float v, float u2, float v2) {
        transformCache.idt();
        transformCache.preTranslate(-originX, -originY);
        transformCache.preScale(scaleX, scaleY);
        transformCache.preRotate(rotation);
        transformCache.preTranslate(x + originX, y + originY);

        setCommand(GL30.GL_TRIANGLES);
        makeRect(0, 0, width, height, context.command == GL30.GL_TRIANGLES);
        prepare(width, height, u, v, u2, v2);
    }

    /** Draws the given rectangle filled with the current color and texture, with transforms described.
     * The portion of the {@link Texture} given by u, v and u2, v2 are used. */
    public void fill(float width, float height, Affine2 transform, float u, float v, float u2, float v2) {
        transformCache.set(transform);

        setCommand(GL30.GL_TRIANGLES);
        makeRect(0, 0, width, height, context.command == GL30.GL_TRIANGLES);
        prepare(width, height, u, v, u2, v2);
    }

    /**
     * Draws the given polygon filled with the current color and texture.
     *
     * The polygon tesselation will be determined by the indices in poly. If
     * the polygon has not been triangulated (by one of the triangulation
     * factories {@link EarclipTriangulator} or {@link DelaunayTriangulator},
     * it may not draw properly.
     *
     * The vertex coordinates will be determined by polygon vertex position.
     * A horizontal position x has texture coordinate x/texture.width. A
     * vertical coordinate has texture coordinate 1-y/texture.height. As a
     * result, a rectangular polygon that has the same dimensions as the
     * texture is the same as simply drawing the texture.
     *
     * One way to think of the polygon is as a "cookie cutter".  Treat the
     * polygon coordinates as pixel coordinates in the texture file, and use
     * that to determine how the texture fills the polygon. This may make the
     * polygon larger than you like in order to get the appropriate texturing.
     * You should use one of the transform methods to fix this.
     *
     * If depth testing is on, all vertices will use the current sprite
     * batch depth.
     *
     * @param poly The polygon to draw
     * @param x    The x offset
     * @param y    The y offset
     */
    public void fill(Poly2 poly, float x, float y) {
        transformCache.idt();
        transformCache.preTranslate(x, y);

        setCommand(GL30.GL_TRIANGLES);
        prepare(poly);
    }

    /** Draw the given polygon with the texture, with transforms described. */
    public void fill (Poly2 poly, float x, float y, float originX, float originY, float scaleX,
                      float scaleY, float rotation) {
        transformCache.idt();
        transformCache.preTranslate(-originX, -originY);
        transformCache.preScale(scaleX, scaleY);
        transformCache.preRotate(rotation);
        transformCache.preTranslate(x + originX, y + originY);

        setCommand(GL30.GL_TRIANGLES);
        prepare(poly);
    }

    /** Draw the given polygon with the texture, with the given transform. */
    public void fill (Poly2 poly, float x, float y, Affine2 transform) {
        transformCache.set(transform);
        transformCache.preTranslate(x, y);

        setCommand(GL30.GL_TRIANGLES);
        prepare(poly);
    }
    //endregion

    //region Outlines
    /**
     * Outlines the given rectangle with the current color and texture.
     *
     * The drawing will be a wireframe of a rectangle.  The wireframe will
     * be textured with Texture coordinate (0,1) at the bottom left corner
     * identified by rect,origin. The remaining edges will correspond to the
     * edges of the texture. To draw only part of a texture, use a subtexture
     * to outline the edges with [minS,maxS]x[min,maxT]. Alternatively, you
     * can use a {@link Poly2} for more fine-tuned control.
     *
     * If depth testing is on, all vertices will use the current sprite
     * batch depth.
     *
     * @param x      x-coordinate
     * @param y      y-coordinate
     * @param width  width of outline box
     * @param height height of outline box
     */
    public void outline(float x, float y, float width, float height) {
        transformCache.idt();

        setCommand(GL30.GL_LINES);
        makeRect(x, y, width, height, context.command == GL30.GL_TRIANGLES);
        prepare(x, y, width, height);
    }

    /**
     * Outlines the given rectangle with the current color and texture.
     *
     * The rectangle will be scaled first, then rotated, and finally offset
     * by the given position. Rotation is measured in radians and is counter
     * clockwise from the x-axis.  Rotation will be about the provided origin,
     * which is specified relative to the origin of the rectangle (not world
     * coordinates).  So to spin about the center, the origin should be width/2,
     * height/2 of the rectangle.
     *
     * The drawing will be a wireframe of a rectangle.  The wireframe will
     * be textured with Texture coordinate (0,1) at the bottom left corner
     * identified by rect,origin. The remaining edges will correspond to the
     * edges of the texture. To draw only part of a texture, use a subtexture
     * to outline the edges with [minS,maxS]x[min,maxT]. Alternatively, you can
     * use a {@link Poly2} for more fine-tuned control.
     *
     * If depth testing is on, all vertices will use the current sprite
     * batch depth.
     *
     * @param x         The x coordinate to draw at
     * @param y         The y coordinate to draw at
     * @param width     The width of the rectangle
     * @param height    The height of the rectangle
     * @param originX   The x coordinate of the rotation offset
     * @param originY   The y coordinate of the rotation offset
     * @param scaleX    The amount to scale the rectangle in x direction
     * @param scaleY    The amount to scale the rectangle in y direction
     * @param rotation  The amount to rotate the rectangle
     */
    public void outline(float x, float y, float width, float height, float originX, float originY, float scaleX,
                              float scaleY, float rotation) {
        transformCache.idt();
        transformCache.preTranslate(-originX, -originY);
        transformCache.preScale(scaleX, scaleY);
        transformCache.preRotate(rotation);
        transformCache.preTranslate(x + originX, y + originY);

        setCommand(GL30.GL_LINES);
        makeRect(0, 0, width, height, context.command == GL30.GL_TRIANGLES);
        prepare(x, y, width, height);
    }

    /** Outlines the given rectangle with the given transforms. The portion of the
     * {@link Texture} given by u, v and u2, v2 are used. */
    public void outline(float width, float height, float originX, float originY, Affine2 transform, float u, float v,
                        float u2, float v2) {
        transformCache.set(transform);
        transformCache.preTranslate(originX, originY);

        setCommand(GL30.GL_LINES);
        makeRect(0, 0, width, height, context.command == GL30.GL_TRIANGLES);
        prepare(width, height, u, v, u2, v2);
    }

    /**
     * Outlines the given path with the current color and texture. The poly
     * here represents a Path2, using the appropriate converted vertices and
     * indices. Look at {@link Path2#getIndices()} for information on getting
     * indices.
     *
     * The drawing will be a wireframe of a path, but the lines are textured.
     * The vertex coordinates will be determined by path vertex position.
     * A horizontal position x has texture coordinate x/texture.width. A
     * vertical coordinate has texture coordinate 1-y/texture.height. As a
     * result, a rectangular polygon that has the same dimensions as the
     * texture is the same as simply outlining the rectangle.
     *
     * One way to think of the path is as a "cookie cutter".  Treat the
     * path coordinates as pixel coordinates in the texture file, and use
     * that to determine how the texture fills the path. This may make the
     * path larger than you like in order to get the appropriate texturing.
     * You should use one of the transform methods to fix this.
     *
     * If depth testing is on, all vertices will use the current sprite
     * batch depth.
     *
     * @param poly      The path to outline
     */
    public void outline(Poly2 poly) {
        transformCache.idt();

        setCommand(GL30.GL_LINES);
        prepare(poly);
    }

    /**
     * Outlines the given path with the current color and texture. The poly
     * here represents a Path2, using the appropriate converted vertices and
     * indices. Look at {@link Path2#getIndices()} for information on getting
     * indices from a Path2.
     *
     * The path will be offset by the given position.
     *
     * The drawing will be a wireframe of a path, but the lines are textured.
     * The vertex coordinates will be determined by path vertex position.
     * A horizontal position x has texture coordinate x/texture.width. A
     * vertical coordinate has texture coordinate 1-y/texture.height. As a
     * result, a rectangular polygon that has the same dimensions as the
     * texture is the same as simply outlining the rectangle.
     *
     * One way to think of the path is as a "cookie cutter".  Treat the
     * path coordinates as pixel coordinates in the texture file, and use
     * that to determine how the texture fills the path. This may make the
     * path larger than you like in order to get the appropriate texturing.
     * You should use one of the transform methods to fix this.
     *
     * If depth testing is on, all vertices will use the current sprite
     * batch depth.
     *
     * @param poly  The path to outline
     * @param x     The x offset
     * @param y     The y offset
     */
    public void outline(Poly2 poly, float x, float y) {
        transformCache.idt();
        transformCache.preTranslate(x, y);

        setCommand(GL30.GL_LINES);
        prepare(poly);
    }

    /**
     * Outlines the given path with the current color and texture. The poly
     * here represents a Path2, using the appropriate converted vertices and
     * indices. Look at {@link Path2#getIndices()} for information on getting
     * indices from a Path2.
     *
     * The path will be scaled first, then rotated, and finally offset
     * by the given position. Rotation is measured in radians and is counter
     * clockwise from the x-axis.  Rotation will be about the provided origin,
     * which is specified relative to the origin of the path (not world
     * coordinates). Hence this origin is essentially the pixel coordinate
     * of the texture (see below) to assign as the rotational center.
     *
     * The drawing will be a wireframe of a path, but the lines are textured.
     * The vertex coordinates will be determined by path vertex position.
     * A horizontal position x has texture coordinate x/texture.width. A
     * vertical coordinate has texture coordinate 1-y/texture.height. As a
     * result, a rectangular polygon that has the same dimensions as the
     * texture is the same as simply outlining the rectangle.
     *
     * One way to think of the path is as a "cookie cutter".  Treat the
     * path coordinates as pixel coordinates in the texture file, and use
     * that to determine how the texture fills the path. This may make the
     * path larger than you like in order to get the appropriate texturing.
     * You should use one of the transform methods to fix this.
     *
     * If depth testing is on, all vertices will use the current sprite
     * batch depth.
     *
     * @param poly      The path to outline
     * @param x         The x offset
     * @param y         The y offset
     * @param originX   The x coordinate of the rotation offset
     * @param originY   The y coordinate of the rotation offset
     * @param scaleX    The amount to scale the rectangle in x direction
     * @param scaleY    The amount to scale the rectangle in y direction
     * @param rotation  The amount to rotate the rectangle
     */
    public void outline (Poly2 poly, float x, float y, float originX, float originY, float scaleX,
                      float scaleY, float rotation) {
        transformCache.idt();
        transformCache.preTranslate(-originX, -originY);
        transformCache.preScale(scaleX, scaleY);
        transformCache.preRotate(rotation);
        transformCache.preTranslate(x + originX, y + originY);

        setCommand(GL30.GL_LINES);
        prepare(poly);
    }

    /**
     * Outlines the given path with the current color and texture.The poly
     * here represents a Path2, using the appropriate converted vertices and
     * indices. Look at {@link Path2#getIndices()} for information on getting
     * indices from a Path2.
     *
     * The path will transformed by the given matrix. The transform will
     * be applied assuming the given origin, which is specified relative
     * to the origin of the path (not world coordinates). Hence this origin
     * is essentially the pixel coordinate of the texture (see below) to
     * assign as the origin of this transform.
     *
     * The drawing will be a wireframe of a path, but the lines are textured.
     * The vertex coordinates will be determined by path vertex position.
     * A horizontal position x has texture coordinate x/texture.width. A
     * vertical coordinate has texture coordinate 1-y/texture.height. As a
     * result, a rectangular polygon that has the same dimensions as the
     * texture is the same as simply outlining the rectangle.
     *
     * One way to think of the path is as a "cookie cutter".  Treat the
     * path coordinates as pixel coordinates in the texture file, and use
     * that to determine how the texture fills the path. This may make the
     * path larger than you like in order to get the appropriate texturing.
     * You should use one of the transform methods to fix this.
     *
     * If depth testing is on, all vertices will use the current sprite
     * batch depth.
     *
     * @param poly          The path to outline
     * @param x             The x offset
     * @param y             The y offset
     * @param transform     The transform to be applied
     */
    public void outline (Poly2 poly, float x, float y, Affine2 transform) {
        transformCache.set(transform);
        transformCache.preTranslate(x, y);

        setCommand(GL30.GL_LINES);
        prepare(poly);
    }
    //endregion

    //region Drawing Convenience Methods
    @Override
    public void draw (Texture texture, float x, float y, float originX, float originY, float width, float height, float scaleX,
                      float scaleY, float rotation, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {
        if (!drawing) throw new IllegalStateException("SpriteBatch.begin must be called before draw.");

        setTexture(texture);
        fill(x, y, originX, originY, width, height, scaleX, scaleY, rotation, srcX, srcY, srcWidth, srcHeight,
                texture.getWidth(), texture.getHeight(), flipX, flipY);
    }

    @Override
    public void draw (Texture texture, float x, float y, float width, float height, int srcX, int srcY, int srcWidth,
                      int srcHeight, boolean flipX, boolean flipY) {
        if (!drawing) throw new IllegalStateException("SpriteBatch.begin must be called before draw.");

        setTexture(texture);
        fill(x, y, width, height, srcX, srcY, srcWidth, srcHeight, texture.getWidth(), texture.getHeight(),
                flipX, flipY);
    }

    @Override
    public void draw (Texture texture, float x, float y, int srcX, int srcY, int srcWidth, int srcHeight) {
        if (!drawing) throw new IllegalStateException("SpriteBatch.begin must be called before draw.");

        setTexture(texture);
        fill(x, y, srcWidth, srcHeight, srcX, srcY, srcWidth, srcHeight, texture.getWidth(), texture.getHeight(),
                false, false);
    }

    @Override
    public void draw (Texture texture, float x, float y, float width, float height, float u, float v, float u2, float v2) {
        if (!drawing) throw new IllegalStateException("SpriteBatch.begin must be called before draw.");

        setTexture(texture);
        fill(x, y, width, height, u, v, u2, v2);
    }

    @Override
    public void draw (Texture texture, float x, float y) {
        draw(texture, x, y, texture.getWidth(), texture.getHeight());
    }

    @Override
    public void draw (Texture texture, float x, float y, float width, float height) {
        if (!drawing) throw new IllegalStateException("SpriteBatch.begin must be called before draw.");

        setTexture(texture);
        fill(x, y, width, height);
    }

    @Override
    public void draw (Texture texture, float[] spriteVertices, int offset, int count) {
        if (!drawing) throw new IllegalStateException("SpriteBatch.begin must be called before draw.");

        setTexture(texture);
        setCommand(GL30.GL_TRIANGLES);
        if (idx + count >= vertices.length || indxSize+(count / (4 * numsInVertex)) * 6 >= indxMax)
            flush();

        int verticesLength = vertices.length;
        int remainingVertices = verticesLength;

        remainingVertices -= idx;
        if (remainingVertices == 0) {
            flush();
            remainingVertices = verticesLength;
        }

        int numIndicesToAdd = (count / (4 * numsInVertex)) * 6;
        int j = vertSize;
        for (int i = indxSize; i < indxSize + numIndicesToAdd; i += 6, j += 4) {
            indxData[i] = (short) j;
            indxData[i + 1] = (short)(j + 1);
            indxData[i + 2] = (short)(j + 2);
            indxData[i + 3] = (short)(j + 2);
            indxData[i + 4] = (short)(j + 3);
            indxData[i + 5] = (short) j;
        }
        indxSize += numIndicesToAdd;
        vertSize += count / numsInVertex;

        int copyCount = Math.min(remainingVertices, count);

        System.arraycopy(spriteVertices, offset, vertices, idx, copyCount);
        idx += copyCount;
        count -= copyCount;
        while (count > 0) {
            offset += copyCount;
            flush();
            copyCount = Math.min(verticesLength, count);
            System.arraycopy(spriteVertices, offset, vertices, 0, copyCount);
            idx += copyCount;
            count -= copyCount;
        }

        setUniformBlock();
        inflight = true;
    }

    @Override
    public void draw (TextureRegion region, float x, float y) {
        draw(region, x, y, region.getRegionWidth(), region.getRegionHeight());
    }

    @Override
    public void draw (TextureRegion region, float x, float y, float width, float height) {
        if (!drawing) throw new IllegalStateException("SpriteBatch.begin must be called before draw.");

        setTexture(region.getTexture());
        fill(x, y, width, height, region.getU(), region.getV2(), region.getU2(), region.getV());
    }

    @Override
    public void draw (TextureRegion region, float x, float y, float originX, float originY, float width, float height,
                      float scaleX, float scaleY, float rotation) {
        if (!drawing) throw new IllegalStateException("SpriteBatch.begin must be called before draw.");

        setTexture(region.getTexture());
        fill(x, y, originX, originY, width, height, scaleX, scaleY, rotation, region.getU(), region.getV2(),
                region.getU2(), region.getV());
    }

    @Override
    public void draw (TextureRegion region, float x, float y, float originX, float originY, float width, float height,
                      float scaleX, float scaleY, float rotation, boolean clockwise) {
        if (!drawing) throw new IllegalStateException("SpriteBatch.begin must be called before draw.");

        setTexture(region.getTexture());
        if (clockwise) {
            fill(x, y, originX, originY, width, height, scaleX, scaleY, rotation, region.getU2(), region.getV2(),
                    region.getU(), region.getV());
        } else {
            fill(x, y, originX, originY, width, height, scaleX, scaleY, rotation, region.getU(), region.getV(),
                    region.getU2(), region.getV2());
        }
    }

    @Override
    public void draw (TextureRegion region, float width, float height, Affine2 transform) {
        if (!drawing) throw new IllegalStateException("SpriteBatch.begin must be called before draw.");

        setTexture(region.getTexture());
        fill(width, height, transform, region.getU(), region.getV2(), region.getU2(), region.getV());
    }

    /**
     * Draws the textured polygon (without tint) at the given position
     *
     * This is a convenience method that calls the appropriate fill method.
     * It sets both the texture and color (removing the previous active values).
     * It then draws the polygon, offset by the given value.
     *
     * The polygon tesselation will be determined by the indices in poly. If
     * the polygon has not been triangulated (by a triangulation
     * factories like {@link DelaunayTriangulator}, it may not draw properly.
     *
     * The vertex coordinates will be determined by polygon vertex position.
     * A horizontal position x has texture coordinate x/texture.width. A
     * vertical coordinate has texture coordinate 1-y/texture.height. As a
     * result, a rectangular polygon that has the same dimensions as the
     * texture is the same as simply drawing the texture.
     *
     * One way to think of the polygon is as a "cookie cutter".  Treat the
     * polygon coordinates as pixel coordinates in the texture filed, and use
     * that to determine how the texture fills the polygon. This may make the
     * polygon larger than you like in order to get the appropriate texturing.
     * You should use one of the transform methods to fix this.
     *
     * If depth testing is on, all vertices will use the current sprite
     * batch depth.
     *
     * @param texture   The new active texture
     * @param poly      The polygon to texture
     * @param x         The polygon x offset
     * @param y         The polygon y offset
     */
    public void draw (Texture texture, Poly2 poly, float x, float y) {
        setTexture(texture);
        fill(poly, x, y);
    }

    /**
     * Draws the texture (without tint) at the given position
     *
     * This is a convenience method that calls the appropriate fill method.
     * It sets both the texture and color (removing the previous active values).
     * It then draws a rectangle of the size of the texture, with bottom left
     * corner at the given position.
     *
     * If depth testing is on, all vertices will use the current sprite
     * batch depth.
     *
     * @param texture   The texture to draw
     * @param poly      The polygon to draw
     * @param x         The x offset
     * @param y         The y offset
     * @param originX   The x-coordinate of the scaling and rotation origin relative to the screen space coordinates
     * @param originY   The y-coordinate of the scaling and rotation origin relative to the screen space coordinates
     * @param scaleX    The scale factor in the x-direction
     * @param scaleY    The scale factor in the y-direction
     * @param rotation  The amount to rotate the sprite in degrees
     */
    public void draw (Texture texture, Poly2 poly, float x, float y, float originX, float originY, float scaleX,
                      float scaleY, float rotation) {
        setTexture(texture);
        fill(poly, x, y, originX, originY, scaleX, scaleY, rotation);
    }

    /** Draw the given polygon of the texture with the transform. */
    public void draw (Texture texture, Poly2 poly, float x, float y, Affine2 transform) {
        setTexture(texture);
        fill(poly, x, y, transform);
    }
    //endregion

    //region Internal Helpers
    /**
     * Fills poly with a mesh defining the given rectangle.
     *
     * We need this method because we want to allow non-standard
     * polygons that represent a path, and not a triangulated
     * polygon.
     *
     * @param x, y, width, height      The source rectangle
     * @param solid     Whether to triangulate the rectangle
     */
    void makeRect(float x, float y, float width, float height, boolean solid) {
        verticesCache[0] = x;
        verticesCache[1] = y;
        verticesCache[2] = x;
        verticesCache[3] = y + height;
        verticesCache[4] = x + width;
        verticesCache[5] = y + height;
        verticesCache[6] = x + width;
        verticesCache[7] = y;
        if (solid) {
            indicesCache[0] = 0;
            indicesCache[1] = 1;
            indicesCache[2] = 2;
            indicesCache[3] = 2;
            indicesCache[4] = 3;
            indicesCache[5] = 0;
        } else {
            indicesCache[0] = 0;
            indicesCache[1] = 1;
            indicesCache[2] = 1;
            indicesCache[3] = 2;
            indicesCache[4] = 2;
            indicesCache[5] = 3;
            indicesCache[6] = 3;
            indicesCache[7] = 0;
        }
    }

    /**
     * Records the current set of uniforms, freezing them.
     *
     * This method must be called whenever draw is called for
     * a new set of uniforms.  It ensures that the vertices batched so far
     * will use the correct set of uniforms.
     */
    public void record () {
        Context next = new Context(context);
        context.last = indxSize;
        next.first = indxSize;
        history.add(context);
        context = next;
        inflight = false;
    }

    /**
     * Deletes the recorded uniforms.
     *
     * This method is called upon flushing or cleanup.
     */
    public void unwind() {
        history.clear();
    }

    /**
     * Sets the active uniform block to agree with the gradient and stroke.
     *
     * This method is called upon vertex preparation.
     *
     */
    public void setUniformBlock() {
        if ((context.dirty & DIRTY_UNIBLOCK) != DIRTY_UNIBLOCK) {
            return;
        }
        if (context.blockptr+1 >= unifbuff.getBlockCount()) {
            flush();
        }
        float[] data = uniformBlockData;
        if (scissor != null) {
            data = scissor.getData(data, 0);
        }
        if (gradient != null) {
            data = gradient.getData(data, 16);
        }
        context.blockptr++;
        unifbuff.setUniformfv(context.blockptr,0,40,data);
    }

    /**
     * Updates the shader with the current blur offsets
     *
     * Blur offsets depend upon the texture size. This method converts the
     * blur step into an offset in texture coordinates. It supports
     * non-square textures.
     *
     * If there is no active texture, the blur offset will be 0.
     *
     * @param texture   The texture to blur
     * @param step      The blur step in pixels
     */
    private void blurTexture(Texture texture, int step)  {
        if (texture == null) {
            shader.setUniformf("u_blurstep", 0, 0);
            return;
        }
        float width  = step/(float)texture.getWidth();
        float height = step/(float)texture.getHeight();
        shader.setUniformf("u_blurstep",width,height);
    }

    /**
     * This method adds the given rectangle (from the parameters) to the vertex buffer,
     * but does not draw it yet.  You must call {@link #flush} or {@link #end} to draw the
     * rectangle. This method will automatically flush if the maximum number
     * of vertices is reached.
     *
     * @param x         The x-coordinate in screen space
     * @param y         The y-coordinate in screen space
     * @param width     The width in pixels
     * @param height    The height in pixels
     */
    public void prepare(float x, float y, float width, float height) {
        if (idx + (numsInVertex * 4) >= vertices.length || indxSize+8 >= indxMax)
            flush();

        int idx = this.idx;
        float clr = this.colorPacked;
        int ii = 0;
        for (int i = 0; i < polyCache.vertices.length; i+=2) {
            float x1 = polyCache.vertices[i]; float y1 = polyCache.vertices[i+1];
            float xm = transformCache.m00 * x1 + transformCache.m01 * y1 + transformCache.m02;
            float ym = transformCache.m10 * x1 + transformCache.m11 * y1 + transformCache.m12;
            x1 = (x1-x) / width;
            y1 = 1-(y1-y) / height;
            vertices[idx] = xm;
            vertices[idx + 1] = ym;
            vertices[idx + 2] = clr;
            vertices[idx + 3] = x1;
            vertices[idx + 4] = y1;
            if (numsInVertex == 7) {
                vertices[idx + 5] = x1;
                vertices[idx + 6] = y1;
            }

            idx += numsInVertex;
            ii++;
        }

        int jj = 0;
        int istart = indxSize;
        int indLength = context.command == GL30.GL_TRIANGLES ? 6 : 8;
        for (int i = 0; i < indLength; i++) {
            indxData[istart+jj] = (short) (vertSize + polyCache.indices[i]);
            jj++;
        }
        this.idx += numsInVertex * ii;

        setUniformBlock();

        vertSize += ii;
        indxSize += jj;
        inflight = true;
    }

    /**
     * This method adds the given rectangle (from the parameters) to the vertex buffer,
     * but does not draw it yet.  You must call {@link #flush} or {@link #end} to draw the
     * rectangle. This method will automatically flush if the maximum number
     * of vertices is reached. The portion of the {@link Texture} given by srcX, srcY and
     * srcWidth, srcHeight is used.
     */
    public void prepare(int srcX, int srcY, int srcWidth, int srcHeight, float tWidth, float tHeight, boolean flipX, boolean flipY) {
        if (idx + (numsInVertex * 4) >= vertices.length || indxSize+8 >= indxMax)
            flush();

        int idx = this.idx;
        float clr = this.colorPacked;
        int ii = 0;
        for (int i = 0; i < polyCache.vertices.length; i+=2) {
            float x1 = polyCache.vertices[i]; float y1 = polyCache.vertices[i+1];
            float xm = transformCache.m00 * x1 + transformCache.m01 * y1 + transformCache.m02;
            float ym = transformCache.m10 * x1 + transformCache.m11 * y1 + transformCache.m12;
            vertices[idx] = xm;
            vertices[idx + 1] = ym;
            vertices[idx + 2] = clr;

            idx += numsInVertex;
            ii++;
        }

        int jj = 0;
        int istart = indxSize;
        int indLength = context.command == GL30.GL_TRIANGLES ? 6 : 8;
        for (int i = 0; i < indLength; i++) {
            indxData[istart+jj] = (short) (vertSize + polyCache.indices[i]);
            jj++;
        }

        // Now, do the textures (separated because there are srcX and srcY parameters)
        idx = this.idx;

        float u = srcX / tWidth;
        float v = (srcY + srcHeight) / tHeight;
        float u2 = (srcX + srcWidth) / tWidth;
        float v2 = srcY / tHeight;

        if (flipX) {
            float tmp = u;
            u = u2;
            u2 = tmp;
        }

        if (flipY) {
            float tmp = v;
            v = v2;
            v2 = tmp;
        }
        vertices[idx + 3] = u;
        vertices[idx + 4] = v;
        idx += numsInVertex;
        vertices[idx + 3] = u;
        vertices[idx + 4] = v2;
        idx += numsInVertex;
        vertices[idx + 3] = u2;
        vertices[idx + 4] = v2;
        idx += numsInVertex;
        vertices[idx + 3] = u2;
        vertices[idx + 4] = v;

        idx = this.idx;
        if (numsInVertex == 7) {
            vertices[idx + 5] = u;
            vertices[idx + 6] = v;
            idx += numsInVertex;
            vertices[idx + 5] = u;
            vertices[idx + 6] = v2;
            idx += numsInVertex;
            vertices[idx + 5] = u2;
            vertices[idx + 6] = v2;
            idx += numsInVertex;
            vertices[idx + 5] = u2;
            vertices[idx + 6] = v;
        }

        this.idx += numsInVertex * ii;

        setUniformBlock();

        vertSize += ii;
        indxSize += jj;
        inflight = true;
    }

    /**
     * This method adds the given rectangle (from the parameters) to the vertex buffer,
     * but does not draw it yet.  You must call {@link #flush} or {@link #end} to draw the
     * rectangle. This method will automatically flush if the maximum number
     * of vertices is reached. The portion of the {@link Texture} given by u, v and u2, v2 are used.
     */
    public void prepare(float width, float height, float u, float v, float u2, float v2) {
        if (idx + (numsInVertex * 4) >= vertices.length || indxSize+8 >= indxMax)
            flush();

        int idx = this.idx;
        float clr = this.colorPacked;
        int ii = 0;
        for (int i = 0; i < polyCache.vertices.length; i+=2) {
            float x1 = polyCache.vertices[i]; float y1 = polyCache.vertices[i+1];
            float xm = transformCache.m00 * x1 + transformCache.m01 * y1 + transformCache.m02;
            float ym = transformCache.m10 * x1 + transformCache.m11 * y1 + transformCache.m12;
            vertices[idx] = xm;
            vertices[idx + 1] = ym;
            vertices[idx + 2] = clr;

            idx += numsInVertex;
            ii++;
        }

        int jj = 0;
        int istart = indxSize;
        int indLength = context.command == GL30.GL_TRIANGLES ? 6 : 8;
        for (int i = 0; i < indLength; i++) {
            indxData[istart+jj] = (short) (vertSize + polyCache.indices[i]);
            jj++;
        }

        // Now, do the textures (separated because there are srcX and srcY parameters)
        idx = this.idx;
        vertices[idx + 3] = u;
        vertices[idx + 4] = v;
        idx += numsInVertex;
        vertices[idx + 3] = u;
        vertices[idx + 4] = v2;
        idx += numsInVertex;
        vertices[idx + 3] = u2;
        vertices[idx + 4] = v2;
        idx += numsInVertex;
        vertices[idx + 3] = u2;
        vertices[idx + 4] = v;

        idx = this.idx;
        if (numsInVertex == 7) {
            vertices[idx + 5] = u;
            vertices[idx + 6] = v;
            idx += numsInVertex;
            vertices[idx + 5] = u;
            vertices[idx + 6] = v2;
            idx += numsInVertex;
            vertices[idx + 5] = u2;
            vertices[idx + 6] = v2;
            idx += numsInVertex;
            vertices[idx + 5] = u2;
            vertices[idx + 6] = v;
        }

        this.idx += numsInVertex * ii;

        setUniformBlock();

        vertSize += ii;
        indxSize += jj;
        inflight = true;
    }

    /** This method adds the polygon to the vertex buffer, but does not draw it yet. */
    public void prepare (Poly2 poly) {
        assert(context.command == GL30.GL_TRIANGLES ?
                poly.indices.length % 3 == 0 :
                poly.indices.length % 2 == 0) :
                "Polynomial has the wrong number of indices: " + poly.indices.length;

        if (poly.vertices.length >= vertMax || poly.indices.length  >= indxMax) {
            chunkify(poly);
            return;
        } else if (idx + (numsInVertex * poly.vertices.length) >= vertices.length ||
                indxSize+poly.indices.length  > indxMax) {
            flush();
        }

        Texture texture = context.texture;
        float twidth, theight;

        if (texture != null) {
            twidth  = texture.getWidth();
            theight = texture.getHeight();
        } else {
            twidth  = poly.getBounds().width;
            theight = poly.getBounds().height;
        }

        int idx = this.idx;
        float clr = this.colorPacked;
        int ii = 0;
        for (int i = 0; i < poly.vertices.length; i+=2) {
            float x1 = poly.vertices[i]; float y1 = poly.vertices[i+1];
            float xm = transformCache.m00 * x1 + transformCache.m01 * y1 + transformCache.m02;
            float ym = transformCache.m10 * x1 + transformCache.m11 * y1 + transformCache.m12;
            x1 /= twidth;
            y1 = 1 - (y1/theight);
            vertices[idx] = xm;
            vertices[idx + 1] = ym;
            vertices[idx + 2] = clr;
            vertices[idx + 3] = x1;
            vertices[idx + 4] = y1;
            if (numsInVertex == 7) {
                vertices[idx + 5] = x1;
                vertices[idx + 6] = y1;
            }

            idx += numsInVertex;
            ii++;
        }

        int jj = 0;
        int istart = indxSize;
        for (int i = 0; i < poly.indices.length; i++) {
            indxData[istart+jj] = (short) (vertSize + poly.indices[i]);
            jj++;
        }

        setUniformBlock();

        this.idx += numsInVertex * ii;
        vertSize += ii;
        indxSize += jj;
        inflight = true;
    }

    /**
     * Returns the number of vertices added to the drawing buffer.
     *
     * This method is an alternate version of {@link #prepare} for the same
     * arguments.  It runs slower (e.g. the compiler cannot easily optimize
     * the loops) but it is guaranteed to work on any size polygon.  This
     * is important for avoiding memory corruption.
     *
     * All vertices will be uniformly transformed by the transform matrix.
     * If depth testing is on, all vertices will use the current sprite
     * batch depth.
     *
     * @param poly The polygone to add to the buffer
     */
    public void chunkify(Poly2 poly) {
        int chunksize = context.command == GL30.GL_TRIANGLES ? 3 : (context.command == GL30.GL_LINES ? 2 : 1);

        Texture texture = context.texture;
        float twidth;
        float theight;

        if (texture != null) {
            twidth = texture.getWidth();
            theight = texture.getHeight();
        } else {
            twidth  = poly.getBounds().width;
            theight = poly.getBounds().height;
        }

        float clr = this.colorPacked;
        for(int ii = 0;  ii < poly.indices.length; ii += chunksize) {
            if (indxSize+chunksize > indxMax || vertSize+chunksize > vertMax) {
                flush();
                offsets.clear();
            }

            for(int jj = 0; jj < chunksize; jj++) {
                int search = offsets.get(poly.indices[ii+jj], -1);
                if (search != -1) {
                    indxData[indxSize] = (short)search;
                } else {
                    int id = poly.indices[ii+jj];
                    float x1 = poly.vertices[2*id  ];
                    float y1 = poly.vertices[2*id+1];
                    float xm = transformCache.m00 * x1 + transformCache.m01 * y1 + transformCache.m02;
                    float ym = transformCache.m10 * x1 + transformCache.m11 * y1 + transformCache.m12;
                    x1 /= twidth;
                    y1 = 1 - (y1/theight);
                    vertices[idx] = xm;
                    vertices[idx + 1] = ym;
                    vertices[idx + 2] = clr;
                    vertices[idx + 3] = x1;
                    vertices[idx + 4] = y1;
                    if (numsInVertex == 7) {
                        vertices[idx + 5] = x1;
                        vertices[idx + 6] = y1;
                    }

                    indxData[indxSize] = (short)vertSize;

                    offsets.put(poly.indices[ii+jj], vertSize);
                    vertSize++;
                    idx += numsInVertex;
                }
                indxSize++;
            }
        }

        setUniformBlock();
        offsets.clear();
        inflight = true;
    }
    //endregion

    //region Drawing Context
    /**
     * A class storing the drawing context for the associate shader.
     *
     * Because we want to minimize the number of times we load vertices
     * to the vertex buffer, all uniforms are recorded and delayed until the
     * final graphics call.  We include blending attributes as part of the
     * context, since they have similar performance characteristics to
     * other uniforms.
     */
    public static class Context implements Pool.Poolable {
        /** The first vertex index position for this set of uniforms */
        public int first;
        /** The last vertex index position for this set of uniforms */
        public int last;
        /** The drawing type for the shader */
        public int type;
        /** The stored drawing command */
        public int command;
        /** Whether to enable blending */
        public boolean blending;
        /** The stored blending equation */
        public int blendEquation;
        /** The stored source factor */
        public int srcFactor;
        /** The stored destination factor */
        public int dstFactor;
        /** The stored alpha source factor */
        public int srcFactorAlpha;
        /** The stored alpha destination factor */
        public int dstFactorAlpha;
        /** The current stencil effect */
        public CUStencilEffect.Effect stencil;
        /** The stencil buffer to clear */
        public int cleared;
        /** The stored perspective matrix */
        public Matrix4 perspective;
        /** The stored transform matrix */
        public Matrix4 transform;
        /** The stored texture */
        public Texture texture;
        /** The stored block offset for gradient and scissor */
        public int blockptr;
        /** The pixel step for our blur function */
        public int  blurstep;
        /** The dirty bits relative to the previous set of uniforms */
        public int dirty;

        /**
         * Creates a context of the default uniforms.
         */
        public Context() {
            perspective = new Matrix4();
            transform = new Matrix4();
            reset();
        }

        /**
         * Creates a copy of the given uniforms
         *
         * @param copy  The uniforms to copy
         */
        public Context(Context copy) {
            perspective = new Matrix4();
            transform = new Matrix4();
            set(copy);
        }

        public void set(Context copy) {
            first = copy.first;
            last  = copy.last;
            type  = copy.type;
            command = copy.command;
            blending = copy.blending;
            blendEquation = copy.blendEquation;
            srcFactor = copy.srcFactor;
            dstFactor = copy.dstFactor;
            srcFactorAlpha = copy.srcFactorAlpha;
            dstFactorAlpha = copy.dstFactorAlpha;
            perspective.set(copy.perspective);
            transform.set(copy.transform);
            stencil = copy.stencil;
            cleared = CUStencilEffect.STENCIL_NONE;
            texture  = copy.texture;
            blockptr = copy.blockptr;
            blurstep = copy.blurstep;
            dirty = 0;
        }

        /**
         * Disposes this collection of uniforms
         */
        public void dispose() {
            first = 0;
            last  = 0;
            command = 0;
            blending = false;
            blendEquation = 0;
            srcFactor = 0;
            dstFactor = 0;
            srcFactorAlpha = 0;
            dstFactorAlpha = 0;
            perspective = null;
            transform = null;
            stencil = CUStencilEffect.Effect.NATIVE;
            cleared = CUStencilEffect.STENCIL_NONE;
            texture = null;
            blockptr = -1;
            blurstep = 0;
            type = 0;
            dirty = 0;
        }

        /**
         *
         * Resets this context to its default values
         */
        public void reset() {
            first = 0;
            last = 0;
            command = GL30.GL_TRIANGLES;
            blending = true;
            blendEquation = GL30.GL_FUNC_ADD;
            srcFactor = GL30.GL_SRC_ALPHA;
            dstFactor = GL30.GL_ONE_MINUS_SRC_ALPHA;
            srcFactorAlpha = -1;
            dstFactorAlpha = -1;
            perspective.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            transform.idt();
            stencil  = CUStencilEffect.Effect.NATIVE;
            cleared  = CUStencilEffect.STENCIL_NONE;
            texture = null;
            blockptr = -1;
            blurstep = 0;
            type = 0;
            dirty = 0;
        }
    }

    /** The default vertex capacity */
    public static final int DEFAULT_CAPACITY = 8192;

    /** The drawing type for a textured mesh */
    private static final int TYPE_TEXTURE = 1;
    /** The drawing type for a gradient mesh */
    private static final int  TYPE_GRADIENT = 2;
    /** The drawing type for a scissored mesh */
    private static final int TYPE_SCISSOR = 4;
    /** The drawing type for a (simple) texture blur */
    private static final int  TYPE_GAUSSBLUR = 8;

    /** The drawing command has changed */
    private static final int DIRTY_COMMAND = 1;
    /** The blending state or equation has changed */
    private static final int DIRTY_BLENDSTATE = 2;
    /** The blending factors have changed */
    private static final int DIRTY_BLENDFACTOR = 4;
    /** The drawing type has changed */
    private static final int DIRTY_DRAWTYPE = 8;
    /** The perspective or transform matrix has changed */
    private static final int DIRTY_MATRIX = 16;
    /** The stencil effect has changed */
    private static final int DIRTY_STENCIL_EFFECT = 32;
    /** The stencil effect was cleared since last draw */
    private static final int DIRTY_STENCIL_CLEAR = 64;
    /** The texture has changed */
    private static final int DIRTY_TEXTURE = 128;
    /** The block offset has changed */
    private static final int DIRTY_UNIBLOCK = 256;
    /** The blur step has changed */
    private static final int DIRTY_BLURSTEP = 512;
    /** All values have changed */
    private static final int DIRTY_ALL_VALS = 1023;
    //endregion
}

