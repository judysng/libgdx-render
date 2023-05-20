package edu.cornell.gdiac.render;

import java.nio.*;
import java.util.HashMap;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;

import com.badlogic.gdx.math.*;
import com.badlogic.gdx.graphics.Color;
import org.w3c.dom.Attr;

public class CUVertexBuffer implements Disposable {

    /**
     * A data type for keeping track of attribute data.
     *
     * This class is necessary since we are allowing the vertex
     * buffer to specify attributes before hooking it up to the shader.
     * This type is used to initialize the attribute hooks as soon as
     * the shader is attached.
     */
    class AttribData {
        /** The attribute size */
        public int size;
        /** The attribute type (as specified in OpenGL) */
        public int type;
        /** Whether the attribute is normalized (floating points only) */
        public boolean norm;
        /** The offset of the attribute in the vertex buffer */
        public int offset;

        public AttribData() {}
    };

    /** The data stride of this buffer (0 if there is only one attribute) */
    public int stride;

    /** Maximum number of vertices */
    public int vertMax;

    /** The array buffer for drawing a the shape */
    IntBuffer vertArray;
    /** The vertex buffer handle for drawing a shape */
    IntBuffer vertBuffer;
    /** The vertex buffer for drawing a shape */
    ByteBuffer vertData;
    /** Corresponding float buffer for the vert buffer */
    FloatBuffer floatVertData;
    /** The index buffer handle for drawing a shape */
    IntBuffer indxBuffer;
    /** The index buffer data for drawing a shape */
    ByteBuffer indxData;
    /** Corresponding short buffer for the index buffer */
    ShortBuffer shortIndxData;

    /** The shader currently attached to this vertex buffer */
    ShaderProgram shader;

    /** The enabled attributes */
    ObjectMap<String, Boolean> enabled;
    /** The settings for each attribute */
    ObjectMap<String, AttribData> attributes;

    /**
     * Constructor for Vertex Buffer.
     * @param stride The number of bytes between each vertex entry in the buffer
     * @param vertMax The maximum number of vertices in the buffer
     */
    public CUVertexBuffer (int stride, int vertMax, int indxMax) {
        GL30 gl = Gdx.gl30;
        this.stride = stride;
        this.vertMax = vertMax;
        shader = null;

        vertArray = BufferUtils.newIntBuffer(1);
        vertArray.put(0, 0);
        vertBuffer = BufferUtils.newIntBuffer(1);
        vertBuffer.put(0, 0);
        indxBuffer = BufferUtils.newIntBuffer(1);
        indxBuffer.put(0, 0);

        vertData = BufferUtils.newUnsafeByteBuffer(stride * vertMax);
        floatVertData = vertData.asFloatBuffer();

        indxData = BufferUtils.newByteBuffer(indxMax * 2); // *2 because 2 is short size in bytes
        shortIndxData = indxData.asShortBuffer();

        enabled = new ObjectMap<>();
        attributes = new ObjectMap<>();

        gl.glGenVertexArrays (1, vertArray);
        if (vertArray == null) {
            throw new RuntimeException("Could not create vertex array.");
        }

        // Generate the buffers
        gl.glGenBuffers(1, vertBuffer);
        if (vertBuffer == null) {
            gl.glDeleteVertexArrays(1, vertArray);
            throw new RuntimeException("Could not create vertex buffer.");
        }

        gl.glGenBuffers(1, indxBuffer);
        if (indxBuffer == null) {
            gl.glDeleteVertexArrays(1,vertArray);
            gl.glDeleteBuffers(1,vertBuffer);
            throw new RuntimeException("Could not create index buffer.");
        }
    }

    @Override
    protected void finalize() throws Throwable {
//        dispose();
    }

    public void dispose() {
        GL30 gl = Gdx.gl30;
        gl.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
        if (vertBuffer == null) {
            return;
        }
        if (enabled != null) {
            enabled.clear();
            enabled = null;
        }
        if (attributes != null) {
            attributes.clear();
            attributes = null;
        }
        if (indxBuffer != null) {
            gl.glDeleteBuffers(1, indxBuffer);
            indxBuffer.put(0, 0);
            indxBuffer = null;
        }
        if (vertBuffer != null) {
            gl.glDeleteBuffers(1, vertBuffer);
            vertBuffer.put(0, 0);
            vertBuffer = null;
        }
        if (vertArray != null) {
            gl.glDeleteVertexArrays(1, vertArray);
            vertArray.put(0, 0);
            vertArray = null;
        }
        if (vertData != null) {
            BufferUtils.disposeUnsafeByteBuffer(vertData);
            vertData = null;
        }
        if (indxData != null) {
            indxData = null;
        }
        shader = null;
        stride = 0;
    }

