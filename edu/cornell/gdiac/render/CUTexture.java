package edu.cornell.gdiac.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.nio.*;

public class CUTexture extends Texture {
    /**
     * This enum lists the possible texture pixel formats.
     *
     * This enum defines the pixel formats supported by GL30. Because of
     * cross-platform issues (we must support both OpenGL and OpenGLES),
     * our textures only support a small subset of formats.
     *
     * This enum also associates default internal types and data types
     * with each pixel format. This greatly simplifies texture creation
     * at the loss of some flexibility.
     */
    public enum PixelFormat {
        /**
         * The default format: RGB with alpha transparency.
         *
         * This format uses GL_RGBA8 as the internal format. The data type
         * (for each component) is unsigned byte.
         */
        RGBA,
        /**
         * RGB with no alpha
         *
         * All blending with this texture assumes alpha is 1.0. This format
         * uses GL_RGB8 as the internal format. The data type (for each component)
         * is unsigned byte.
         */
        RGB,
        /**
         * A single color channel.  In OpenGL that is identified as red.
         *
         * The green and blue values will be 0.  All blending with this texture
         * assumes alpha is 1.0. This format uses GL_RGB8 as the internal format.
         * The data type (for each component) is unsigned byte.
         */
        RED,
        /**
         * A dual color channel.  In OpenGL that is identified as red and green.
         *
         * The blue values will be 0.  All blending with this texture assumes
         * alpha is 1.0. This format uses GL_RGB8 as the internal format.
         * The data type (for each component) is unsigned byte.
         */
        RED_GREEN,
        /**
         * A texture used to store a depth component.
         *
         * This format uses GL_DEPTH_COMPONENT32F as the internal format. The
         * data type (for the only component) is float.
         */
        DEPTH,
        /**
         * A texture used to store a combined depth and stencil component
         *
         * This format uses GL_DEPTH24_STENCIL8 as the internal format. The
         * data type is GL_UNSIGNED_INT_24_8, giving 24 bytes to depth and
         * 8 bits to the stencil.
         */
        DEPTH_STENCIL,
        /**
         * A texture format that has is not generally supported by GL30
         *
         * This value exists for backwards compatability reasons.  It is so
         * we can assign a PixelFormat to every Pixmap.Format
         */
        UNSUPPORTED;

        /**
         * Returns the OpenGL format associated with this pixel format
         *
         * @return the OpenGL format associated with this pixel format
         */
        public int glFormat() {
            switch(this) {
                case RGBA:
                    return GL30.GL_RGBA;
                case RGB:
                    return GL30.GL_RGB;
                case RED:
                    return GL30.GL_RED;
                case DEPTH:
                    return GL30.GL_DEPTH_COMPONENT;
                case DEPTH_STENCIL:
                    return GL30.GL_DEPTH_STENCIL;
                case UNSUPPORTED:
                    throw new GdxRuntimeException("Format not supported by GL30");
            }

            return GL30.GL_RGBA;
        }

        /**
         * Returns the internal format for the pixel format
         *
         * We have standardized internal formats for all platforms. This may not
         * be memory efficient, but it works for 90% of all use cases.
         *
         * @return the internal format for the pixel format
         */
        public int internalFormat() {
            switch (this) {
                case RGBA:
                    return GL30.GL_RGBA8;
                case RGB:
                    return GL30.GL_RGB8;
                case RED:
                    return GL30.GL_R8;
                case RED_GREEN:
                    return GL30.GL_RG8;
                case DEPTH:
                    return GL30.GL_DEPTH_COMPONENT32F;
                case DEPTH_STENCIL:
                    return GL30.GL_DEPTH24_STENCIL8;
                case UNSUPPORTED:
                    throw new GdxRuntimeException("Format not supported by GL30");
            }

            return GL30.GL_RGBA8;
        }

        /**
         * Returns the data type for the pixel format
         *
         * The data type is derived from the internal format. We have standardized
         * internal formats for all platforms. This may not be memory efficient, but
         * it works for 90% of all use cases.
         *
         * @return the data type for the pixel format
         */
        public int formatType() {
            switch (this) {
                case RGBA:
                case RGB:
                case RED:
                case RED_GREEN:
                    return GL30.GL_UNSIGNED_BYTE;
                case DEPTH:
                    return GL30.GL_FLOAT;
                case DEPTH_STENCIL:
                    return GL30.GL_UNSIGNED_INT_24_8;
                case UNSUPPORTED:
                    throw new GdxRuntimeException("Format not supported by GL30");
            }

            return GL30.GL_UNSIGNED_BYTE;
        }

