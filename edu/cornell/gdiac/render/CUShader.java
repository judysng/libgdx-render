package edu.cornell.gdiac.render;

import java.nio.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.g2d.UniformBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * This module provides a general purpose shader class for GLSL shaders.
 * It supports compilations and has diagnostic tools for errors. The
 * shader is general enough that it should not need to be subclassed.
 * However, to use a shader, it must be attached to a VertexBuffer.
 *
 * @author  */
public class CUShader extends ShaderProgram implements Disposable {
    /** default name for gradient coordinates attributes **/
    public static final String GRADCOORD_ATTRIBUTE = "a_gradCoords";

    /** The uniform block variable names for this shader */
    private final ObjectMap<Integer, String> uniblockNames = new ObjectMap<Integer, String>();

    /** The uniform block locations of this shader */
    private final ObjectIntMap<String> uniblockSizes = new ObjectIntMap<String>();

    /** Mappings of uniforms to a uniform block */
    private final ObjectMap<Integer, Integer> uniblockFields = new ObjectMap<Integer, Integer>();

    /** Buffers for caching the uniform block information */
    IntBuffer count = BufferUtils.newIntBuffer(1);
    ByteBuffer name = BufferUtils.newByteBuffer(16); // 16 is max length
    IntBuffer size = BufferUtils.newIntBuffer(1);

    /** Constructs a new ShaderProgram and immediately compiles it.
     *
     * @param vertexShader the vertex shader
     * @param fragmentShader the fragment shader */

    public CUShader (String vertexShader, String fragmentShader) {
        super(vertexShader, fragmentShader);
        GL30 gl = Gdx.gl30;

        // Cache the uniform blocks
        ((Buffer)count).clear();
        gl.glGetProgramiv(getHandle(), gl.GL_ACTIVE_UNIFORM_BLOCKS, count);
        int numUniformBlocks = count.get(0);
        for (int ii = 0; ii < numUniformBlocks; ii++) {
            ((Buffer)count).clear();
            count.put(0, 1);
            ((Buffer)name).clear();
            gl.glGetActiveUniformBlockName(getHandle(), ii, count, name);
            int error = gl.glGetError();
            if (error == 0) {
                String key = name.toString();
                ((Buffer)size).clear();
                gl.glGetActiveUniformBlockiv(getHandle(), ii, gl.GL_UNIFORM_BLOCK_DATA_SIZE, size);
                uniblockSizes.put(key, size.get(0));
                uniblockNames.put(ii, key);

                // Link the block to uniforms
                ((Buffer)size).clear();
                gl.glGetActiveUniformBlockiv(getHandle(), ii, gl.GL_UNIFORM_BLOCK_ACTIVE_UNIFORMS, size);
                IntBuffer ans = BufferUtils.newIntBuffer(size.get(0));
                gl.glGetActiveUniformBlockiv(getHandle(), ii, gl.GL_UNIFORM_BLOCK_ACTIVE_UNIFORM_INDICES, ans);
                for(int jj = 0; jj < size.get(0); jj++) {
                    uniblockFields.put(ans.get(jj), ii);
                }
                ans.clear();
            }
        }
    }

    public CUShader (FileHandle vertexShader, FileHandle fragmentShader) {
        this(vertexShader.readString(), fragmentShader.readString());
    }

    /**
     * Returns a vector of all uniform blocks used by this shader
     *
     * A uniform block is a variable attached to a uniform buffer.  It is not the
     * same as a normal uniform and cannot be treated as such.  In this case
     * the uniform values are set in the Uniform Buffer object and
     * not the shader.
     *
     * @return a vector of all uniform blocks used by this shader
     */
    public String[] getUniformBlocks() {
        String[] result = new String[uniblockNames.size];
        int count = 0;
        for (Integer in : uniblockNames.keys()){
            result[count] = uniblockNames.get(in);
            count++;
        }
        return result;
    }

