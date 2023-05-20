package edu.cornell.gdiac.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.IntMap;

import java.nio.Buffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
/**
 * This is a class representing an offscreen render target.
 *
 * A render target allows the user to draw to a texture before drawing to a screen.
 * This allows for the potential for post-processing effects.  To draw to a render
 * target simply call the {@link #bind} method before drawing.  From that point on
 * all drawing commands will be sent to the associated texture instead of the screen.
 * Call {@link #unbind} to resume drawing to the screen.
 *
 * Frame buffers should not be stacked.  It is not safe to call a begin/end pair of
 * one render target inside of another begin/end pair.  Control to the screen should
 * be resumed before using another render target.
 *
 * While frame buffers must have at least one output texture, they can support multiple
 * textures as long as the active fragment shader has multiple output variables. The
 * locations of these outputs should be set explicitly and sequentially with the
 * layout keyword.
 *
 * This class greatly simplifies OpenGL framebuffers at the cost of some flexibility.
 * The only support for depth and stencil is a combined 24/8 depth and stencil buffer.
 * In addition, output textures must have one of the simplified formats defined by
 * {@link CUTexture.PixelFormat}. Finally, all output textures are bound sequentially
 * to output locations 0..#outputs-1. However, we find that still allows us to handle
 * the vast majority of applications with a framebuffer.
 *
 * Frame buffer targets may be written to a file for debugging purposes. While we
 * cannot write {@link CUTexture} objects to a file directly (OpenGLES does not
 * permit this), we can always do so when they are the target of a frame buffer.
 */
public class CURenderTarget {
    /** The framebuffer associated with this render target */
    private int framebo;
    /** The backing renderbuffer for the framebuffer */
    private int renderbo;

    /** The render target "screen" width */
    private int width;
    /** The render target "screen" height */
    private int height;

    /** The cached viewport to restore when this target is finished */
    private final IntBuffer viewport = BufferUtils.newByteBuffer( 4*16 ).asIntBuffer();
    /** Buffer for other queries */
    private final IntBuffer query = BufferUtils.newByteBuffer( 4*16 ).asIntBuffer();

    /** The combined depth and stencil buffer */
    private CUTexture  depthst;
    /** The array of output textures (must be at least one) */
    private CUTexture[] outputs;
    /** The bind points for linking up the shader output variables */
    private int[] bindpoints;

    // #mark Setup
    /**
     * Initializes the framebuffer and associated render buffer
     *
     * This method also initializes the depth/stencil buffer. It allocates the
     * arrays for the output textures and bindpoints. However, it does not
     * initialize the output textures.  That is done in {@link #attachTexture}.
     *
     * If this method fails, it will safely clean up any allocated objects
     * before quitting.
     *
     * @return true if initialization was successful.
     */
    private boolean prepareBuffer() {
        Gdx.gl30.glGetIntegerv( GL30.GL_VIEWPORT, viewport);

        int error;
        Gdx.gl30.glGenFramebuffers(1, query);
        framebo = query.get();
        query.clear();
        if (framebo == 0) {
            error = Gdx.gl30.glGetError();
            Gdx.app.error("OPENGL",String.format("Could not create frame buffer. %s", CUGLDebug.errorName(error)));
            return false;
        }

        Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebo);

        try {
            // Attach the depth buffer first
            depthst = new CUTexture( width, height, CUTexture.PixelFormat.DEPTH_STENCIL );
        } catch (Exception e) {
            dispose();
            Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            return false;
        }