        /**
         * Returns the number of bytes in a single pixel of this texture.
         *
         * @return the number of bytes in a single pixel of this texture.
         */
        public int byteSize() {
            switch (this) {
                case RGB:
                    return 3;
                case RED:
                    return 1;
                case RED_GREEN:
                    return 2;
                case RGBA:
                case DEPTH:
                case DEPTH_STENCIL:
                    return 4;
                case UNSUPPORTED:
                    return -1;
            }
            return -1;
        }

        /**
         * Returns the PixelFormat equivalent to a Pixmap.Format
         *
         * @param format	The Pixmap.Format
         *
         * @return the PixelFormat equivalent to a Pixmap.Format
         */
        public static PixelFormat gl30Format(Pixmap.Format format) {
            switch (format) {
                case Alpha:
                    return RED;
                case Intensity:
                    return RED;
                case LuminanceAlpha:
                    return RED_GREEN;
                case RGB565:
                case RGBA4444:
                    return UNSUPPORTED;
                case RGB888:
                    return RGB;
                case RGBA8888:
                    return RGBA;
            }

            return RGBA;
        }
    }

    /**
     * This class is a TextureData implementation for GL30 textures (OpenGL/ES)
     */
    private static class GL30Data implements TextureData {
        /** An internal reference to the OpenGL handle */
        private int glHandle;
        /** The texture data */
        private Buffer data;
        /** The texture width */
        private int width;
        /** The texture height */
        private int height;
        /** Whether to use mipmaps */
        private boolean mipmaps;
        /** The modern GL30 pixel format */
        private PixelFormat format;
        /** To query OpenGL information */
        private IntBuffer query = BufferUtils.newByteBuffer(4*16).asIntBuffer();

        /**
         * Creates a TextureData with the given buffer.
         *
         * The data format must match the one given.
         *
         * @param data      The texture data (size width*height*format)
         * @param width     The texture width in pixels
         * @param height    The texture height in pixels
         * @param format    The texture data format
         * @param mipMaps	Whether the texture should use mipmaps
         */
        public GL30Data(Buffer data, int width, int height, PixelFormat format, boolean mipMaps) {
            this.data = data;
            this.width  = width;
            this.height = height;
            this.format = format;
            this.mipmaps = mipMaps;
            glHandle = 0;
        }

        /**
         * Returns the {@link TextureDataType}
         *
         * @return the {@link TextureDataType}
         */
        public TextureDataType getType () {
            return TextureDataType.Custom;
        }

        /**
         * Returns whether the TextureData is prepared or not.
         *
         * @return whether the TextureData is prepared or not.
         */
        public boolean isPrepared () {
            return true;
        }

        /**
         * Returns the {@link Pixmap} of the current texture contents
         *
         * A call to {@link #prepare()} must precede a call to this method. Any internal
         * data structures created in {@link #prepare()} should be disposed of here.
         *
         * In this class, ownership of the pixmap is transfered to the caller
         *
         * @return the {@link Pixmap} of the current texture contents.
         */
        public Pixmap consumePixmap () {
            throw new GdxRuntimeException( "OpenGLES does not support data from dynamic textures." );
        }

        /**
         * Returns whether the caller of {@link #consumePixmap()} should dispose the Pixmap
         *
         * This class always transfers the ownership of the pixmap.
         *
         * @return whether the caller of {@link #consumePixmap()} should dispose the Pixmap
         */
        public boolean disposePixmap () { return true; }