    /**
     * Returns a vector of all uniforms for the given block.
     *
     * A uniform block is a variable attached to a uniform buffer.  It is not the
     * same as a normal uniform and cannot be treated as such.  In this case
     * the uniform values are set in the {@link UniformBuffer} object and
     * not the shader.
     *
     * This method allows us to verify that at {@link UniformBuffer} object
     * properly matches this shader.
     *
     * @param pos   The location of the uniform block in the shader
     *
     * @return a vector of all uniform blocks used by this shader
     */
    public String[] getUniformsForBlock(int pos) {
        String[] result = new String[uniblockFields.size];
        int count = 0;
        for (int in : uniblockFields.keys()) {
            if (in == pos) {
                result[count] = getUniforms()[uniblockFields.get(in)];
                count++;
            }
        }
        String[] cutResult = new String[count];
        System.arraycopy(result, 0, cutResult, 0, count);
        return cutResult;
    }

    /**
     * Returns a vector of all uniforms for the given block.
     *
     * A uniform block is a variable attached to a uniform buffer.  It is not the
     * same as a normal uniform and cannot be treated as such.  In this case
     * the uniform values are set in the {@link UniformBuffer} object and
     * not the shader.
     *
     * This method allows us to verify that at {@link UniformBuffer} object
     * properly matches this shader.
     *
     * @param name      The name of the uniform block in the shader
     *
     * @return a vector of all uniform blocks used by this shader
     */
    public String[] getUniformsForBlock(String name) {
        GL30 gl = Gdx.gl30;
        String[] result = new String[uniblockFields.size];
        int index = gl.glGetUniformBlockIndex(getHandle(), name);
        if (index == GL30.GL_INVALID_INDEX) {
            return result;
        }
        int count = 0;
        for (int in : uniblockFields.keys()) {
            if (in == index) {
                result[count] = getUniforms()[uniblockFields.get(in)];
                count++;
            }
        }
        String[] cutResult = new String[count];
        System.arraycopy(result, 0, cutResult, 0, count);
        return result;
    }

    /**
     * Sets the given uniform block variable to a uniform buffer bindpoint.
     *
     * A uniform block is a variable attached to a uniform buffer.  It is not the
     * same as a normal uniform and cannot be treated as such.  In this case
     * the uniform values are set in the UniformBuffer object and
     * not the shader.
     *
     * This method will only succeed if the shader is actively bound.
     *
     * @param pos      The location of the uniform block in the shader
     * @param bindpoint   The bindpoint for the uniform block
     */
    public void setUniformBlock(int pos, int bindpoint) {
        GL30 gl = Gdx.gl30;
        gl.glUniformBlockBinding(getHandle(), pos, bindpoint);
    }

    /**
     * Sets the given uniform block variable to a uniform buffer bindpoint.
     *
     * A uniform block is a variable attached to a uniform buffer.  It is not the
     * same as a normal uniform and cannot be treated as such.  In this case
     * the uniform values are set in the UniformBuffer object and
     * not the shader.
     *
     * This method will only succeed if the shader is actively bound.
     *
     * @param name      The name of the uniform block in the shader
     * @param bindpoint   The bindpoint for the uniform block
     */
    public void setUniformBlock(String name, int bindpoint) {
        GL30 gl = Gdx.gl30;
        int index = gl.glGetUniformBlockIndex(getHandle(), name);
        if (index != GL30.GL_INVALID_INDEX) {
            gl.glUniformBlockBinding(getHandle(), index, bindpoint);
        }
    }