        Gdx.gl30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                GL30.GL_TEXTURE_2D,  depthst.getBuffer(), 0);

        Gdx.gl30.glGenRenderbuffers(1, query);
        renderbo = query.get();
        query.clear();
        if (renderbo == 0) {
            error = Gdx.gl30.glGetError();
            dispose();
            Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            Gdx.app.error("OPENGL",String.format("Could not create render buffer. %s", CUGLDebug.errorName(error)));
            return false;
        }

        Gdx.gl30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbo);
        error = Gdx.gl30.glGetError();
        System.out.println(CUGLDebug.errorName( error ));

        Gdx.gl30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);
        System.out.println(CUGLDebug.errorName( error ));
        Gdx.gl30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                GL30.GL_RENDERBUFFER, renderbo);
        System.out.println(CUGLDebug.errorName( error ));
        error = Gdx.gl30.glGetError();
        if (error != 0) {
            dispose();
            Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            Gdx.app.error("OPENGL",String.format("Could not attach render buffer to frame buffer. %s",
                    CUGLDebug.errorName(error)));
            return false;
        }

        return true;
    }

    /**
     * Attaches an output texture with the given format to framebuffer.
     *
     * This method allocates the texture and binds it in the correct place
     * (e.g. GL_COLOR_ATTACHMENT0+index).  The texture will be the same size
     * as this render target.
     *
     * If this method fails, it will safely clean up any previously allocated
     * objects before quitting.
     *
     * @param index		The index location for the bindpoint array
     * @param bpoint    The bind point to attach this output texture
     * @param format    The texture pixel format {@see Texture}
     *
     * @return true if initialization was successful.
     */
    private boolean attachTexture(int index, int bpoint, CUTexture.PixelFormat format) {
        if (format == CUTexture.PixelFormat.RED_GREEN) {
            Gdx.app.error("OPENGL","RED_GREEN is not an accepted frame buffer format");
            return false;
        }

        int error;
        CUTexture texture;
        try {
            texture = new CUTexture( width, height, format );
        } catch (Exception e) {
            dispose();
            Gdx.gl30.glBindFramebuffer( GL30.GL_FRAMEBUFFER, 0 );
            return false;
        }

        outputs[index] = texture;
        texture.setBindPoint( bpoint,false );
        bindpoints[index] = GL30.GL_COLOR_ATTACHMENT0+bpoint;

        Gdx.gl30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0+bpoint,
                GL30.GL_TEXTURE_2D,  texture.getBuffer(), 0);
        error = Gdx.gl30.glGetError();
        if (error != 0) {
            dispose();
            Gdx.gl30.glBindFramebuffer( GL30.GL_FRAMEBUFFER, 0 );
            Gdx.app.error("OPENGL",String.format("Could not attach output textures to frame buffer. %s",
                    CUGLDebug.errorName(error)));
            return false;
        }
        return true;
    }

    /**
     * Completes the framebuffer after all attachments are finalized
     *
     * This sets the draw buffers and checks the framebuffer status. If
     * OpenGL says that it is complete, it returns true.
     *
     * If this method fails, it will safely clean up any previously allocated
     * objects before quitting.
     *
     * @return true if the framebuffer was successfully finalized.
     */
    private boolean completeBuffer() {
        IntBuffer data = BufferUtils.newByteBuffer( 4*bindpoints.length ).asIntBuffer();
        BufferUtils.copy( bindpoints,0,data,bindpoints.length );
        Gdx.gl30.glDrawBuffers( outputs.length, data);

        int status = Gdx.gl30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            dispose();
            Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            Gdx.app.error("OPENGL",String.format("Could not bind frame buffer. %s",
                    CUGLDebug.errorName(status)));
            return false;
        }

        Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        return true;
    }


    // #mark -
    // #mark Constructors
    /**
     * Creates a frame buffer with a single RGBA output texture.
     *
     * The output texture will have the given width and size.
     *
     * @param width        The drawing width of this render target
     * @param height    The drawing width of this render target
     */
    public CURenderTarget(int width, int height) {
        this(width,height,1);
    }

    /**
     * Creates a frame buffer with multiple RGBA output textures.
     *
     * The output textures will have the given width and size. They will
     * be assigned locations 0..outputs-1.  These locations should be
     * bound with the layout keyword in any shader used with this render
     * target. Otherwise the results are not well-defined.
     *
     * If outputs is larger than the number of possible shader outputs
     * for this platform, this method will fail.  OpenGL only guarantees
     * up to 8 output textures.
     *
     * @param width		The drawing width of this render target
     * @param height    The drawing width of this render target
     * @param outputs	The number of output textures
     */
    public CURenderTarget(int width, int height, int outputs) {
        this.width  = width;
        this.height = height;
        this.outputs = new CUTexture[outputs];
        bindpoints = new int[outputs];

        if (prepareBuffer()) {
            for(int ii = 0; ii < outputs; ii++) {
                if (!attachTexture(ii, ii, CUTexture.PixelFormat.RGBA)) {
                    throw new GdxRuntimeException( "Unable to construct frame buffer" );
                }
            }
            if (completeBuffer()) {
                return;
            }
        }

        throw new GdxRuntimeException( "Unable to construct frame buffer" );
    }

    /**
     * Creates a frame buffer with multiple textures of the given format.
     *
     * The output textures will have the given width and size. They will
     * be assigned the appropriate format as specified in {@link CUTexture}.
     * They will be assigned locations matching the keys of the map outputs.
     * These locations should be bound with the layout keyword in any shader
     * used with this render target. Otherwise the results are not well-defined.
     *
     * If the size of the outputs parameter is larger than the number of
     * possible shader outputs for this platform, this method will fail.
     * OpenGL only guarantees up to 8 output textures.
     *
     * @param width        The drawing width of this render target
     * @param height    The drawing width of this render target
     * @param outputs    The map of desired texture formats for each location
     */
    public CURenderTarget(int width, int height, IntMap<CUTexture.PixelFormat> outputs) {
        this.width  = width;
        this.height = height;
        this.outputs = new CUTexture[outputs.size];
        bindpoints = new int[outputs.size];

        if (prepareBuffer()) {
            int ii = 0;
            for(IntMap.Entry<CUTexture.PixelFormat> it : outputs.entries()) {
                if (!attachTexture(ii,it.key, it.value)) {
                    throw new GdxRuntimeException( "Unable to construct frame buffer" );
                }
                ii++;
            }
            if (completeBuffer()) {
                return;
            }
        }

        throw new GdxRuntimeException( "Unable to construct frame buffer" );
    }

    /**
     * Creates a frame buffer with with multiple textures of the given format.
     *
     * The output textures will have the given width and size. They will
     * be assigned the appropriate format as specified in {@link CUTexture}.
     * They will be assigned locations 0..#outputs-1.  These locations should
     * be bound with the layout keyword in any shader used with this render
     * target. Otherwise the results are not well-defined.
     *
     * If the size of the outputs parameter is larger than the number of
     * possible shader outputs for this platform, this method will fail.
     * OpenGL only guarantees up to 8 output textures.
     *
     * @param width     The drawing width of this render target
     * @param height    The drawing width of this render target
     * @param outputs   The list of desired texture formats
     */
    public CURenderTarget(int width, int height, Array<CUTexture.PixelFormat> outputs) {
        this.width  = width;
        this.height = height;
        this.outputs = new CUTexture[outputs.size];
        bindpoints = new int[outputs.size];

        if (prepareBuffer()) {
            int ii = 0;
            for(CUTexture.PixelFormat format : outputs) {
                if (!attachTexture(ii, ii, format)) {
                    throw new GdxRuntimeException( "Unable to construct frame buffer" );
                }
                ii++;
            }
            if (completeBuffer()) {
                return;
            }
        }

        throw new GdxRuntimeException( "Unable to construct frame buffer" );
    }

    /**
     * Creates a frame buffer with with multiple textures of the given format.
     *
     * The output textures will have the given width and size. They will
     * be assigned the appropriate format as specified in {@link CUTexture}.
     * They will be assigned locations 0..outsize-1.  These locations should
     * be bound with the layout keyword in any shader used with this render
     * target. Otherwise the results are not well-defined.
     *
     * If the size of the outputs parameter is larger than the number of
     * possible shader outputs for this platform, this method will fail.
     * OpenGL only guarantees up to 8 output textures.
     *
     * @param width     The drawing width of this render target
     * @param height    The drawing width of this render target
     * @param outputs   The list of desired texture formats
     */
    public CURenderTarget(int width, int height, CUTexture.PixelFormat[] outputs) {
        this.width  = width;
        this.height = height;
        this.outputs = new CUTexture[outputs.length];
        bindpoints = new int[outputs.length];

        if (prepareBuffer()) {
            int ii = 0;
            for(CUTexture.PixelFormat format : outputs) {
                if (!attachTexture(ii, ii, format)) {
                    throw new GdxRuntimeException( "Unable to construct frame buffer" );
                }
                ii++;
            }
            if (completeBuffer()) {
                return;
            }
        }

        throw new GdxRuntimeException( "Unable to construct frame buffer" );
    }

    /**
     * Clean up frame buffer on Garbage collection
     */
    @Override
    protected void finalize() throws Throwable {
        dispose();
        super.finalize();
    }

    /**
     * Deletes the frame buffer and resets all attributes.
     *
     * You must reinitialize the frame buffer to use it.
     */
    public void dispose() {
        if (framebo != 0) {
            query.put( framebo );
            query.flip();
            Gdx.gl30.glDeleteFramebuffers(1, query);
            query.clear();
            framebo = 0;
        }
        if (renderbo != 0) {
            query.put( renderbo );
            query.flip();
            Gdx.gl30.glDeleteRenderbuffers(1, query);
            query.clear();
            framebo = 0;
        }
        outputs = null;
        depthst = null;
        bindpoints = null;
        width  = 0;
        height = 0;
    }

    // #mark -
    // #mark Attributes
    /**
     * Returns the width of this render target
     *
     * @return the width of this render target
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the height of this render target
     *
     * @return the height of this render target
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the number of output textures for this render target.
     *
     * If the render target has been successfully initialized, this
     * value is guaranteed to be at least 1.
     *
     * @return the number of output textures for this render target.
     */
    int getOutputSize() {
        return outputs.length;
    }

    /**
     * Returns the primary output texture.
     *
     * If the frame buffer has multiple output textures, this will be the
     * texture at index 0.
     *
     * @return the output texture for the given index.
     */
    public CUTexture getTexture() {
        return outputs[0];
    }

    /**
     * Returns the output texture for the given index.
     *
     * The index should be a value between 0..OutputSize-1.
     *
     * @param index    The output index
     *
     * @return the output texture for the given index.
     */
    public CUTexture getTexture(int index) {
        return outputs[index];
    }

    /**
     * Returns the depth/stencil buffer for this render target
     *
     * The framebuffer for a render target always uses a combined depth
     * and stencil buffer.  It uses 24 bits for the depth and 8 bits for
     * the stencil.  This should be sufficient in most applications.
     *
     * @return the depth/stencil buffer for this render target
     */
    public CUTexture getDepthStencil() {
        return depthst;
    }

    // #mark -
    // #mark Binding
    /**
     * Binds this frame buffer so that it can receive draw commands.
     *
     * This method ets the viewpoint to match the size of this render target (which
     * may not be the same as the screen). The old viewport is saved and will be
     * restored when {@link #unbind} is called.
     *
     * It is NOT safe to call a bind/unbind pair of a render target inside of
     * another render target.  Render targets do not keep a stack.  They always
     * return control to the default render target (the screen) when done.
     */
    public void bind() {
        Gdx.gl30.glGetIntegerv(GL30.GL_VIEWPORT, viewport);

        Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebo);
        Gdx.gl30.glViewport(0, 0, width, height);
    }

    /**
     * Unbinds this frame buffer so that it no longer receives  draw commands.
     *
     * When this method is called, the original viewport will be restored. Future
     * draw commands will be sent directly to the screen.
     *
     * It is NOT safe to call a bind/unbind pair of a render target inside of
     * another render target.  Render targets do not keep a stack.  They always
     * return control to the default render target (the screen) when done.
     */
    public void unbind() {
        int[] vport = new int[4];
        Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        viewport.get(vport,0,4);
        Gdx.gl30.glViewport(vport[0], vport[1], vport[2], vport[3]);
        viewport.clear();
    }

    /**
     * Returns true if this frame buffer is currently bound.
     *
     * @return true if this frame buffer is currently bound.
     */
    public boolean isBound() {
        int orig;
        Gdx.gl30.glGetIntegerv(GL30.GL_FRAMEBUFFER_BINDING,query);
        orig = query.get();
        query.clear();
        return orig == framebo;
    }

    // #mark -
    // Texture Access
    /**
     * Returns the pixel data for the primary output texture.
     *
     * If the frame buffer has multiple output textures, this will be the
     * texture at index 0. The frame buffer must be bound for this method
     * to succeed.
     *
     * @return the pixel data for the primary output texture.
     */
    public Buffer getPixelData() {
        return getPixelData(0);
    }

    /**
     * Returns the pixel data for the given output texture.
     *
     * The index should be a value between 0..OutputSize-1. The frame buffer
     * must be bound for this method to succeed.
     *
     * @param index    The output index
     *
     * @return the pixel data for the given output texture.
     */
    public Buffer getPixelData(int index) {
        Buffer buffer = BufferUtils.newByteBuffer( width*height*outputs[index].getByteSize() );
        return getPixelData( buffer, index );
    }

    /**
     * Returns the pixel data for the given output texture, stored in place
     *
     * If the frame buffer has multiple output textures, this will be the
     * texture at index 0. The frame buffer must be bound for this method
     * to succeed.
     *
     * @param buffer    The buffer to store the pixel data
     *
     * @return the pixel data for the given output texture, stored in place
     */
    public Buffer getPixelData(Buffer buffer) {
        return getPixelData(buffer, 0);
    }

    /**
     * Returns the pixel data for the given output texture, stored in place
     *
     * The index should be a value between 0..OutputSize-1. The frame buffer
     * must be bound for this method to succeed.
     *
     * @param index     The output index
     * @param buffer    The buffer to store the pixel data
     *
     * @return the pixel data for the given output texture, stored in place
     */
    public Buffer getPixelData(Buffer buffer, int index) {
        CUTexture texture = outputs[index];
        int bpoint = bindpoints[index];
        CUTexture.PixelFormat format = texture.getFormat();
        Gdx.gl30.glReadBuffer( bpoint );
        Gdx.gl30.glReadPixels(0,0,width,height,format.glFormat( ),format.formatType(),buffer);
        return buffer;
    }

    /**
     * Returns the pixmap for the primary output texture.
     *
     * The Pixmap will have the y-pixels flipped, so that they agree with
     * the texture when saved to a file.
     *
     * If the frame buffer has multiple output textures, this will be the
     * texture at index 0. The frame buffer must be bound for this method
     * to succeed.
     *
     * @return the pixmap for the primary output texture.
     */
    public Pixmap getPixmap() {
        return getPixmap(0);
    }

    /**
     * Returns the pixmap for the given output texture.
     *
     * The Pixmap will have the y-pixels flipped, so that they agree with
     * the texture when saved to a file.
     *
     * The index should be a value between 0..OutputSize-1. The frame buffer
     * must be bound for this method to succeed.
     *
     * @param index     The output index
     *
     * @return the pixmap for the given output texture.
     */
    public Pixmap getPixmap(int index) {
        CUTexture texture = outputs[index];
        Pixmap.Format format = texture.getTextureData().getFormat();
        CUTexture.PixelFormat iformat = texture.getFormat();
        int size = width*height*texture.getByteSize();

        Gdx.gl30.glPixelStorei(GL30.GL_PACK_ALIGNMENT, 1);

        Pixmap pixmap = new Pixmap(width, height, format);
        getPixelData(pixmap.getPixels(),index);

        // Flip the Pixmap
        int w = pixmap.getWidth();
        int h = pixmap.getHeight();
        int temp;

        //change blending to 'none' so that we have true replacement
        pixmap.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < h / 2; y++) {
            for (int x = 0; x < w; x++) {
                temp = pixmap.getPixel(x,y);
                pixmap.drawPixel(x,y, pixmap.getPixel(x, h-y-1));
                pixmap.drawPixel(x,h-y-1, temp);
            }
        }
        pixmap.setBlending(Pixmap.Blending.SourceOver);

        return pixmap;
    }

    /**
     * Returns a string representation of this texture for debugging purposes.
     *
     * If verbose is true, the string will include class information.  This
     * allows us to unambiguously identify the class.
     *
     * @return a string representation of this texture for debugging purposes.
     */
    public String toString() {
        String result = "Framebuffer["+width+"x"+height+"]";
        result += String.format("@%x", hashCode());
        return result;
    }

}