        /**
         * Uploads the pixel data to the OpenGL/ES texture.
         *
         * The caller must bind an OpenGL ES texture. A call to {@link #prepare()}
         * must preceed a call to this method. Any internal data structures created in
         * {@link #prepare()} should be disposed of here.
         */
        public void consumeCustomData(int target) {
            if (width <= 0 || height <= 0) {
                throw new GdxRuntimeException( String.format( "Texture size %dx%d is not valid", width, height ) );
            }
            int error;

            if (glHandle > 0) {
                assert false : "Texture is already initialized";
                return;
            }

            // Query for handle
            Gdx.gl30.glGetIntegerv( GL30.GL_TEXTURE_BINDING_2D, query );
            glHandle = query.get();
            query.clear();

            int glFormat = format.glFormat();
            int internal = format.internalFormat();
            int datatype = format.formatType();
            Gdx.gl30.glTexImage2D( GL30.GL_TEXTURE_2D, 0, internal, width, height, 0, glFormat, datatype, data );

            error = Gdx.gl30.glGetError();
            if (error != 0) {
                Gdx.app.error( "OPENGL", String.format( "Could not initialize texture. %s", CUGLDebug.errorName( error ) ) );
                return;
            }
        }

        /**
         * Returns the width of the pixel data
         *
         * @return the width of the pixel data
         */
        public int getWidth () { return width; }

        /**
         * Returns the height of the pixel data
         *
         * @return the height of the pixel data
         */
        public int getHeight () { return height; }

        /**
         * Returns the {@link Pixmap.Format} of the pixel data
         *
         * This is a GL20 format. Not all images have a GL20 format. For images
         * that do not have a GL20 format, it will return RGBA8888 (because at
         * least the size is right). You should call {@link Texture#getFormat()}
         * instead for accurate information.
         *
         * @return the {@link Pixmap.Format} of the pixel data
         */
        public Pixmap.Format getFormat () {
            switch(format) {
                case RGB:
                    return Pixmap.Format.RGB888;
                case RED:
                    return Pixmap.Format.Alpha;
                case RED_GREEN:
                    return Pixmap.Format.LuminanceAlpha;
                default:
                    return Pixmap.Format.RGBA8888;
            }
        }

        /**
         * Returns whether to generate mipmaps or not
         *
         * @return whether to generate mipmaps or not.
         */
        public boolean useMipMaps () {
            return mipmaps;
        }
    }

    /**
     * This array is the data of a white image with 2 by 2 dimension.
     * It's used for creating a default texture when the texture is a nullptr.
     */
    private static byte[] cu_2x2_white_image = {
            // RGBA8888
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
    };

    //region Friends
    /** The blank texture corresponding to cu_2x2_white_image */
    private static CUTexture BLANK = null;

    /** The pixel format of the texture */
    private PixelFormat pixelFormat;

    /** The descriptive texture name */
    private String name;

    /** The bind point assigned to this texture (default 0) */
    private int bindpoint;

    /** To query OpenGL information */
    private IntBuffer query = BufferUtils.newByteBuffer( 4*16 ).asIntBuffer();
    //endregion

    //region Constructors
    /**
     * Creates a texture from a file with the given internal path
     *
     * Initializing a texture requires the use of the binding point at 0.
     * Any texture bound to that point will be unbound. Instead, this
     * texture will be bound in its place.
     *
     * @param internalPath	The internal path to the file
     */
    public CUTexture (String internalPath) {
        super(internalPath);
        pixelFormat = PixelFormat.gl30Format(getTextureData().getFormat());
        name = internalPath;
    }

    /**
     * Creates a texture from the given file
     *
     * Initializing a texture requires the use of the binding point at 0.
     * Any texture bound to that point will be unbound. Instead, this
     * texture will be bound in its place.
     *
     * @param file		The image file handle
     */
    public CUTexture (FileHandle file) {
        super(file);
        pixelFormat = PixelFormat.gl30Format(getTextureData().getFormat());
        name = file.toString();
    }

    /**
     * Creates a texture from the given file
     *
     * Initializing a texture requires the use of the binding point at 0.
     * Any texture bound to that point will be unbound. Instead, this
     * texture will be bound in its place.
     *
     * @param file		The image file handle
     * @param mipMaps	Whether the texture should use mipmaps
     */
    public CUTexture (FileHandle file, boolean mipMaps) {
        super(file, mipMaps);
        pixelFormat = PixelFormat.gl30Format(getTextureData().getFormat());
        name = file.toString();
    }

    /**
     * Creates a texture from the given Pixmap
     *
     * Initializing a texture requires the use of the binding point at 0.
     * Any texture bound to that point will be unbound. Instead, this
     * texture will be bound in its place.
     *
     * @param pixmap	The pixmap source
     */
    public CUTexture (Pixmap pixmap) {
        super(pixmap);
        pixelFormat = PixelFormat.gl30Format(getTextureData().getFormat());
        name = pixmap.toString();
    }