    /**
     * Sets the given uniform block variable to the bindpoint of the given uniform buffer.
     *
     * A uniform block is a variable attached to a uniform buffer.  It is not the
     * same as a normal uniform and cannot be treated as such.  In this case
     * the uniform values are set in the {@link UniformBuffer} object and
     * not the shader.
     *
     * This method will bind the uniform buffer to the current bindpoint of the
     * block object.  The shader will not be aware if the buffer object changes
     * its bindpoint in the future. However, it will verify whether the buffer
     * object has uniform variables matching this shader.
     *
     * This method will only succeed if the shader is actively bound.
     *
     * @param pos       The location of the uniform block in the shader
     * @param buffer    The buffer to bind to this uniform block
     */
    public void setUniformBlock(int pos, CUUniformBuffer buffer) {
        GL30 gl = Gdx.gl30;
        int bpoint = buffer == null ? 0 : buffer.getBindPoint();
        // Do some verification
        for (int in : uniblockFields.keys()) {
            if (uniblockFields.get(in) == pos) {
                String name = getUniforms()[in];
                int offset = buffer.getOffset(name);
                if (offset == -1) { // Invalid offset
                    System.out.println("Uniform buffer is missing variable '" + name + "'.");
                }
            }
        }

        String[] names = buffer.getOffsets();
        ObjectMap<String, Boolean> check = new ObjectMap<>();
        for (int i = 0; i < names.length; i++) {
            check.put(names[i], false);
        }
        for (int in : uniblockFields.keys()) {
            if (uniblockFields.get(in) == pos) {
                check.put(getUniforms()[in], true);
            }
        }
        for (String str : check.keys()) {
            if (!check.get(str)) {
                System.out.println("Shader is missing variable '" + str + "'.");
            }
        }

        // Now bind
        gl.glUniformBlockBinding(getHandle(), pos, bpoint);
    }

    /**
     * Sets the given uniform block variable to the bindpoint of the given uniform buffer.
     *
     * A uniform block is a variable attached to a uniform buffer.  It is not the
     * same as a normal uniform and cannot be treated as such.  In this case
     * the uniform values are set in the {@link UniformBuffer} object and
     * not the shader.
     *
     * This method will bind the uniform buffer to the current bindpoint of the
     * block object.  The shader will not be aware if the buffer object changes
     * its bindpoint in the future. However, it will verify whether the buffer
     * object has uniform variables matching this shader.
     *
     * This method will only succeed if the shader is actively bound.
     *
     * @param name      The name of the uniform block in the shader
     * @param buffer    The buffer to bind to this uniform block
     */
    public void setUniformBlock(String name, CUUniformBuffer buffer) {
        GL30 gl = Gdx.gl30;
        int index = gl.glGetUniformBlockIndex(getHandle(), name);
        if (index != GL30.GL_INVALID_INDEX) {
            setUniformBlock(index, buffer);
        }
    }

    /**
     * Returns the buffer bindpoint associated with the given uniform block.
     *
     * The shader does not track the actual uniform buffer associated with this
     * bindpoint, only the bindpoint itself.  It is up to the software developer to
     * keep track of what uniform buffer is currently at that bindpoint.
     *
     * @param pos      The location of the uniform block in the shader
     *
     * @return the buffer bindpoint associated with the given uniform block.
     */
    public int getUniformBlock(int pos) {
        GL30 gl = Gdx.gl30;
        IntBuffer block = BufferUtils.newIntBuffer(1);
        gl.glGetActiveUniformBlockiv(getHandle(),pos,GL30.GL_UNIFORM_BLOCK_BINDING,block);
        return block.get(0);
    }

    /**
     * Returns the buffer bindpoint associated with the given uniform block.
     *
     * The shader does not track the actual uniform buffer associated with this
     * bindpoint, only the bindpoint itself.  It is up to the software developer to
     * keep track of what uniform buffer is currently at that bindpoint.
     *
     * @param name      The name of the uniform block in the shader
     *
     * @return the buffer bindpoint associated with the given uniform block.
     */
    public int getUniformBlock(String name) {
        GL30 gl = Gdx.gl30;
        int index = gl.glGetUniformBlockIndex(getHandle(), name);
        if (index == GL30.GL_INVALID_INDEX) {
            return 0;
        }

        IntBuffer block = BufferUtils.newIntBuffer(1);
        gl.glGetActiveUniformBlockiv(getHandle(),index,GL30.GL_UNIFORM_BLOCK_BINDING,block);
        return block.get(0);
    }
}
