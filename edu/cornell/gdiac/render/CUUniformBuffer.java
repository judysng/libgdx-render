package edu.cornell.gdiac.render;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class CUUniformBuffer implements Disposable {
    /** The OpenGL uniform buffer; 0 is not allocated. */
    private IntBuffer dataBuffer;
    /** The number of blocks assigned to the uniform buffer. */
    private int blockCount;
    /** The active uniform block for this buffer. */
    private int blockPntr;
    /** The capacity of a single block in the uniform buffer. */
    private int blockSize;
    /** The alignment stride of a single block. */
    private int blockStride;
    /** The bind point associated with this buffer (default 0) */
    private int bindpoint;
    /** Boolean tracks whether this buffer is bound (unreliable because OpenGL) */
    private boolean isbound  = false;
    /** An underlying byte buffer to manage the uniform data */
    private ByteBuffer byteBuffer;
    /** The draw type for this buffer */
    private int drawtype;
    /** Whether the byte buffer flushes automatically */
    private boolean autoflush;
    /** Whether the byte buffer must be flushed to the graphics card */
    private boolean dirty;
    /** A mapping of struct names to their std140 offsets */
    private HashMap<String, Integer> offsets;
    /** The decriptive buffer name */
    private String name;
    /** Temporary data */
    private final float[] tempdata = new float[16];
    /** Whether this class has been initialized, for finalizer */
    private boolean initialized;

    /**
     * Creates an uninitialized uniform buffer.
     *
     * You must initialize the uniform buffer to allocate memory.
     */
    public CUUniformBuffer() {
        dataBuffer = BufferUtils.newIntBuffer(1);
        dataBuffer.put(0, 0);
        blockCount = 0;
        blockPntr = 0;
        blockSize = 0;
        blockStride = 0;
        bindpoint = 0;
        autoflush = false;
        dirty = false;
        name = "";
        byteBuffer = null;
        drawtype = GL30.GL_STREAM_DRAW;
        offsets = new HashMap<>();
        initialized = true;
    }

    /**
     * Initializes this uniform buffer to support multiple blocks of the given capacity.
     *
     * The block capacity is measured in bytes.  In std140 format, all scalars are
     * 4 bytes, vectors are 8 or 16 bytes, and matrices are treated as an array of
     * 8 or 16 byte column vectors.
     *
     * Keep in mind that uniform buffer blocks must be aligned, and so this may take
     * significantly more memory than the number of blocks times the capacity. If the
     * graphics card cannot support that many blocks, this method will return false.
     *
     * @param capacity  The block capacity in bytes
     * @param blocks    The number of blocks to support
     *
     * @return true if initialization was successful.
     */
    public CUUniformBuffer(int capacity, int blocks) {
        this();
        assert blocks != 0 : "Block count must be nonzero";
        GL30 gl = Gdx.gl30;
        blockCount = blocks;
        blockSize = capacity;

        IntBuffer value = BufferUtils.newIntBuffer(1);
        gl.glGetIntegerv(GL30.GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT, value);
        while (blockStride < blockSize) {
            blockStride += value.get(0);
        }


        // Quit if the memory request is too high
        gl.glGetIntegerv(GL30.GL_MAX_UNIFORM_BLOCK_SIZE, value);
        if (blockStride > value.get(0)) {
            blockSize = 0;
            blockStride = 0;
            blockCount = 0;
            throw new GdxRuntimeException(String.format("Capacity exceeds maximum value of %d bytes",value));
        }

        int error;
        gl.glGenBuffers(1, dataBuffer);
        if (dataBuffer.get(0) == 0) {
            error = Gdx.gl30.glGetError();
            throw new GdxRuntimeException(String.format("Could not create uniform buffer. %s", CUGLDebug.errorName(error)));
        }

        byteBuffer = BufferUtils.newUnsafeByteBuffer(blockStride * blockCount);

        gl.glBindBuffer(GL30.GL_UNIFORM_BUFFER, dataBuffer.get(0));
        gl.glBufferData(GL30.GL_UNIFORM_BUFFER, blockStride * blockCount, null, drawtype);
        error = gl.glGetError();
        if (error != 0) {
            gl.glDeleteBuffers(1, dataBuffer);
            dataBuffer.put(0, 0);
            throw new GdxRuntimeException("Could not create uniform buffer");
        }

        gl.glBindBuffer(GL30.GL_UNIFORM_BUFFER, 0);
        System.out.println(byteBuffer);

        offsets = new HashMap<>();
    }

    @Override
    protected void finalize() throws Throwable {
        if (initialized) {
            dispose();
            super.finalize();
        }
    }

    public void dispose () {
        GL30 gl = Gdx.gl30;
        if (dataBuffer != null) {
            gl.glDeleteBuffers(1, dataBuffer);
            dataBuffer.put(0, 0);
            dataBuffer = null;
        }
        if (byteBuffer !=null) {
            BufferUtils.disposeUnsafeByteBuffer(byteBuffer);
            byteBuffer = null;
        }
        name = "";
        if (offsets != null) {
            offsets.clear();
            offsets = null;
        }
        blockCount = 0;
        blockSize = 0;
        blockStride = 0;
        bindpoint = 0;
    }

    //region Binding
    /**
     * Returns the OpenGL buffer for this uniform buffer.
     *
     * The buffer is a value assigned by OpenGL when the uniform buffer was allocated.
     * This method will return 0 if the block is not initialized. This method is
     * provided to allow the user direct access to the buffer for maximum flexibility.
     *
     * @return the OpenGL buffer for this unform block.
     */
    public int getBuffer()  { return dataBuffer.get(0); }

    /**
     * Returns the bind point for this uniform buffer.
     *
     * Uniform buffers and shaders have a many-to-many relationship. This means
     * that connecting them requires an intermediate table. The positions in
     * this table are called bind points. A uniform buffer is associated with
     * a bind point and a shader associates a bind point with a uniform struct.
     * That uniform struct then pulls data from the active block of the uniform
     * buffer. By default, this value is 0.
     *
     * @return the bind point for this uniform block.
     */
    public int getBindPoint() { return bindpoint; }

    /**
     * Returns the number of blocks supported by this buffer.
     *
     * A uniform buffer can support multiple uniform blocks at once.  The
     * active block is identified by the method {@link #getBlock}.
     *
     * @return the number of blocks supported by this buffer.
     */
    public int getBlockCount() { return blockCount; }

    /**
     * Sets the bind point for this uniform buffer.
     *
     * Uniform buffers and shaders have a many-to-many relationship. This means
     * that connecting them requires an intermediate table. The positions in
     * this table are called bind points. A uniform buffer is associated with
     * a bind point and a shader associates a bind point with a uniform struct.
     * That uniform struct then pulls data from the active block of the uniform
     * buffer. By default this value is 0.
     *
     * The uniform buffer does not need to be active to call this method. This
     * method only sets the bind point preference and does not actually the buffer.
     * However, if the buffer is bound to another bind
     * point, then it will be unbound from that point.
     *
     * @param point The bindpoint for this uniform buffer.
     */
    public void setBindPoint(int point) {
        GL30 gl = Gdx.gl30;
        if (isbound) {
            gl.glBindBufferBase(GL30.GL_UNIFORM_BUFFER, bindpoint, 0);
        }
        bindpoint = point;
    }

    /**
     * Binds this uniform buffer to its bind point.
     *
     * Unlike Texture, it is possible to bind a uniform buffer to its
     * bind point without making it the active uniform buffer. An inactive buffer
     * will still stream data to the shader, though its data cannot be altered
     * without making it active.
     *
     * Binding a buffer to a bind point replaces the uniform block originally
     * there.  So this buffer can be unbound without a call to unbind.
     * However, if another buffer is bound to a different bind point than this
     * block, it will not affect this buffer's relationship with the shader.
     *
     * For compatibility reasons with texture we allow this method to
     * both bind and activate the uniform buffer in one call.
     *
     * This call is reentrant. If can be safely called multiple times.
     *
     * @param activate  Whether to activate this buffer in addition to binding.
     */
    public void bind(boolean activate) {
        GL30 gl = Gdx.gl30;
        if (activate) {
            this.activate();
        }
        gl.glBindBufferBase(GL30.GL_UNIFORM_BUFFER, bindpoint, dataBuffer.get(0));
    }

    /**
     * Unbinds this uniform buffer disassociating it from its bind point.
     *
     * This call will have no affect on the active buffer (e.g. which buffer is
     * receiving data from the program). This method simply removes this buffer
     * from its bind point.
     *
     * Once unbound, the bind point for this buffer will no longer send data
     * to the appropriate uniform(s) in the shader. In that case the shader will
     * use default values according to the variable types.
     *
     * This call is reentrant.  If can be safely called multiple times.
     */
    public void unbind() {
        GL30 gl = Gdx.gl30;
        if (isbound) {
            gl.glBindBufferBase(GL30.GL_UNIFORM_BUFFER, bindpoint, 0);
            isbound = false;
        }
    }

    /**
     * Activates this uniform block so that if can receive data.
     *
     * This method makes this uniform block the active uniform buffer. This means
     * that changes made to the data in this uniform buffer will be pushed to the
     * graphics card. If there were are any pending changes to the uniform buffer
     * (made when it was not active), they will be pushed immediately when this
     * method is called.
     *
     * This method does not bind the uniform block to a bind point. That must be
     * done with a call to {@link #bind}.
     *
     * This call is reentrant. If can be safely called multiple times.
     */
    public void activate() {
        GL30 gl = Gdx.gl30;
        gl.glBindBuffer(GL30.GL_UNIFORM_BUFFER, dataBuffer.get(0));
        if (autoflush && dirty) {
            gl.glBufferData(GL30.GL_UNIFORM_BUFFER, blockStride * blockCount, byteBuffer, drawtype);
            dirty = false;
        }
    }

    /**
     * Deactivates this uniform block, making it no longer active.
     *
     * This method will not unbind the buffer from its bind point (assuming it is
     * bound to one). It simply means that it is no longer the active uniform buffer
     * and cannot receive new data. Data sent to this buffer will be cached and sent
     * to the graphics card once the buffer is reactivated.  However, the shader will
     * use the current graphics card data until that happens.
     *
     * This call is reentrant.  If can be safely called multiple times.
     */
    public void deactivate() {
        GL30 gl = Gdx.gl30;
        IntBuffer bound = BufferUtils.newIntBuffer(1);
        gl.glGetIntegerv(GL30.GL_UNIFORM_BUFFER_BINDING, bound);
        if (bound == dataBuffer) {
            gl.glBindBuffer(GL30.GL_UNIFORM_BUFFER, 0);
        }
    }

    /**
     * Returns true if this uniform buffer is currently bound.
     *
     * A uniform buffer is bound if it is attached to a bind point. That means that
     * the shader will pull its data for that bind point from this buffer. A uniform
     * block can be bound without being active.
     *
     * @return true if this uniform block is currently bound.
     */
    boolean isBound() { return isbound; }

    /**
     * Returns true if this uniform buffer is currently active.
     *
     * An active uniform block is the one that pushes changes in data directly to
     * the graphics card. If the buffer is not active, then many of the setter
     * methods in this class will cache changes but delay applying them until the
     * buffer is reactivated.
     *
     * Unlike Texture, it is possible for a uniform buffer to be active but
     * not bound.
     *
     * @return true if this uniform block is currently active.
     */
    public boolean isActive() {
        GL30 gl = Gdx.gl30;
        IntBuffer bound = BufferUtils.newIntBuffer(1);
        gl.glGetIntegerv(GL30.GL_UNIFORM_BUFFER_BINDING, bound);
        return bound == dataBuffer;
    }

    /**
     * Returns the active uniform block in this buffer.
     *
     * The active uniform block is the block from which the shader will pull
     * uniform values.  This value can be altered even if the buffer is not active
     * (or even bound)
     *
     * @return the active uniform block in this buffer.
     */
    public int getBlock() { return blockPntr; }

    /**
     * Sets the active uniform block in this buffer.
     *
     * The active uniform block is the block from which the shader will pull
     * uniform values. This value can only be altered if this buffer is bound
     * (though it need not be active).
     *
     * @param block The active uniform block in this buffer.
     */
    public void setBlock(int block) {
        GL30 gl = Gdx.gl30;
        if (blockPntr != block) {
            blockPntr = block;
            gl.glBindBufferRange(GL30.GL_UNIFORM_BUFFER, bindpoint, dataBuffer.get(0),
                    block* blockStride, blockSize);
        }
    }

    /**
     * Flushes any changes in the backing byte buffer to the graphics card.
     *
     * This method is only necessary if the user has accessed the backing byte
     * buffer directly via getData and needs to push these changes to the
     * graphics card.  Calling this method will not affect the active uniform
     * buffer.
     */
    public void flush() {
        assert isActive() : "Buffer is not active.";
        GL30 gl = Gdx.gl30;
        gl.glBufferData(GL30.GL_UNIFORM_BUFFER, blockStride * blockCount, byteBuffer, drawtype);
        dirty = false;
    }
    //endregion

    //region Data Offsets

    public static final int INVALID_OFFSET = -1;
    /**
     * Defines the byte offset of the given buffer variable.
     *
     * It is not necessary to call this method to use the uniform buffer. It is
     * always possible to pass data to the uniform block by specifying the byte
     * offset.  The shader uses byte offsets to pull data from the uniform buffer
     * and assign it to the appropriate struct variable.
     *
     * However, this method makes use of the uniform buffer easier to follow. It
     * explicitly assigns a variable name to a byte offset. This variable name
     * can now be used in place of the byte offset with passing data to this
     * uniform block.
     *
     * Use of this method does not require the uniform buffer to be bound or
     * even active.
     *
     * @param name      The variable name to use for this offset
     * @param offset    The buffer offset in bytes
     */
    public void setOffset(String name, int offset) {
        offsets.put(name, offset);
    }

    /**
     * Returns the byte offset for the given name.
     *
     * This method requires that name be previously associated with an offset
     * via {@link #setOffset}. If it has not been associated with an offset,
     * then this method will return invalid offset instead.
     *
     * @param name      The variable name to query for an offset
     *
     * @return the byte offset of the given struct variable.
     */
    public int getOffset(String name) {
        Integer elt = offsets.get(name);
        if (elt == null) {
            return INVALID_OFFSET;
        }
        return elt;
    }

    /**
     * Returns the offsets defined for this buffer
     *
     * The vector returned will include the name of every variable set by
     * the method {@link #setOffset}.
     *
     * @return the offsets defined for this buffer
     */
    public String[] getOffsets() {
        String[] result = new String[offsets.size()];
        int count = 0;
        for (HashMap.Entry<String,Integer> mapElement : offsets.entrySet()) {
            String key = mapElement.getKey();
            result[count] = key;
            count++;
        }
        return result;
    }

    //endregion

    //region Uniforms
    /**
     * Sets the given uniform variable to a vector value.
     *
     * This method will write the vector as 2*sizeof(float) bytes to the appropriate
     * buffer location (and the buffer must have the appropriate capacity).
     *
     * Values set by this method will not be sent to the graphics card until the
     * buffer is flushed. However, if the buffer is active and auto-flush is turned
     * on, it will be written immediately.
     *
     * If block is -1, it sets this value in every block in this uniform buffer.
     * This is a potentially expensive operation if the block is active.  For
     * mass changes, it is better to deactivate the buffer, and have them apply
     * once the buffer is reactivated.
     *
     * @param block     The block in this uniform buffer to access
     * @param offset    The offset within the block
     * @param vec       The value for the uniform
     */
    public void setUniform(int block, int offset, Vector2 vec) {
        tempdata[0] = vec.x;
        tempdata[1] = vec.y;
        setUniformfv(block,offset,2,tempdata,0);
    }

    /**
     * Sets the given uniform variable to a vector value.
     *
     * This method requires that the uniform name be previously bound to a byte
     * offset with the call {@link #setOffset}. This method will write the vector
     * as 2*sizeof(float) bytes to the appropriate buffer location (and the buffer
     * must have the appropriate capacity).
     *
     * Values set by this method will not be sent to the graphics card until the
     * buffer is flushed. However, if the buffer is active and auto-flush is turned
     * on, it will be written immediately.
     *
     * If block is -1, it sets this value in every block in this uniform buffer.
     * This is a potentially expensive operation if the block is active.  For
     * mass changes, it is better to deactivate the buffer, and have them apply
     * once the buffer is reactivated.
     *
     * @param block The block in this uniform buffer to access
     * @param name  The name of the uniform variable
     * @param vec   The value for the uniform
     */
    public void setUniform(int block, String name, Vector2 vec) {
        tempdata[0] = vec.x;
        tempdata[1] = vec.y;
        setUniformfv(block,name,2,tempdata,0);
    }

    /**
     * Returns true if it can access the given uniform variable as a vector.
     *
     * This method will read the vector as 2*sizeof(float) bytes to the appropriate
     * buffer location (and the buffer must have the appropriate capacity).
     *
     * The buffer does not have to be active to call this method.  If it is not
     * active and there are pending changes to this uniform variable, this method
     * will read those changes and not the current value in the graphics card.
     *
     * @param block     The block in this uniform buffer to access
     * @param offset    The offset within the block
     * @param vec       The vector to store the result
     *
     * @return true if it can access the given uniform variable as a vector.
     */
    public boolean getUniform(int block, int offset, Vector2 vec) {
        if (getUniformfv(block,offset,2,tempdata,0)) {
            vec.set( tempdata[0], tempdata[1] );
            return true;
        }
        return false;
    }

    /**
     * Returns true if it can access the given uniform variable as a vector.
     *
     * This method requires that the uniform name be previously bound to a byte
     * offset with the call {@link #setOffset}. This method will read the vector
     * as 2*sizeof(float) bytes to the appropriate buffer location (and the buffer
     * must have the appropriate capacity).
     *
     * The buffer does not have to be active to call this method.  If it is not
     * active and there are pending changes to this uniform variable, this method
     * will read those changes and not the current value in the graphics card.
     *
     * @param block The block in this uniform buffer to access
     * @param name  The name of the uniform variable
     * @param vec   The vector to store the result
     *
     * @return true if it can access the given uniform variable as a vector.
     */
    public boolean getUniform(int block, String name, Vector2 vec) {
        if (getUniformfv(block,name,2,tempdata,0)) {
            vec.set( tempdata[0], tempdata[1] );
            return true;
        }
        return false;
    }

    /**
     * Sets the given uniform variable to a vector value.
     *
     * This method will write the vector as 3*sizeof(float) bytes to the appropriate
     * buffer location (and the buffer must have the appropriate capacity).
     *
     * Values set by this method will not be sent to the graphics card until the
     * buffer is flushed. However, if the buffer is active and auto-flush is turned
     * on, it will be written immediately.
     *
     * If block is -1, it sets this value in every block in this uniform buffer.
     * This is a potentially expensive operation if the block is active.  For
     * mass changes, it is better to deactivate the buffer, and have them apply
     * once the buffer is reactivated.
     *
     * @param block     The block in this uniform buffer to access
     * @param offset    The offset within the block
     * @param vec       The value for the uniform
     */
    public void setUniform(int block, int offset, Vector3 vec) {
        tempdata[0] = vec.x;
        tempdata[1] = vec.y;
        tempdata[2] = vec.z;
        setUniformfv(block,offset,3,tempdata,0);
    }

    /**
     * Sets the given uniform variable to a vector value.
     *
     * This method requires that the uniform name be previously bound to a byte
     * offset with the call {@link #setOffset}. This method will write the vector
     * as 3*sizeof(float) bytes to the appropriate buffer location (and the buffer
     * must have the appropriate capacity).
     *
     * Values set by this method will not be sent to the graphics card until the
     * buffer is flushed. However, if the buffer is active and auto-flush is turned
     * on, it will be written immediately.
     *
     * If block is -1, it sets this value in every block in this uniform buffer.
     * This is a potentially expensive operation if the block is active.  For
     * mass changes, it is better to deactivate the buffer, and have them apply
     * once the buffer is reactivated.
     *
     * @param block The block in this uniform buffer to access
     * @param name  The name of the uniform variable
     * @param vec   The value for the uniform
     */
    public void setUniform(int block, String name, Vector3 vec) {
        tempdata[0] = vec.x;
        tempdata[1] = vec.y;
        tempdata[2] = vec.z;
        setUniformfv(block,name,3,tempdata,0);

    }

    /**
     * Returns true if it can access the given uniform variable as a vector.
     *
     * This method will read the vector as 3*sizeof(float) bytes to the appropriate
     * buffer location (and the buffer must have the appropriate capacity).
     *
     * The buffer does not have to be active to call this method.  If it is not
     * active and there are pending changes to this uniform variable, this method
     * will read those changes and not the current value in the graphics card.
     *
     * @param block     The block in this uniform buffer to access
     * @param offset    The offset within the block
     * @param vec       The vector to store the result
     *
     * @return true if it can access the given uniform variable as a vector.
     */
    public boolean getUniform(int block, int offset, Vector3 vec) {
        if (getUniformfv(block, offset,3, tempdata,0)) {
            vec.set( tempdata[0], tempdata[1], tempdata[2] );
            return true;
        }
        return false;
    }

    /**
     * Returns true if it can access the given uniform variable as a vector.
     *
     * This method requires that the uniform name be previously bound to a byte
     * offset with the call {@link #setOffset}. This method will read the vector
     * as 3*sizeof(float) bytes to the appropriate buffer location (and the buffer
     * must have the appropriate capacity).
     *
     * The buffer does not have to be active to call this method.  If it is not
     * active and there are pending changes to this uniform variable, this method
     * will read those changes and not the current value in the graphics card.
     *
     * @param block The block in this uniform buffer to access
     * @param name  The name of the uniform variable
     * @param vec   The vector to store the result
     *
     * @return true if it can access the given uniform variable as a vector.
     */
    public boolean getUniform(int block, String name, Vector3 vec) {
        if (getUniformfv(block, name, 3, tempdata,0)) {
            vec.set( tempdata[0], tempdata[1], tempdata[2] );
            return true;
        }
        return false;
    }

    /**
     * Sets the given uniform variable to a color value.
     *
     * This method will write the color as 4*sizeof(float) bytes to the appropriate
     * buffer location (and the buffer must have the appropriate capacity).
     *
     * Values set by this method will not be sent to the graphics card until the
     * buffer is flushed. However, if the buffer is active and auto-flush is turned
     * on, it will be written immediately.
     *
     * If block is -1, it sets this value in every block in this uniform buffer.
     * This is a potentially expensive operation if the block is active.  For
     * mass changes, it is better to deactivate the buffer, and have them apply
     * once the buffer is reactivated.
     *
     * @param block     The block in this uniform buffer to access
     * @param offset    The offset within the block
     * @param color     The value for the uniform
     */
    public void setUniform(int block, int offset, Color color) {
        tempdata[0] = color.r;
        tempdata[1] = color.g;
        tempdata[2] = color.b;
        tempdata[3] = color.a;
        setUniformfv(block, offset, 4, tempdata,0);
    }

    /**
     * Sets the given uniform variable to a color value.
     *
     * This method requires that the uniform name be previously bound to a byte
     * offset with the call {@link #setOffset}. This method will write the color
     * as 4*sizeof(float) bytes to the appropriate buffer location (and the buffer
     * must have the appropriate capacity).
     *
     * Values set by this method will not be sent to the graphics card until the
     * buffer is flushed. However, if the buffer is active and auto-flush is turned
     * on, it will be written immediately.
     *
     * If block is -1, it sets this value in every block in this uniform buffer.
     * This is a potentially expensive operation if the block is active.  For
     * mass changes, it is better to deactivate the buffer, and have them apply
     * once the buffer is reactivated.
     *
     * @param block The block in this uniform buffer to access
     * @param name  The name of the uniform variable
     * @param color The value for the uniform
     */
    public void setUniform(int block, String name, Color color) {
        tempdata[0] = color.r;
        tempdata[1] = color.g;
        tempdata[2] = color.b;
        tempdata[3] = color.a;
        setUniformfv(block, name, 4, tempdata,0);
    }

    /**
     * Returns true if it can access the given uniform variable as a color.
     *
     * This method will read the color as 4*sizeof(float) bytes to the appropriate
     * buffer location (and the buffer must have the appropriate capacity).
     *
     * The buffer does not have to be active to call this method.  If it is not
     * active and there are pending changes to this uniform variable, this method
     * will read those changes and not the current value in the graphics card.
     *
     * @param block     The block in this uniform buffer to access
     * @param offset    The offset within the block
     * @param color     The color to store the result
     *
     * @return true if it can access the given uniform variable as a color.
     */
    public boolean getUniform(int block, int offset, Color color) {
        if (getUniformfv(block, offset, 4, tempdata,0)) {
            color.set( tempdata[0], tempdata[1], tempdata[2], tempdata[3] );
            return true;
        }
        return false;
    }

    /**
     * Returns true if it can access the given uniform variable as a color.
     *
     * This method requires that the uniform name be previously bound to a byte
     * offset with the call {@link #setOffset}. This method will read the color
     * as 4*sizeof(float) bytes to the appropriate buffer location (and the buffer
     * must have the appropriate capacity).
     *
     * The buffer does not have to be active to call this method.  If it is not
     * active and there are pending changes to this uniform variable, this method
     * will read those changes and not the current value in the graphics card.
     *
     * @param block The block in this uniform buffer to access
     * @param name  The name of the uniform variable
     * @param color The color to store the result
     *
     * @return true if it can access the given uniform variable as a color.
     */
    public boolean getUniform(int block, String name, Color color) {
        if (getUniformfv(block, name, 4, tempdata,0)) {
            color.set( tempdata[0], tempdata[1], tempdata[2], tempdata[3] );
            return true;
        }
        return false;
    }

    /**
     * Sets the given uniform variable to a matrix value.
     *
     * This method will write the matrix as 16*sizeof(float) bytes to the appropriate
     * buffer location (and the buffer must have the appropriate capacity).
     *
     * Values set by this method will not be sent to the graphics card until the
     * buffer is flushed. However, if the buffer is active and auto-flush is turned
     * on, it will be written immediately.
     *
     * If block is -1, it sets this value in every block in this uniform buffer.
     * This is a potentially expensive operation if the block is active.  For
     * mass changes, it is better to deactivate the buffer, and have them apply
     * once the buffer is reactivated.
     *
     * @param block     The block in this uniform buffer to access
     * @param offset    The offset within the block
     * @param mat       The value for the uniform
     */
    public void setUniform(int block, int offset, Matrix4 mat) {
        setUniformfv(block, offset, 16, mat.val,0);
    }

    /**
     * Sets the given uniform variable to a matrix value.
     *
     * This method requires that the uniform name be previously bound to a byte
     * offset with the call {@link #setOffset}. This method will write the matrix
     * as 16*sizeof(float) bytes to the appropriate buffer location (and the buffer
     * must have the appropriate capacity).
     *
     * Values set by this method will not be sent to the graphics card until the
     * buffer is flushed. However, if the buffer is active and auto-flush is turned
     * on, it will be written immediately.
     *
     * If block is -1, it sets this value in every block in this uniform buffer.
     * This is a potentially expensive operation if the block is active.  For
     * mass changes, it is better to deactivate the buffer, and have them apply
     * once the buffer is reactivated.
     *
     * @param block The block in this uniform buffer to access
     * @param name  The name of the uniform variable
     * @param mat   The value for the uniform
     */
    public void setUniform(int block, String name, Matrix4 mat) {
        setUniformfv(block, name, 16, mat.val, 0);
    }

    /**
     * Returns true if it can access the given uniform variable as a matrix.
     *
     * This method will read the matrix as 16*sizeof(float) bytes to the appropriate
     * buffer location (and the buffer must have the appropriate capacity).
     *
     * The buffer does not have to be active to call this method.  If it is not
     * active and there are pending changes to this uniform variable, this method
     * will read those changes and not the current value in the graphics card.
     *
     * @param block     The block in this uniform buffer to access
     * @param offset    The offset within the block
     * @param mat       The matrix to store the result
     *
     * @return true if it can access the given uniform variable as a matrix.
     */
    public boolean getUniform(int block, int offset, Matrix4 mat) {
        return (getUniformfv(block, offset, 16, mat.val, 0));
    }

    /**
     * Returns true if it can access the given uniform variable as a matrix.
     *
     * This method requires that the uniform name be previously bound to a byte
     * offset with the call {@link #setOffset}. This method will read the matrix
     * as 16*sizeof(float) bytes to the appropriate buffer location (and the buffer
     * must have the appropriate capacity).
     *
     * The buffer does not have to be active to call this method.  If it is not
     * active and there are pending changes to this uniform variable, this method
     * will read those changes and not the current value in the graphics card.
     *
     * @param block The block in this uniform buffer to access
     * @param name  The name of the uniform variable
     * @param mat   The matrix to store the result
     *
     * @return true if it can access the given uniform variable as a matrix.
     */
    public boolean getUniform(int block, String name, Matrix4 mat) {
        return (getUniformfv(block, name, 16, mat.val, 0));
    }

    /**
     * Sets the given uniform variable to an affine transform.
     *
     * Affine transforms are passed to a uniform block as a 4x3 matrix on
     * homogenous coordinates. That is because the columns must be 4*sizeof(float)
     * bytes for alignment reasons. The buffer must have 12*sizeof(float) bytes
     * available for this write.
     *
     * Values set by this method will not be sent to the graphics card until the
     * buffer is flushed. However, if the buffer is active and auto-flush is turned
     * on, it will be written immediately.
     *
     * If block is -1, it sets this value in every block in this uniform buffer.
     * This is a potentially expensive operation if the block is active.  For
     * mass changes, it is better to deactivate the buffer, and have them apply
     * once the buffer is reactivated.
     *
     * @param block     The block in this uniform buffer to access
     * @param offset    The offset within the block
     * @param mat       The value for the uniform
     */
    public void setUniform(int block, int offset, Affine2 mat) {
        float[] data = new float[12];
        data[0] = mat.m00;
        data[1] = mat.m10;
        data[4] = mat.m01;
        data[5] = mat.m11;
        data[8] = mat.m02;
        data[9] = mat.m12;
        data[10] = 1;
        setUniformfv(block, offset, 12, data, 0);
    }

    /**
     * Sets the given uniform variable to an affine transform.
     *
     * Affine transforms are passed to a uniform block as a 4x3 matrix on
     * homogenous coordinates. That is because the columns must be 4*sizeof(float)
     * bytes for alignment reasons. The buffer must have 12*sizeof(float) bytes
     * available for this write.
     *
     * This method requires that the uniform name be previously bound to a byte
     * offset with the call {@link #setOffset}.  Values set by this method will not
     * be sent to the graphics card until the buffer is flushed. However, if the
     * buffer is active and auto-flush is turned on, it will be written immediately.
     *
     * If block is -1, it sets this value in every block in this uniform buffer.
     * This is a potentially expensive operation if the block is active.  For
     * mass changes, it is better to deactivate the buffer, and have them apply
     * once the buffer is reactivated.
     *
     * @param block The block in this uniform buffer to access
     * @param name  The name of the uniform variable
     * @param mat   The value for the uniform
     */
    public void setUniform(int block, String name, Affine2 mat) {
        float[] data = new float[12];
        data[0] = mat.m00;
        data[1] = mat.m10;
        data[4] = mat.m01;
        data[5] = mat.m11;
        data[8] = mat.m02;
        data[9] = mat.m12;
        data[10] = 1;
        setUniformfv(block, name, 12, data, 0);

    }

    /**
     * Returns true if it can access the given uniform variable as an affine transform.
     *
     * Affine transforms are read from a uniform block as a 4x3 matrix on
     * homogenous coordinates. That is because the columns must be 4*sizeof(float)
     * bytes for alignment reasons. The buffer must have 12*sizeof(float) bytes
     * available for this read.
     *
     * The buffer does not have to be active to call this method.  If it is not
     * active and there are pending changes to this uniform variable, this method
     * will read those changes and not the current value in the graphics card.
     *
     * @param block     The block in this uniform buffer to access
     * @param offset    The offset within the block
     * @param mat       The matrix to store the result
     *
     * @return true if it can access the given uniform variable as an affine transform.
     */
    public boolean getUniform(int block, int offset, Affine2 mat) {
        float[] data = new float[12];
        if (getUniformfv(block, offset, 12, data, 0)) {
            mat.m00 = data[0];
            mat.m10 = data[1];
            mat.m01 = data[4];
            mat.m11 = data[5];
            mat.m02 = data[8];
            mat.m12 = data[9];
            return true;
        }
        return false;
    }

    /**
     * Returns true if it can access the given uniform variable as an affine transform.
     *
     * Affine transforms are read from a uniform block as a 4x3 matrix on
     * homogenous coordinates. That is because the columns must be 4*sizeof(float)
     * bytes for alignment reasons. The buffer must have 12*sizeof(float) bytes
     * available for this read.
     *
     * This method requires that the uniform name be previously bound to a byte
     * offset with the call {@link #setOffset}. The buffer does not have to be active
     * to call this method.  If it is not active and there are pending changes to
     * this uniform variable, this method will read those changes and not the current
     * value in the graphics card.
     *
     * @param block     The block in this uniform buffer to access
     * @param name      The name of the uniform variable
     * @param mat       The matrix to store the result
     *
     * @return true if it can access the given uniform variable as an affine transform.
     */
    public boolean getUniform(int block, String name, Affine2 mat) {
        float[] data = new float[12];
        if (getUniformfv(block, name, 12, data, 0)) {
            mat.m00 = data[0];
            mat.m10 = data[1];
            mat.m01 = data[4];
            mat.m11 = data[5];
            mat.m02 = data[8];
            mat.m12 = data[9];
            return true;
        }
        return false;
    }

    /**
     * Sets the given uniform variable to a quaternion.
     *
     * This method will write the quaternion as 4*sizeof(float) bytes to the appropriate
     * buffer location (and the buffer must have the appropriate capacity).
     *
     * Values set by this method will not be sent to the graphics card until the
     * buffer is flushed. However, if the buffer is active and auto-flush is turned
     * on, it will be written immediately.
     *
     * If block is -1, it sets this value in every block in this uniform buffer.
     * This is a potentially expensive operation if the block is active.  For
     * mass changes, it is better to deactivate the buffer, and have them apply
     * once the buffer is reactivated.
     *
     * @param block     The block in this uniform buffer to access
     * @param offset    The offset within the block
     * @param quat      The value for the uniform
     */
    public void setUniform(int block, int offset, Quaternion quat) {
        tempdata[0] = quat.x;
        tempdata[1] = quat.y;
        tempdata[2] = quat.z;
        tempdata[3] = quat.w;
        setUniformfv(block, offset, 4, tempdata,0);
    }


    /**
     * Sets the given uniform variable to a quaternion
     *
     * This method requires that the uniform name be previously bound to a byte
     * offset with the call {@link #setOffset}. This method will write the quaternion
     * as 4*sizeof(float) bytes to the appropriate buffer location (and the buffer
     * must have the appropriate capacity).
     *
     * Values set by this method will not be sent to the graphics card until the
     * buffer is flushed. However, if the buffer is active and auto-flush is turned
     * on, it will be written immediately.
     *
     * If block is -1, it sets this value in every block in this uniform buffer.
     * This is a potentially expensive operation if the block is active.  For
     * mass changes, it is better to deactivate the buffer, and have them apply
     * once the buffer is reactivated.
     *
     * @param block The block in this uniform buffer to access
     * @param name  The name of the uniform variable
     * @param quat  The value for the uniform
     */
    public void setUniform(int block, String name, Quaternion quat) {
        tempdata[0] = quat.x;
        tempdata[1] = quat.y;
        tempdata[2] = quat.z;
        tempdata[3] = quat.w;
        setUniformfv(block, name, 4, tempdata,0);
    }

    /**
     * Returns true if it can access the given uniform variable as a quaternion.
     *
     * This method will read the quaternion as 4*sizeof(float) bytes to the appropriate
     * buffer location (and the buffer must have the appropriate capacity).
     *
     * The buffer does not have to be active to call this method.  If it is not
     * active and there are pending changes to this uniform variable, this method
     * will read those changes and not the current value in the graphics card.
     *
     * @param block     The block in this uniform buffer to access
     * @param offset    The offset within the block
     * @param quat      The quaternion to store the result
     *
     * @return true if it can access the given uniform variable as a quaternion.
     */
    public boolean getUniform(int block, int offset, Quaternion quat) {
        if (getUniformfv(block, offset, 4, tempdata,0)) {
            quat.set( tempdata[0], tempdata[1], tempdata[2], tempdata[3] );
            return true;
        }
        return false;
    }

    /**
     * Returns true if it can access the given uniform variable as a quaternion.
     *
     * This method requires that the uniform name be previously bound to a byte
     * offset with the call {@link #setOffset}. This method will read the quaternion
     * as 4*sizeof(float) bytes to the appropriate buffer location (and the buffer
     * must have the appropriate capacity).
     *
     * The buffer does not have to be active to call this method.  If it is not
     * active and there are pending changes to this uniform variable, this method
     * will read those changes and not the current value in the graphics card.
     *
     * @param block The block in this uniform buffer to access
     * @param name  The name of the uniform variable
     * @param quat  The quaternion to store the result
     *
     * @return true if it can access the given uniform variable as a quaternion.
     */
    public boolean getUniformQuaternion(int block, String name, Quaternion quat) {
        if (getUniformfv(block, name, 4, tempdata,0)) {
            quat.set( tempdata[0], tempdata[1], tempdata[2], tempdata[3] );
            return true;
        }
        return false;
    }

    /**
     * Sets the given buffer offset to an array of float values with a default
     * offset of 0.
     */
    public void setUniformfv(int block, int offset, int size, float[] values) {
        setUniformfv(block, offset, size, values, 0);
    }

    /**
     * Sets the given buffer offset to an array of float values
     *
     * Values set by this method will not be sent to the graphics card until the
     * buffer is flushed. However, if the buffer is active and auto-flush is turned
     * on, it will be written immediately.
     *
     * If block is -1, it sets this value in every block in this uniform buffer.
     * This is a potentially expensive operation if the block is active.  For
     * mass changes, it is better to deactivate the buffer, and have them apply
     * once the buffer is reactivated.
     *
     * @param block     The block in this uniform buffer to access
     * @param offset    The offset within the block
     * @param size      The number of values to write to the buffer
     * @param values    The values to write
     */
    public void setUniformfv(int block, int offset, int size, float[] values, int srcOffset) {
        assert block < blockCount : "Block " + block + " is invalid.";
        assert offset < blockSize : "Offset " + offset + " is invalid.";
        GL30 gl = Gdx.gl30;
        if (block >= 0) {
            int pos = byteBuffer.position();
            int position = block* blockStride +offset;
            ((Buffer) byteBuffer).position(position);
            BufferUtils.copy(values, srcOffset, size, byteBuffer);
            ((Buffer) byteBuffer).position(pos);
            if (autoflush && isActive()) {
                gl.glBufferSubData(GL30.GL_UNIFORM_BUFFER, position, size * Float.SIZE, byteBuffer);
            } else {
                dirty = true;
            }
        } else {
            boolean active = false;
            if (autoflush && isActive()) {
                active = true;
            } else {
                dirty = true;
            }
            for(int bl = 0; bl < blockCount; bl++) {
                final int pos = byteBuffer.position();
                int position = bl* blockStride +offset;
                ((Buffer) byteBuffer).position(position);
                BufferUtils.copy(values, srcOffset, size, byteBuffer);
                ((Buffer) byteBuffer).position(pos);
                if (active) {
                    gl.glBufferSubData(GL30.GL_UNIFORM_BUFFER, position, size * Float.SIZE, byteBuffer);
                }
            }
        }
    }

    /**
     * Sets the given buffer location to an array of float values
     *
     * This method requires that the uniform name be previously bound to a byte offset
     * with the call {@link #setOffset}. Values set by this method will not be sent to
     * the graphics card until the buffer is flushed. However, if the buffer is active
     * and auto-flush is turned on, it will be written immediately.
     *
     * If block is -1, it sets this value in every block in this uniform buffer.
     * This is a potentially expensive operation if the block is active.  For
     * mass changes, it is better to deactivate the buffer, and have them apply
     * once the buffer is reactivated.
     *
     * @param block     The block in this uniform buffer to access
     * @param name      The name of the uniform variable
     * @param size      The number of values to write to the buffer
     * @param values    The values to write
     * @param srcoff    The offset in the source array
     */
    public void setUniformfv(int block, String name, int size, float[] values, int srcoff) {
        int offset = getOffset(name);
        if (offset != INVALID_OFFSET) {
            setUniformfv(block,offset,size,values,srcoff);
        }
    }

    /**
     * Returns true if it can access the given buffer offset as an array of floats
     *
     * The buffer does not have to be active to call this method.  If it is not
     * active and there are pending changes to this uniform variable, this method
     * will read those changes and not the current value in the graphics card.
     *
     * @param block     The block in this uniform buffer to access
     * @param offset    The offset within the block
     * @param size      The available size of the value array
     * @param values    The array to receive the values
     * @param dstoff    The offset in the destination array
     *
     * @return true if data was successfully read into values
     */
    public boolean getUniformfv(int block, int offset, int size, float[] values, int dstoff) {
        if (block >= blockCount || offset > blockSize) {
            return false;
        }
        int position = (block*blockStride+offset)/4;
        FloatBuffer floater = byteBuffer.asFloatBuffer();
        floater.position(position);
        floater.get(values,dstoff,size);
        byteBuffer.position( 0 );
        return true;
    }

    /**
     * Returns true if it can access the given buffer location as an array of floats
     *
     * This method requires that the uniform name be previously bound to a byte offset
     * with the call {@link #setOffset}. The buffer does not have to be active to call
     * this method. If it is not active and there are pending changes to this uniform
     * variable, this method will read those changes and not the current value in the
     * graphics card.
     *
     * @param block     The block in this uniform buffer to access
     * @param name      The name of the uniform variable
     * @param size      The available size of the value array
     * @param values    The array to receive the values
     * @param dstoff    The offset in the destination array
     *
     * @return true if data was successfully read into values
     */
    public boolean getUniformfv(int block, String name, int size, float[] values, int dstoff) {
        int offset = getOffset(name);
        if (offset != INVALID_OFFSET) {
            return getUniformfv(block,offset,size,values,dstoff);
        }
        return false;
    }

    /**
     * Sets the given buffer offset to an array of integer values
     *
     * Values set by this method will not be sent to the graphics card until the
     * buffer is flushed. However, if the buffer is active and auto-flush is turned
     * on, it will be written immediately.
     *
     * If block is -1, it sets this value in every block in this uniform buffer.
     * This is a potentially expensive operation if the block is active.  For
     * mass changes, it is better to deactivate the buffer, and have them apply
     * once the buffer is reactivated.
     *
     * @param block     The block in this uniform buffer to access
     * @param offset    The offset within the block
     * @param size      The number of values to write to the buffer
     * @param values    The values to write
     * @param srcoff    The offset in the source array
     */
    public void setUniformiv(int block, int offset, int size, int[] values, int srcoff) {
        assert block < blockCount : String.format("Block %d is invalid.",block);
        assert offset < blockSize : String.format("Offset %d is invalid.",offset);
        if (block >= 0) {
            int position = block*blockStride+offset;
            byteBuffer.position(position);
            BufferUtils.copy( values, srcoff, size, byteBuffer );
            byteBuffer.position( 0 );
            if (autoflush && isActive()) {
                Gdx.gl30.glBufferSubData(GL30.GL_UNIFORM_BUFFER, position, size*4, byteBuffer);
            } else {
                dirty = true;
            }
        } else {
            for(int bb = 0; bb < blockCount; bb++) {
                int position = bb*blockStride+offset;
                byteBuffer.position(position);
                BufferUtils.copy( values, srcoff, size, byteBuffer );
            }
            byteBuffer.position( 0 );
            if (autoflush && isActive()) {
                Gdx.gl30.glBufferSubData(GL30.GL_UNIFORM_BUFFER, 0, size*4, byteBuffer);
            } else {
                dirty = true;
            }
        }
    }

    /**
     * Sets the given buffer location to an array of integer values
     *
     * This method requires that the uniform name be previously bound to a byte offset
     * with the call {@link #setOffset}. Values set by this method will not be sent to
     * the graphics card until the buffer is flushed. However, if the buffer is active
     * and auto-flush is turned on, it will be written immediately.
     *
     * If block is -1, it sets this value in every block in this uniform buffer.
     * This is a potentially expensive operation if the block is active.  For
     * mass changes, it is better to deactivate the buffer, and have them apply
     * once the buffer is reactivated.
     *
     * @param block     The block in this uniform buffer to access
     * @param name      The name of the uniform variable
     * @param size      The number of values to write to the buffer
     * @param values    The values to write
     * @param srcoff    The offset in the source array
     */
    public void setUniformiv(int block, String name, int size, int[] values, int srcoff) {
        int offset = getOffset(name);
        if (offset != INVALID_OFFSET) {
            setUniformiv(block,offset,size,values,srcoff);
        }
    }

    /**
     * Returns true if it can access the given buffer offset as an array of integers
     *
     * The buffer does not have to be active to call this method.  If it is not
     * active and there are pending changes to this uniform variable, this method
     * will read those changes and not the current value in the graphics card.
     *
     * @param block     The block in this uniform buffer to access
     * @param offset    The offset within the block
     * @param size      The available size of the value array
     * @param values    The array to receive the values
     * @param dstoff    The offset in the destination array
     *
     * @return true if data was successfully read into values
     */
    public boolean getUniformiv(int block, int offset, int size, int[] values, int dstoff) {
        if (block >= blockCount || offset > blockSize) {
            return false;
        }
        int position = (block*blockStride+offset)/4;
        IntBuffer integer = byteBuffer.asIntBuffer();
        integer.position(position);
        integer.get(values,dstoff,size);
        byteBuffer.position( 0 );
        return true;
    }

    /**
     * Returns true if it can access the given buffer location as an array of integers
     *
     * This method requires that the uniform name be previously bound to a byte offset
     * with the call {@link #setOffset}. The buffer does not have to be active to call
     * this method. If it is not active and there are pending changes to this uniform
     * variable, this method will read those changes and not the current value in the
     * graphics card.
     *
     * @param block     The block in this uniform buffer to access
     * @param name      The name of the uniform variable
     * @param size      The available size of the value array
     * @param values    The array to receive the values
     * @param dstoff    The offset in the destination array
     *
     * @return true if data was successfully read into values
     */
    public boolean getUniformiv(int block, String name, int size, int[] values, int dstoff) {
        int offset = getOffset(name);
        if (offset != INVALID_OFFSET) {
            return getUniformiv(block,offset,size,values,dstoff);
        }
        return false;
    }
}