    /**
     * Binds this vertex buffer, making it active.
     *
     * If this vertex buffer has an attached shader, this will bind the shader
     * as well. Once bound, all vertex data and uniforms will be sent to
     * the associated shader.
     *
     * A vertex buffer can be bound without being attached to a shader.  However,
     * if it is actively attached to a shader, this method will bind that shader
     * as well.
     */
    public void bind() {
        GL30 gl = Gdx.gl30;
        assert(vertBuffer != null) : "VertexBuffer has not been initialized.";
        gl.glBindVertexArray(vertArray.get(0));
        gl.glBindBuffer(GL30.GL_ARRAY_BUFFER, vertBuffer.get(0));
        gl.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, indxBuffer.get(0));
        if (shader != null) {
            shader.bind();
        }
    }

    /**
     * Unbinds this vertex buffer, making it no longer active.
     *
     * A vertex buffer can be unbound without being attached to a shader.  Furthermore,
     * if it is actively attached to a shader, this method will NOT unbind the shader.
     * This allows for fast(er) switching between buffers of the same shader.
     *
     * Once unbound, all vertex data and uniforms will be ignored.  In addition, all
     * uniforms and samplers are potentially invalidated.  These values should be
     * set again when the vertex buffer is next bound.
     */
    public void unbind() {
        GL30 gl = Gdx.gl30;
        if (isBound()) {
            gl.glBindBuffer( GL30.GL_ELEMENT_ARRAY_BUFFER, 0);
            gl.glBindBuffer( GL30.GL_ARRAY_BUFFER, 0);
            gl.glBindVertexArray(0);
        }
    }

    /**
     * Attaches the given shader to this vertex buffer.
     *
     * This method will link all enabled attributes in this vertex buffer
     * (warning about any attributes that are missing from the shader).
     * It will also immediately bind both the vertex buffer and the
     * shader, making them ready to use.
     *
     * @param shader    The shader to detach
     */
    public void attach(ShaderProgram shader) {
        assert(shader != null) : "Attempting to attach a null shader";
        GL30 gl = Gdx.gl30;

        if (this.shader != shader) {
            this.shader = shader;
            bind();

            for (ObjectMap.Entry<String, AttribData> attrib : attributes) {
                String key = attrib.key;
                AttribData attribute = attrib.value;

                int pos = gl.glGetAttribLocation(shader.getHandle(), key);
                if (pos == -1) {
//                    CUWarn("Active shader has no attribute %s", name.c_str());
                    System.out.println("Active shader has no attribute " + key);
                } else if (enabled.get(key)) {
                    gl.glEnableVertexAttribArray(pos);
                    gl.glVertexAttribPointer(pos, attribute.size, attribute.type, attribute.norm, stride, attribute.offset);
                } else {
                    gl.glDisableVertexAttribArray(pos);
                }
            }

            int error = gl.glGetError();
            assert error == gl.GL_NO_ERROR : "VertexBuffer error";
//            CUAssertLog(error == GL_NO_ERROR, "VertexBuffer: %s", gl_error_name(error).c_str());
        } else {
            bind();
        }
    }

    /**
     * Returns the previously active shader, after detaching it.
     *
     * This method will unbind the vertex buffer, but not the shader.
     *
     * @return  The previously active shader (or null if none)
     */
    public ShaderProgram detach() {
        ShaderProgram result = shader;
        unbind();
        shader = null;
        return result;
    }

    /**
     * Returns true if this vertex is currently bound.
     *
     * @return true if this vertex is currently bound.
     */
    public boolean isBound() {
        GL30 gl = Gdx.gl30;
        IntBuffer vao = BufferUtils.newIntBuffer(1);
        gl.glGetIntegerv(gl.GL_VERTEX_ARRAY_BINDING, vao);
        return vao.get(0) == vertArray.get(0);
    }

    /**
     * Loads the given vertex buffer with data.
     *
     * The data loaded is the data that will be used at the next draw command.
     * Frequent reloading of data and/or indices is to be discouraged (though it
     * is faster than swapping to another vertex buffer). Instead, data and indices
     * should be loaded once (if possible) and draw calls should make use of the
     * offset parameter.
     *
     * The data loaded is expected to have the size of the vertex buffer stride.
     * If it does not, strange things will happen.
     *
     * The data usage is one of GL_STATIC_DRAW, GL_STREAM_DRAW, or GL_DYNAMIC_DRAW.
     * Static drawing should be reserved for vertices and/or indices that do not
     * change (so all animation happens in uniforms). Given the high speed of
     * CPU processing, this approach should only be taken for large meshes that
     * can amortize the uniform changes.  For quads and other simple meshes,
     * you should always choose GL_STREAM_DRAW.
     *
     * This method will only succeed if this buffer is actively bound.
     *
     * @param data  The data to load
     * @param size  The number of vertices to load
     * @param usage The type of data load
     */
    public void loadVertexData(float[] data, int size, int usage) {
        assert isBound() : "Vertex buffer is not bound";
        GL30 gl = Gdx.gl30;

        BufferUtils.copy(data, vertData, size, 0);
        gl.glBufferData(gl.GL_ARRAY_BUFFER, stride * size, vertData, usage);

        int error = gl.glGetError();
        assert (error == gl.GL_NO_ERROR) : "VertexBuffer Error";
    }

    public void loadVertexData(float[] data, int size) {
        loadVertexData(data, size, GL30.GL_STREAM_DRAW);
    }

    /**
     * Loads the given vertex buffer with indices.
     *
     * The indices loaded are those that will be used at the next draw command.
     * Frequent reloading of data and/or indices is to be discouraged (though it
     * is faster than swapping to another vertex buffer). Instead, data and indices
     * should be loaded once (if possible) and draw calls should make use of the
     * offset parameter.
     *
     * The indices loaded are expected to refer to valid vertex positions. If they
     * do not, strange things will happen.
     *
     * The data usage is one of GL_STATIC_DRAW, GL_STREAM_DRAW, or GL_DYNAMIC_DRAW.
     * Static drawing should be reserved for vertices and/or indices that do not
     * change (so all animation happens in uniforms). Given the high speed of
     * CPU processing, this approach should only be taken for large meshes that
     * can amortize the uniform changes.  For quads and other simple meshes,
     * you should always choose GL_STREAM_DRAW and push as much computation to the
     * CPU as possible.
     *
     * This method will only succeed if this buffer is actively bound.
     *
     * @param data  The indices to load
     * @param size  The number of indices to load
     * @param usage The type of data load
     */
    public void loadIndexData(short[] data, int size, int usage) {
        assert isBound() : "Vertex buffer is not bound";
        GL30 gl = Gdx.gl30;
        BufferUtils.copy(data, 0, indxData, size);
        gl.glBufferData( gl.GL_ELEMENT_ARRAY_BUFFER, 2 * size, indxData, usage );
        int error = gl.glGetError();
        assert (error == gl.GL_NO_ERROR) : "VertexBuffer Error";
    }

    public void loadIndexData(short[] data, int size) {
        loadIndexData(data, size, GL30.GL_STREAM_DRAW);
    }

    public void resetIndexBuffer(int size) {
        ((Buffer)shortIndxData).position(0);
        ((Buffer)shortIndxData).limit(size);
    }

    /**
     * Draws to the active framebuffer using this vertex buffer
     *
     * Any call to this command will use the current texture and uniforms. If
     * the texture and/or uniforms need to be changed, then this draw command
     * will need to be broken up into chunks. Use the optional parameter
     * offset to chunk up the draw calls without having to reload data.
     *
     * The drawing mode can be any of  GL_POINTS, GL_LINE_STRIP, GL_LINE_LOOP,
     * GL_LINES, GL_TRIANGLE_STRIP, GL_TRIANGLE_FAN or GL_TRIANGLES.  These
     * are the only modes accepted by both OpenGL and OpenGLES. See the OpenGL
     * documentation for the number of indices required for each type.  In
     * practice the Poly2 class is designed to support GL_POINTS,
     * GL_LINES, and GL_TRIANGLES only.
     *
     * This method will only succeed if this buffer is actively bound.
     *
     * @param mode      The OpenGLES drawing mode
     * @param count     The number of vertices to draw
     * @param offset    The initial index to start with
     */
    public void draw(int mode, int count, int offset) {
        assert(isBound()) : "Vertex buffer is not bound";
        GL30 gl = Gdx.gl30;
        gl.glDrawElements(mode, count, gl.GL_UNSIGNED_SHORT, offset * 2);
    }

    /**
     * Draws to the active framebuffer using this vertex buffer
     *
     * This version of drawing supports instancing.This allows you to draw the
     * the same vertices multiple times, with slightly different uniforms each
     * time. While the use of this is limited -- there is an 8096 byte limit on
     * uniforms for more shaders -- it can speed up rendering in some special
     * cases. See the documentation of glDrawElementsInstanced for how to properly
     * leverage instancing.
     *
     * Any call to this command will use the current texture and uniforms. If
     * the texture and/or uniforms need to be changed, then this draw command
     * will need to be broken up into chunks. Use the optional parameter
     * offset to chunk up the draw calls without having to reload data.
     *
     * The drawing mode can be any of  GL_POINTS, GL_LINE_STRIP, GL_LINE_LOOP,
     * GL_LINES, GL_TRIANGLE_STRIP, GL_TRIANGLE_FAN or GL_TRIANGLES.  These
     * are the only modes accepted by both OpenGL and OpenGLES. See the OpenGL
     * documentation for the number of indices required for each type.  In
     * practice the Poly2 class is designed to support GL_POINTS,
     * GL_LINES, and GL_TRIANGLES only.
     *
     * This method will only succeed if this buffer is actively bound.
     *
     * @param mode      The OpenGLES drawing mode
     * @param count     The number of vertices to draw
     * @param instance The number of instances to draw
     * @param offset    The initial index to start with
     */
    public void drawInstanced(int mode, int count, int instance, int offset) {
        //CUAssertLog(isBound(), "Vertex buffer is not bound"); // Problems on android emulator for now
        GL30 gl = Gdx.gl30;
        gl.glDrawElementsInstanced(mode, count, gl.GL_UNSIGNED_INT, offset * 4, instance);
    }

    /**
     * Defines the (periodic) position for the given attribute in this vertex buffer.
     *
     * Since a vertex buffer is often an interleaving of multiple attributes, the
     * associated shader needs to know how to map data to the shader
     * attributes.  That is the purpose of this method.
     *
     * However, this method can be called even when a shader is not attached
     * to this buffer.  The values will be cached and applied when a shader is
     * attached.  This allows a vertex buffer to swap (compatible) shaders
     * with little additional code.
     *
     *@param name   The name of the attribute
     *@param size   The number of components per vertex
     *@param type   The data type per component
     *@param norm   Whether the data values are normalized (floating point only)
     *@param offset The offset of the first component in the buffer
     */
    public void setupAttribute(String name, int size, int type,
                               boolean norm, int offset) {
        GL30 gl = Gdx.gl30;
        AttribData data = new AttribData();
        data.size = size;
        data.norm = norm;
        data.type = type;
        data.offset = offset;
        attributes.put(name, data);
        enabled.put(name, true);

        if (shader != null) {
            shader.bind();
            int pos = gl.glGetAttribLocation(shader.getHandle(), name);
            if (pos == -1) {
//                CUWarn("Active shader has no attribute %s", name.c_str());
                System.out.println("Active shader has no attribute " + name);
            } else {
                gl.glEnableVertexAttribArray(pos);
                gl.glVertexAttribPointer(pos,data.size,data.type,data.norm,stride,data.offset);
            }

            int error = gl.glGetError();
            assert (error == gl.GL_NO_ERROR) : "VertexBuffer Error";
        }
    }

    /**
     * Enables the given attribute
     *
     * Attributes are immediately enabled once they are set-up.  This method
     * is only needed if the attribute was previously disabled.  It will have
     * no effect if the active shader does not support this attribute.
     *
     * @param name  The attribute to enable.
     */
    public void enableAttribute(String name) {
        assert (enabled.containsKey(name)) : "Vertex buffer has no attribute " + name;
        assert (isBound()) : "Vertex buffer is not bound.";
        GL30 gl = Gdx.gl30;

        if (!enabled.get(name)) {
            enabled.put(name, true);
            if (shader != null) {
                int locale = shader.getUniformLocation(name);
                gl.glEnableVertexAttribArray(locale);
            }
        }
    }

    /**
     * Disables the given attribute
     *
     * Attributes are immediately enabled once they are set-up.  This method
     * allows you to temporarily turn off an attribute.  If that attribute is
     * required by the shader, it will use the default value for the type instead.
     *
     * @param name  The attribute to disable.
     */
    public void disableAttribute(String name) {
        assert (enabled.containsKey(name)) : "Vertex buffer has no attribute " + name;
        assert (isBound()) : "Vertex buffer is not bound.";
        GL30 gl = Gdx.gl30;
        if (enabled.get(name)) {
            enabled.put(name, false);
            if (shader != null) {
                int locale = shader.getUniformLocation(name);
                gl.glDisableVertexAttribArray(locale);
            }
        }
    }
}

