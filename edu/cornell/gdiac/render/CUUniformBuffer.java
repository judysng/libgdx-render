package edu.cornell.gdiac.render;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;

public class CUUniformBuffer implements Disposable {
    /** The OpenGL uniform buffer; 0 is not allocated. */
    IntBuffer dataBuffer;
    /** The number of blocks assigned to the uniform buffer. */
    int blockCount;
    /** The active uniform block for this buffer. */
    int blockPntr;
    /** The capacity of a single block in the uniform buffer. */
    int blockSize;
    /** The alignment stride of a single block. */
    int blockStride;
    /** The bind point associated with this buffer (default 0) */
    int bindpoint;
    /** An underlying byte buffer to manage the uniform data */
    ByteBuffer byteBuffer;
    /** The draw type for this buffer */
    int drawtype;
    /** Whether the byte buffer flushes automatically */
    boolean autoflush;
    /** Whether the byte buffer must be flushed to the graphics card */
    boolean dirty;
    /** A mapping of struct names to their std140 offsets */
    HashMap<String, Integer> offsets;
    /** The decriptive buffer name */
    String name;

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
    }

    public CUUniformBuffer(int capacity, int blocks) {
        this();
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
//            CUAssertLog(false,"Capacity exceeds maximum value of %d bytes",value);
            blockCount = 0;
            blockSize = 0;
            blockStride = 0;
//            return false;
        }

        int error;
        gl.glGenBuffers(1, dataBuffer);
        if (dataBuffer == null) {
            error = gl.glGetError();
//            CULogError("Could not create uniform buffer. %s", gl_error_name(error).c_str());
//            return false;
        }

        byteBuffer = BufferUtils.newUnsafeByteBuffer(blockStride * blockCount);
        gl.glBindBuffer(GL30.GL_UNIFORM_BUFFER, dataBuffer.get(0));
        gl.glBufferData(GL30.GL_UNIFORM_BUFFER, blockStride * blockCount, null, drawtype);
        error = gl.glGetError();
        if (error != GL30.GL_NO_ERROR) {
            gl.glDeleteBuffers(1, dataBuffer);
            dataBuffer.put(0, 0);
//            CULogError("Could not allocate memory for uniform buffer. %s",
//                    gl_error_name(error).c_str());
//            return false;
        }

        gl.glBindBuffer(GL30.GL_UNIFORM_BUFFER, 0);
        System.out.println(byteBuffer);

        offsets = new HashMap<>();
//        return true;
    }

    @Override
    protected void finalize() throws Throwable {
//        dispose();
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
     * @param point The bind point for for this uniform buffer.
     */
    public void setBindPoint(int point) {
        GL30 gl = Gdx.gl30;
        IntBuffer bound = BufferUtils.newIntBuffer(1);
        // Don't have glGetIntegeri_v method, replace with this for now
        // But I don't think it works because this target could be overriden w multiple uniform buffers,
        // Only works if it is the active buffer,
        // also doesn't check if it is bound to the bindpoint.
        // for now, maybe no matter what just unbind when u set a new bind point
//        gl.glGetIntegeri_v(GL30.GL_UNIFORM_BUFFER_BINDING,_bindpoint, bound);
//        gl.glGetIntegerv(GL30.GL_UNIFORM_BUFFER_BINDING, bound);
//        if (bound == _dataBuffer) {
//            gl.glBindBufferBase(GL30.GL_UNIFORM_BUFFER, _bindpoint, 0);
//        }
        gl.glBindBufferBase(GL30.GL_UNIFORM_BUFFER, bindpoint, 0);
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
        IntBuffer bound = BufferUtils.newIntBuffer(1);
        // Don't have glGetIntegeri_v method, replace with this for now
        // But I don't think it works because this target could be overriden w multiple uniform buffers,
        // Only works if it is the active buffer
        // for now, maybe no matter what just unbind
//        gl.glGetIntegeri_v(GL30.GL_UNIFORM_BUFFER_BINDING,_bindpoint, bound);
//        gl.glGetIntegerv(GL30.GL_UNIFORM_BUFFER_BINDING, bound);
//        if (bound == _dataBuffer) {
//            gl.glBindBufferBase(GL30.GL_UNIFORM_BUFFER, _bindpoint, 0);
//        }
        gl.glBindBufferBase(GL30.GL_UNIFORM_BUFFER, bindpoint, 0);
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
     * This call is reentrant.  If can be safely called multiple times.
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

//    /**
//     * Returns true if this uniform buffer is currently bound.
//     *
//     * A uniform buffer is bound if it is attached to a bind point. That means that
//     * the shader will pull its data for that bind point from this buffer. A uniform
//     * block can be bound without being active.
//     *
//     * @return true if this uniform block is currently bound.
//     */
//    bool UniformBuffer::isBound() const {
//        GLint bound;
//        glGetIntegeri_v(GL_UNIFORM_BUFFER_BINDING,_bindpoint,&bound);
//        return bound == _dataBuffer;
//    }

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
//        CUAssertLog(isBound(), "Buffer is not bound.");
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
        GL30 gl = Gdx.gl30;
        // CUAssertLog(isActive(), "Buffer is not active."); // Problems on android emulator for now
        gl.glBufferData(GL30.GL_UNIFORM_BUFFER, blockStride * blockCount, byteBuffer, drawtype);
        dirty = false;
    }

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
            return -1; // Invalid offset
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
    public void setUniformfv(int block, int offset, int size, float[] values) {
        assert block < blockCount : "Block " + block + " is invalid.";
        assert offset < blockSize : "Offset " + offset + " is invalid.";
        GL30 gl = Gdx.gl30;
        if (block >= 0) {
            int pos = byteBuffer.position();
            int position = block* blockStride +offset;
            ((Buffer) byteBuffer).position(position);
            BufferUtils.copy(values, 0, size, byteBuffer);
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
                BufferUtils.copy(values, 0, size, byteBuffer);
                ((Buffer) byteBuffer).position(pos);
                if (active) {
                    gl.glBufferSubData(GL30.GL_UNIFORM_BUFFER, position, size * Float.SIZE, byteBuffer);
                }
            }
        }
    }
}