    /**
     * Creates a texture from the given Pixmap
     *
     * Initializing a texture requires the use of the binding point at 0.
     * Any texture bound to that point will be unbound. Instead, this
     * texture will be bound in its place.
     *
     * @param pixmap	The pixmap source
     * @param mipMaps	Whether the texture should use mipmaps
     */
    public CUTexture (Pixmap pixmap, boolean mipMaps) {
        super(pixmap, mipMaps);
        pixelFormat = PixelFormat.gl30Format(getTextureData().getFormat());
        name = pixmap.toString();
    }

    /**
     * Creates an empty RGBA texture with the given dimensions.
     *
     * Initializing a texture requires the use of the binding point at 0.
     * Any texture bound to that point will be unbound. Instead, this
     * texture will be bound in its place.
     *
     * You must use the set() method to load data into the texture.
     *
     * @param width     The texture width in pixels
     * @param height    The texture height in pixels
     */
    public CUTexture(int width, int height) {
        this(null, width, height, PixelFormat.RGBA,false);
    }

    /**
     * Creates an empty texture with the given dimensions.
     *
     * Initializing a texture requires the use of the binding point at 0.
     * Any texture bound to that point will be unbound. Instead, this
     * texture will be bound in its place.
     *
     * You must use the set() method to load data into the texture.
     *
     * @param width     The texture width in pixels
     * @param height    The texture height in pixels
     * @param format    The texture data format
     */
    public CUTexture(int width, int height, PixelFormat format) {
        this(null, width, height, format,false);
    }

    /**
     * Creates a texture with the given data.
     *
     * Initializing a texture requires the use of the binding point at 0.
     * Any texture bound to that point will be unbound. Instead, this
     * texture will be bound in its place.
     *
     * The format is assumed to be RGBA.
     *
     * @param data      The texture data (size width*height*format)
     * @param width     The texture width in pixels
     * @param height    The texture height in pixels
     */
    public CUTexture(Buffer data, int width, int height) {
        this(data,width,height,PixelFormat.RGBA,false);
    }

    /**
     * Creates a texture with the given data.
     *
     * Initializing a texture requires the use of the binding point at 0.
     * Any texture bound to that point will be unbound. Instead, this
     * texture will be bound in its place.
     *
     * The data format must match the one given.
     *
     * @param data      The texture data (size width*height*format)
     * @param width     The texture width in pixels
     * @param height    The texture height in pixels
     * @param format    The texture data format
     */
    public CUTexture(Buffer data, int width, int height, PixelFormat format) {
        this(data,width,height,format,false);
    }

    /**
     * Creates a texture with the given data.
     *
     * Initializing a texture requires the use of the binding point at 0.
     * Any texture bound to that point will be unbound. Instead, this
     * texture will be bound in its place.
     *
     * The data format must match the one given.
     *
     * @param data      The texture data (size width*height*format)
     * @param width     The texture width in pixels
     * @param height    The texture height in pixels
     * @param format    The texture data format
     * @param mipMaps	Whether the texture should use mipmaps
     */
    public CUTexture(Buffer data, int width, int height, PixelFormat format, boolean mipMaps) {
        super( new GL30Data( data, width, height, format, mipMaps ) );
        pixelFormat = format;
    }

    /**
     * Clean up shader on Garbage collection
     */
    @Override
    protected void finalize() throws Throwable {
        dispose();
        super.finalize();
    }

    /**
     * Returns a blank texture that can be used to make solid shapes.
     *
     * Allocating a texture requires the use of texture offset 0.  Any texture
     * bound to that offset will be unbound.  In addition, once initialization
     * is done, this texture will not longer be bound as well.
     *
     * This is the texture used by {@link CUSpriteBatch} when the active texture
     * is nullptr. It is a 2x2 texture of all white pixels. Using this texture
     * means that all shapes and outlines will be drawn with a solid color.
     *
     * This texture is a singleton. There is only one of it.  All calls to
     * this method will return a reference to the same object.
     *
     * @return a blank texture that can be used to make solid shapes.
     */
    public static CUTexture getBlank() {
        if (BLANK == null) {
            ByteBuffer buffer = BufferUtils.newByteBuffer(cu_2x2_white_image.length);
            BufferUtils.copy(cu_2x2_white_image,0, buffer, cu_2x2_white_image.length);
            BLANK = new CUTexture(buffer, 2, 2);
            BLANK.setWrap(TextureWrap.Repeat,TextureWrap.Repeat);
        }
        return BLANK;
    }
    //endregion

