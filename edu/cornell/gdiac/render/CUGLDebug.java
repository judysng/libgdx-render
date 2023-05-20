package edu.cornell.gdiac.render;

import com.badlogic.gdx.graphics.GL30;

/**
 *  This class is just a collection of static functions to help with debugging OpenGL.
 */
public class CUGLDebug {
    /**
     * Returns a string description of an OpenGL error type
     *
     * @param type The OpenGL error type
     *
     * @return a string description of an OpenGL error type
     */
    public static String errorName(int type) {
        String error = "UNKNOWN";

        switch(type) {
            case 0:
                error="NO ERROR";
                break;
            case GL30.GL_INVALID_OPERATION:
                error="INVALID_OPERATION";
                break;
            case GL30.GL_INVALID_ENUM:
                error="INVALID_ENUM";
                break;
            case GL30.GL_INVALID_VALUE:
                error="INVALID_VALUE";
                break;
            case GL30.GL_OUT_OF_MEMORY:
                error="OUT_OF_MEMORY";
                break;
            case GL30.GL_INVALID_FRAMEBUFFER_OPERATION:
                error="INVALID_FRAMEBUFFER_OPERATION";
                break;
            case GL30.GL_FRAMEBUFFER_UNDEFINED:
                error="FRAMEBUFFER_UNDEFINED";
                break;
            case GL30.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT:
                error="FRAMEBUFFER_INCOMPLETE_ATTACHMENT";
                break;
            case GL30.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT:
                error="FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT";
                break;
            case GL30.GL_FRAMEBUFFER_UNSUPPORTED:
                error="FRAMEBUFFER_UNSUPPORTED";
                break;
            case GL30.GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE:
                error="FRAMEBUFFER_INCOMPLETE_MULTISAMPLE";
                break;
        }
        return error;
    }

    /**
     * Returns a string description of an OpenGL data type
     *
     * @param type The OpenGL data type
     *
     * @return a string description of an OpenGL data type
     */
    public static String typeName(int type) {
        switch(type) {
            case GL30.GL_FLOAT:
                return "GL_FLOAT";
            case GL30.GL_FLOAT_VEC2:
                return "GL_FLOAT";
            case GL30.GL_FLOAT_VEC3:
                return "GL_FLOAT_VEC2";
            case GL30.GL_FLOAT_VEC4:
                return "GL_FLOAT_VEC4";
            case GL30.GL_FLOAT_MAT2:
                return "GL_FLOAT_MAT2";
            case GL30.GL_FLOAT_MAT3:
                return "GL_FLOAT_MAT3";
            case GL30.GL_FLOAT_MAT4:
                return "GL_FLOAT_MAT4";
            case GL30.GL_FLOAT_MAT2x3:
                return "GL_FLOAT_MAT2x3";
            case GL30.GL_FLOAT_MAT2x4:
                return "GL_FLOAT_MAT2x4";
            case GL30.GL_FLOAT_MAT3x2:
                return "GL_FLOAT_MAT3x2";
            case GL30.GL_FLOAT_MAT3x4:
                return "GL_FLOAT_MAT3x4";
            case GL30.GL_FLOAT_MAT4x2:
                return "GL_FLOAT_MAT4x2";
            case GL30.GL_FLOAT_MAT4x3:
                return "GL_FLOAT_MAT4x3";
            case GL30.GL_INT:
                return "GL_INT";
            case GL30.GL_INT_VEC2:
                return "GL_INT_VEC2";
            case GL30.GL_INT_VEC3:
                return "GL_INT_VEC3";
            case GL30.GL_INT_VEC4:
                return "GL_INT_VEC4";
            case GL30.GL_UNSIGNED_INT:
                return "GL_UNSIGNED_INT";
            case GL30.GL_UNSIGNED_INT_VEC2:
                return "GL_UNSIGNED_INT_VEC2";
            case GL30.GL_UNSIGNED_INT_VEC3:
                return "GL_UNSIGNED_INT_VEC3";
            case GL30.GL_UNSIGNED_INT_VEC4:
                return "GL_UNSIGNED_INT_VEC4";
            case GL30.GL_SAMPLER_2D:
                return "GL_SAMPLER_2D";
            case GL30.GL_SAMPLER_3D:
                return "GL_SAMPLER_3D";
            case GL30.GL_SAMPLER_CUBE:
                return "GL_SAMPLER_CUBE";
            case GL30.GL_SAMPLER_2D_SHADOW:
                return "GL_SAMPLER_2D_SHADOW";
            case GL30.GL_UNIFORM_BUFFER:
                return "GL_UNIFORM_BUFFER";
        }
        return "GL_UNKNOWN";
    }
}