    //region Data Access
    /**
     * Sets this texture to have the contents of the given buffer.
     *
     * The buffer must have the correct data format. In addition, the buffer must
     * be size width*height*bytesize.  See {@link #getByteSize} for a description
     * of the latter.
     *
     * This method is only successful if the texture is (1) not managed and (2) a
     * supported PixelFormat.
     *
     * Calls to this method will make the texture active (bound to its bindpoint).
     *
     * @param data  The buffer to read into the texture
     *
     * @return a reference to this (modified) texture for chaining.
     */
    public Texture setContents(Buffer data) {
        if (getTextureData().isManaged() || pixelFormat == PixelFormat.UNSUPPORTED ){
            throw new GdxRuntimeException("Texture does not support direct writes");
        }

        bind();
        int glFormat = pixelFormat.glFormat();
        int internal = pixelFormat.internalFormat();
        int datatype = pixelFormat.formatType();
        Gdx.gl30.glTexImage2D(GL30.GL_TEXTURE_2D, 0, glFormat, getWidth(), getHeight(),
                0, internal, datatype, data);
        return this;
    }

    /**
     * Sets this texture to have the contents of the given buffer.
     *
     * The buffer must have the correct data format. In addition, the buffer must
     * be size width*height*bytesize.  See {@link #getByteSize} for a description
     * of the latter.
     *
     * This static method provides some limited support for legacy textures.  This
     * method is only successful if the texture is (1) not managed and (2) a
     * supported PixelFormat.
     *
     * Calls to this method will make the texture bound.
     *
     * @param data  The buffer to read into the texture
     */
    public static void setContents(Texture texture, Buffer data) {
        PixelFormat format = PixelFormat.gl30Format( texture.getTextureData().getFormat() );
        if (texture.getTextureData().isManaged() || format == PixelFormat.UNSUPPORTED ){
            throw new GdxRuntimeException("Texture does not support direct writes");
        }

        texture.bind();
        int glFormat = format.glFormat();
        int internal = format.internalFormat();
        int datatype = format.formatType();
        Gdx.gl30.glTexImage2D(GL30.GL_TEXTURE_2D, 0, glFormat, texture.getWidth(), texture.getHeight(),
                0, internal, datatype, data);
    }
    //endregion

    //region Attributes
    /**
     * Sets the name of this texture.
     *
     * A name is a user-defined way of identifying a texture.  Subtextures are
     * permitted to have different names than their parents.
     *
     * @param name  The name of this texture.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the name of this texture.
     *
     * A name is a user-defined way of identifying a texture.  Subtextures are
     * permitted to have different names than their parents.
     *
     * @return the name of this texture.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the number of bytes in a single pixel of this texture.
     *
     * @return the number of bytes in a single pixel of this texture.
     */
    public int getByteSize() {
        switch (pixelFormat) {
            case RGB:
                return 3;
            case RED:
                return 1;
            case RED_GREEN:
                return 2;
            case RGBA:
            case DEPTH:
            case DEPTH_STENCIL:
                return 4;
            case UNSUPPORTED:
                return -1;
        }
        return -1;
    }


    /**
     * Returns the data format of this texture.
     *
     * The data format determines what type of data can be assigned to this
     * texture.
     *
     * @return the data format of this texture.
     */
    public PixelFormat getFormat() {
        return pixelFormat;
    }
    //endregion

    //region Binding
    /**
     * Returns the OpenGL buffer for this texture.
     *
     * The buffer is a value assigned by OpenGL when the texture was allocated.
     * This method will return 0 if the texture is not initialized.
     *
     * @return the OpenGL buffer for this texture.
     */
    public int getBuffer() {
        return glHandle;
    }

    /**
     * Returns the bind point for this texture.
     *
     * Textures and shaders have a many-to-many relationship. This means that
     * connecting them requires an intermediate table. The positions in this
     * table are called bind points. A texture is associated with a bind point
     * and a shader associates a bind point with a sampler variable. That sampler
     * variable then pulls data from the appropriate texture. By default this
     * value is 0.
     *
     * @return the bind point for for this texture.
     */
    public int getBindPoint() {
        return bindpoint;
    }

    /**
     * Sets the bind point for this texture.
     *
     * Textures and shaders have a many-to-many relationship. This means that
     * connecting them requires an intermediate table. The positions in this
     * table are called bind points. A texture is associated with a bind point
     * and a shader associates a bind point with a sampler variable. That
     * sampler variable then pulls data from the appropriate texture. By default
     * this value is 0.
     *
     * This method differs from {@link #bind(int)} in that the texture does not
     * need to be active to call this method. This method only sets the bind point
     * preference and does not actually {@link #bind} the texture. However, if
     * unbind is true, and the texture is bound to another bind point, then
     * it will be unbound from that point (returning the active texture back
     * to its original status.
     *
     * @param point		The bind point for this texture.
     * @param unbind	Whether to unbind this texture from its previous bind point.
     */
    void setBindPoint(int point, boolean unbind) {
        if (bindpoint == point) {
            return;
        }
        if (unbind) {
            Gdx.gl30.glGetIntegerv( GL30.GL_ACTIVE_TEXTURE, query );
            int orig = query.get();
            query.clear();

            if (orig != bindpoint + GL30.GL_TEXTURE0) {
                Gdx.gl30.glActiveTexture( GL30.GL_TEXTURE0 + bindpoint );
            }
            Gdx.gl30.glGetIntegerv( GL30.GL_TEXTURE_BINDING_2D, query );
            int bind = query.get();
            query.clear();
            if (bind == glHandle) {
                Gdx.gl30.glBindTexture( GL30.GL_TEXTURE_2D, 0 );
            }
            if (orig != bindpoint + GL30.GL_TEXTURE0) {
                Gdx.gl30.glActiveTexture( orig );
            }
            int error = Gdx.gl30.glGetError();
            assert error == GL30.GL_NO_ERROR : "Texture: " + CUGLDebug.errorName( error );
        }
        bindpoint = point;
    }

    /**
     * Binds this texture to its bind point, making it active.
     *
     * Because of legacy issues with OpenGL, this method actually does two
     * things. It attaches the block to the correct bind point, as defined by
     * {@link #setBindPoint}. It also makes this the active texture, capable of
     * receiving OpenGL commands.
     *
     * Unlike {@link CUUniformBuffer} is not possible to bind a texture without
     * making it the active texture.  Therefore, any existing texture will
     * be deactivated, no matter its bind point. So this texture can be unbound
     * without a call to {@link #unbind}.
     *
     * This call is reentrant. If can be safely called multiple times.
     */
    public void bind () {
        Gdx.gl30.glActiveTexture(GL30.GL_TEXTURE0+bindpoint);
        Gdx.gl30.glBindTexture(GL30.GL_TEXTURE_2D,glHandle);
    }

    /**
     * Binds this texture to the given bind point, making it active.
     *
     * Because of legacy issues with OpenGL, this method actually does two
     * things. It attaches the block to the correct bind point, as defined by
     * {@link #setBindPoint}. It also makes this the active texture, capable of
     * receiving OpenGL commands.
     *
     * Unlike {@link CUUniformBuffer} is not possible to bind a texture without
     * making it the active texture.  Therefore, any existing texture will
     * be deactivated, no matter its bind point. So this texture can be unbound
     * without a call to {@link #unbind}.
     *
     * Once this method is called, future calls to {@link #bind()} will bind to
     * this same bindpoint.
     *
     * This call is reentrant. If can be safely called multiple times.
     *
     * @param bindpoint	The bindpoint (0 to MAX_TEXTURE_UNITS).
     */
    public void bind (int bindpoint) {
        if (bindpoint != this.bindpoint) {
            this.bindpoint = bindpoint;
        }
        Gdx.gl30.glActiveTexture(GL30.GL_TEXTURE0+bindpoint);
        Gdx.gl30.glBindTexture(GL30.GL_TEXTURE_2D,glHandle);
    }

    /**
     * Unbinds this texture, making it neither bound nor active.
     *
     * If another texture is active, calling this method will not effect that
     * texture.  But once unbound, the shader will no longer receive data from
     * the bind point for this texture.  A new texture must be bound for the
     * shader to receive data.  In addition this call will make the bindpoint
     * of this texture the active texture slot.
     *
     * Unlike {@link CUUniformBuffer}, it is not possible to unbind a texture
     * without deactivating it. If called while this texture is NOT active,
     * it will make its bindpoint the active texture slot.
     *
     * This call is reentrant.  If can be safely called multiple times.
     */
    void unbind() {
        unbind(false);
    }

    /**
     * Unbinds this texture, making it neither bound nor active.
     *
     * If another texture is active, calling this method will not effect that
     * texture.  But once unbound, the shader will no longer receive data from
     * the bind point for this texture.  A new texture must be bound for the
     * shader to receive data.  In addition this call will make the bindpoint
     * of this texture the active texture slot.
     *
     * Unlike {@link CUUniformBuffer}, it is not possible to unbind a texture
     * without deactivating it. If called while this texture is NOT active,
     * it will make its bindpoint the active texture slot, unless restore
     * is true.
     *
     * This call is reentrant.  If can be safely called multiple times.
     *
     * @param restore	Whether to restore the active texture slot
     */
    void unbind(boolean restore) {
        int orig = 0;
        if (restore) {
            Gdx.gl30.glGetIntegerv( GL30.GL_ACTIVE_TEXTURE, query );
            orig = query.get();
            query.clear();
        }
        Gdx.gl30.glBindTexture(GL30.GL_TEXTURE_2D, 0);
        if (restore && orig != bindpoint+GL30.GL_TEXTURE0) {
            Gdx.gl30.glActiveTexture(orig);
        }
    }

    /**
     * Returns true if this texture is currently bound.
     *
     * A texture is bound if it is attached to a bind point. That means that
     * the shader will pull sampler data for that bind point from this texture.
     *
     * A texture can be bound without being active.  This happens when another
     * texture has subsequently been bound, but to a different bind point.
     *
     * @return true if this texture is currently bound.
     */
    public boolean isBound() {
        int orig;
        Gdx.gl30.glGetIntegerv(GL30.GL_ACTIVE_TEXTURE,query);
        orig = query.get();
        query.clear();
        if (orig != bindpoint+GL30.GL_TEXTURE0) {
            Gdx.gl30.glActiveTexture(GL30.GL_TEXTURE0+bindpoint);
        }

        int bind;
        Gdx.gl30.glGetIntegerv(GL30.GL_TEXTURE_BINDING_2D, query);
        bind = query.get();
        query.clear();
        boolean result = (bind == glHandle);
        if (orig != bindpoint+GL30.GL_TEXTURE0) {
            Gdx.gl30.glActiveTexture(orig);
        }
        return result;
    }

    /**
     * Returns true if this texture is currently active.
     *
     * An active uniform block is the one that receives data from OpenGL
     * calls (such as glTexImage2D). Many of the setter-like methods in
     * this class require the texture to be active to work properly (because
     * of how OpenGL calls are wrapped).
     *
     * Unlike {@link CUUniformBuffer}, it is not possible for a texture to be
     * active without being bound. To activate a texture simply call the
     * {@link #bind} method.
     *
     * @return true if this texture is currently active.
     */
    public boolean isActive() {
        int orig;
        Gdx.gl30.glGetIntegerv(GL30.GL_ACTIVE_TEXTURE,query);
        orig = query.get();
        query.clear();
        if (orig != bindpoint+GL30.GL_TEXTURE0) {
            return false;
        }

        int bind;
        Gdx.gl30.glGetIntegerv(GL30.GL_TEXTURE_BINDING_2D, query);
        bind = query.get();
        query.clear();
        return (bind == glHandle);
    }
    //endregion

    //region Conversions
    /**
     * Returns a string representation of this texture for debugging purposes.
     *
     * If verbose is true, the string will include class information.  This
     * allows us to unambiguously identify the class.
     *
     * @return a string representation of this texture for debugging purposes.
     */
    public String toString() {
        String result = "CUTexture[";
        result += "data: " + getName() + ",";
        result += "w:" + getWidth() + ",";
        result += "h:" + getWidth();
        result += "]";
        return result;
    }

    //endregion
}
