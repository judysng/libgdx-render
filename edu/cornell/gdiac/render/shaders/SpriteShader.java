package edu.cornell.gdiac.render.shaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import edu.cornell.gdiac.render.CUShader;
import edu.cornell.gdiac.render.CUSpriteBatch;

/**
 * A factory for the default {@link CUSpriteBatch} shader
 *
 * This is a full-power SpriteBatch vertex shader for both OpenGL and OpenGL ES.
 * It supports textures which can be tinted per vertex. It also supports gradients
 * (which can be used simulataneously with textures, but not with colors), as
 * well as a scissor mask.  Gradients use the color inputs as their texture
 * coordinates. Finally, there is support for very simple blur effects, which
 * can be used on font labels.
 *
 * Any alternate shader for {@link CUSpriteBatch} should support all of the attributes,
 * the texture uniform, and the projection uniform.  All other uniforms are optional.
 *
 * This shader is heavily inspired by nanovg by Mikko Mononen (memon@inside.org). 
 * It has been modified to support the LibGDX engine.
 *
 */
public class SpriteShader {
	/** The position attribute variable (uses the default from {@link ShaderProgram} */
    public static final String POSITION_ATTRIBUTE = ShaderProgram.POSITION_ATTRIBUTE;
	/** The color attribute variable (uses the default from {@link ShaderProgram} */
    public static final String COLOR_ATTRIBUTE = ShaderProgram.COLOR_ATTRIBUTE;
	/** The texture coordinate attribute variable (uses the default from {@link ShaderProgram} */
    public static final String TEXCOORD_ATTRIBUTE = ShaderProgram.TEXCOORD_ATTRIBUTE+"0";
    /** The gradient coordinate attribute variable (uses the default from {@link CUShader} */
    public static final String GRADCOORD_ATTRIBUTE = CUShader.GRADCOORD_ATTRIBUTE+"0";
    /** The projection matrix uniform */
    public static final String PROJECTION_UNIFORM = "u_projTrans";
	/** The texture uniform */
    public static final String TEXTURE_UNIFORM = "u_texture";
	/** The draw type uniform (for switching draw modes) */
	public static final String DRAWTYPE_UNIFORM = "u_drawtype";
	/** The blur step uniform (for the Gaussian blur kernel) */
    public static final String BLURSTEP_UNIFORM = "u_blurstep";
	/** The uniform block for gradients and scissors */
    public static final String CONTEXT_UNIFORM = "u_context";

	/** The individual fields of the context uniform block struct */
	public static final String[] CONTEXT_FIELDS = {
    	"scMatrix",
		"scExtent",
		"scScale",
		"gdMatrix",
		"gdInner",
		"gdOuter",
		"gdExtent",
		"gdRadius",
		"gdFeathr",
	};
	
	/** The std140 offset of the fields of the context uniform block struct (plus one more for block end) */
	public static final int[] CONTEXT_OFFSETS = { 0, 48, 56, 64, 112, 128, 144, 152, 156, 160 };

    /** The offsets for the attributes in this vertex shader (plus one more for end) */
    public static final int[] ATTRIBUTE_OFFSET = {0, 8, 12, 20, 28};

    /**
     * Returns a newly created {@link CUSpriteBatch} shader
     *
     * This method throws and error if the shader fails to compile.
     *
     * @return a newly created {@link CUSpriteBatch} shader
     */
    public static CUShader createShader() { return createShader(5); }

    /**
     * Returns a newly created {@link CUSpriteBatch} shader
     *
     * This method throws and error if the shader fails to compile.
     *
     * @return a newly created {@link CUSpriteBatch} shader
     */
    public static CUShader createShader(int numVertices) {

        final String vertexShader;
        final String fragmentShader;

        if (numVertices == 7) {
            vertexShader = "////////// SHADER BEGIN /////////\n" +
                    "// Positions\n" +
                    "in vec4 " + POSITION_ATTRIBUTE + ";\n" +
                    "out vec2 outPosition;\n" +
                    "\n" +
                    "// Colors\n" +
                    "in  vec4 " + COLOR_ATTRIBUTE + ";\n" +
                    "out vec4 outColor;\n" +
                    "\n" +
                    "// Texture coordinates\n" +
                    "in  vec2 " + TEXCOORD_ATTRIBUTE + ";\n" +
                    "out vec2 outTexCoord;\n" +
                    "\n" +
                    "// Gradient coordinates\n" +
                    "in  vec2 " + GRADCOORD_ATTRIBUTE + ";\n" +
                    "out vec2 outGradCoord;\n" +
                    "\n" +
                    "// Matrices\n" +
                    "uniform mat4 " + PROJECTION_UNIFORM + ";\n" +
                    "\n" +
                    "// Transform and pass through\n" +
                    "void main(void) {\n" +
                    "    gl_Position = " + PROJECTION_UNIFORM + "*" + POSITION_ATTRIBUTE + ";\n" +
                    "    outPosition = " + POSITION_ATTRIBUTE + ".xy; // Need untransformed for scissor\n" +
                    "    outColor = " + COLOR_ATTRIBUTE + ";\n" +
                    "    outTexCoord = " + TEXCOORD_ATTRIBUTE + ";\n" +
                    "    outGradCoord = " + GRADCOORD_ATTRIBUTE + ";\n" +
                    "}\n" +
                    "/////////// SHADER END //////////";
            fragmentShader = "////////// SHADER BEGIN /////////\n" +
                    "#ifdef GL_ES\n" +
                    "    precision mediump float;\n" +
                    "#endif\n" +
                    "\n" +
                    "// Bit vector for texturing, gradients, and scissoring\n" +
                    "uniform int  " + DRAWTYPE_UNIFORM + ";\n" +
                    "// Blur offset for simple kernel blur\n" +
                    "uniform vec2 " + BLURSTEP_UNIFORM + ";\n" +
                    "\n" +
                    "// The texture for sampling\n" +
                    "uniform sampler2D " + TEXTURE_UNIFORM + ";\n" +
                    "\n" +
                    "// The output color\n" +
                    "out vec4 frag_color;\n" +
                    "\n" +
                    "// The inputs from the vertex shader\n" +
                    "in vec2 outPosition;\n" +
                    "in vec4 outColor;\n" +
                    "in vec2 outTexCoord;\n" +
                    "in vec2 outGradCoord;\n" +
                    "\n" +
                    "// The stroke+gradient uniform block\n" +
                    "layout (std140) uniform " + CONTEXT_UNIFORM + "\n" +
                    "{\n" +
                    "    mat3 " + CONTEXT_FIELDS[0] + ";      // 48\n" +
                    "    vec2 " + CONTEXT_FIELDS[1] + ";      //  8\n" +
                    "    vec2 " + CONTEXT_FIELDS[2] + ";       //  8\n" +
                    "    mat3 " + CONTEXT_FIELDS[3] + ";      // 48\n" +
                    "    vec4 " + CONTEXT_FIELDS[4] + ";       // 16\n" +
                    "    vec4 " + CONTEXT_FIELDS[5] + ";       // 16\n" +
                    "    vec2 " + CONTEXT_FIELDS[6] + ";      //  8\n" +
                    "    float " + CONTEXT_FIELDS[7] + ";     //  4\n" +
                    "    float " + CONTEXT_FIELDS[8] + ";     //  4\n" +
                    "};\n" +
                    "\n" +
                    "// Returns an interpolation value for a box gradient\n" +
                    "float boxgradient(vec2 pt, vec2 ext, float radius, float feather) {\n" +
                    "    vec2 ext2 = ext - vec2(radius,radius);\n" +
                    "    vec2 dst = abs(pt) - ext2;\n" +
                    "    float m = min(max(dst.x,dst.y),0.0) + length(max(dst,0.0)) - radius;\n" +
                    "    return clamp((m + feather*0.5) / feather, 0.0, 1.0);\n" +
                    "}\n" +
                    "\n" +
                    "// Returns an alpha value for scissoring\n" +
                    "float scissormask(vec2 pt) {\n" +
                    "    vec2 sc = (abs((" + CONTEXT_FIELDS[0] + " * vec3(pt,1.0)).xy) - " + CONTEXT_FIELDS[1] + ");\n" +
                    "    sc = vec2(0.5,0.5) - sc * " + CONTEXT_FIELDS[2] + ";\n" +
                    "    return clamp(sc.x,0.0,1.0) * clamp(sc.y,0.0,1.0);\n" +
                    "}\n" +
                    "\n" +
                    "// Returns the result of a simple kernel blur\n" +
                    "vec4 blursample(vec2 coord) {\n" +
                    "    // Sample offsets\n" +
                    "    vec2 off[9] = vec2[](\n" +
                    "            vec2(-" + BLURSTEP_UNIFORM + ".x,  " + BLURSTEP_UNIFORM + ".y), // top-left\n" +
                    "            vec2(         0.0f,  " + BLURSTEP_UNIFORM + ".y), // top-center\n" +
                    "            vec2( " + BLURSTEP_UNIFORM + ".x,  " + BLURSTEP_UNIFORM + ".y), // top-right\n" +
                    "            vec2(-" + BLURSTEP_UNIFORM + ".x,          0.0f), // center-left\n" +
                    "            vec2(         0.0f,          0.0f), // center-center\n" +
                    "            vec2( " + BLURSTEP_UNIFORM + ".x,          0.0f), // center-right\n" +
                    "            vec2(-" + BLURSTEP_UNIFORM + ".x, -" + BLURSTEP_UNIFORM + ".y), // bottom-left\n" +
                    "            vec2(         0.0f, -" + BLURSTEP_UNIFORM + ".y), // bottom-center\n" +
                    "            vec2( " + BLURSTEP_UNIFORM + ".x, -" + BLURSTEP_UNIFORM + ".y)  // bottom-right\n" +
                    "    );\n" +
                    "\n" +
                    "    // Blur kernel\n" +
                    "    float kernel[9] = float[](\n" +
                    "            1.0 / 16, 2.0 / 16, 1.0 / 16,\n" +
                    "            2.0 / 16, 4.0 / 16, 2.0 / 16,\n" +
                    "            1.0 / 16, 2.0 / 16, 1.0 / 16\n" +
                    "    );\n" +
                    "\n" +
                    "    // Sample from the texture and average\n" +
                    "    vec4 result = vec4(0.0);\n" +
                    "    for(int ii = 0; ii < 9; ii++) {\n" +
                    "        result += texture(" + TEXTURE_UNIFORM + ", coord + off[ii])*kernel[ii];\n" +
                    "    }\n" +
                    "\n" +
                    "    return result;\n" +
                    "}\n" +
                    "\n" +
                    "void main(void) {\n" +
                    "    vec4 result;\n" +
                    "    if (mod(" + DRAWTYPE_UNIFORM + ", 4.0) >= 2.0) {\n" +
                    "        // Apply a gradient color\n" +
                    "        mat3  cmatrix = " + CONTEXT_FIELDS[3] + ";\n" +
                    "        vec2  cextent = " + CONTEXT_FIELDS[6] + ";\n" +
                    "        float cfeathr = " + CONTEXT_FIELDS[8] + ";\n" +
                    "        vec2 pt = (cmatrix * vec3(outGradCoord,1.0)).xy;\n" +
                    "        float d = boxgradient(pt,cextent," + CONTEXT_FIELDS[7] + ",cfeathr);\n" +
                    "        result = mix(" + CONTEXT_FIELDS[4] + "," + CONTEXT_FIELDS[5] + ",d);\n" +
                    "    } else {\n" +
                    "        // Use a solid color\n" +
                    "        result = outColor;\n" +
                    "    }\n" +
                    "\n" +
                    "    if (mod(" + DRAWTYPE_UNIFORM + ", 2.0) == 1.0) {\n" +
                    "        // Include texture (tinted by color or gradient)\n" +
                    "        if (" + DRAWTYPE_UNIFORM + " >= 8.0) {\n" +
                    "            result *= blursample(outTexCoord);\n" +
                    "        } else {\n" +
                    "            result *= texture(" + TEXTURE_UNIFORM + ", outTexCoord);\n" +
                    "        }\n" +
                    "    }\n" +
                    "\n" +
                    "    if (mod(" + DRAWTYPE_UNIFORM + ", 8.0) >= 4.0) {\n" +
                    "        // Apply scissor mask\n" +
                    "        result.w *= scissormask(outTexCoord);\n" +
                    "    }\n" +
                    "\n" +
                    "    frag_color = result;\n" +
                    "}\n" +
                    "/////////// SHADER END //////////";
        } else {
            vertexShader = "////////// SHADER BEGIN /////////\n"+
                    "// Positions\n"+
                    "in vec4 "+POSITION_ATTRIBUTE+";\n"+
                    "out vec2 outPosition;\n"+
                    "\n"+
                    "// Colors\n"+
                    "in  vec4 "+COLOR_ATTRIBUTE+";\n"+
                    "out vec4 outColor;\n"+
                    "\n"+
                    "// Texture coordinates\n"+
                    "in  vec2 "+TEXCOORD_ATTRIBUTE+";\n"+
                    "out vec2 outTexCoord;\n"+
                    "\n"+
                    "// Matrices\n"+
                    "uniform mat4 "+PROJECTION_UNIFORM+";\n"+
                    "\n"+
                    "// Transform and pass through\n"+
                    "void main(void) {\n"+
                    "    gl_Position = "+PROJECTION_UNIFORM+"*"+POSITION_ATTRIBUTE+";\n"+
                    "    outPosition = "+POSITION_ATTRIBUTE+".xy; // Need untransformed for scissor\n"+
                    "    outColor = "+COLOR_ATTRIBUTE+";\n"+
                    "    outTexCoord = "+TEXCOORD_ATTRIBUTE+";\n"+
                    "}\n"+
                    "/////////// SHADER END //////////";
            fragmentShader = "////////// SHADER BEGIN /////////\n"+
                    "#ifdef GL_ES\n"+
                    "    // This one line is all the difference\n"+
                    "    precision mediump float;\n"+
                    "#endif\n"+
                    "\n"+
                    "// Bit vector for texturing, gradients, and scissoring\n"+
                    "uniform int  "+DRAWTYPE_UNIFORM+";\n"+
                    "// Blur offset for simple kernel blur\n"+
                    "uniform vec2 "+BLURSTEP_UNIFORM+";\n"+
                    "\n"+
                    "// The texture for sampling\n"+
                    "uniform sampler2D "+TEXTURE_UNIFORM+";\n"+
                    "\n"+
                    "// The output color\n"+
                    "out vec4 frag_color;\n"+
                    "\n"+
                    "// The inputs from the vertex shader\n"+
                    "in vec2 outPosition;\n"+
                    "in vec4 outColor;\n"+
                    "in vec2 outTexCoord;\n"+
                    "\n"+
                    "// The stroke+gradient uniform block\n"+
                    "layout (std140) uniform "+CONTEXT_UNIFORM+"\n"+
                    "{\n"+
                    "    mat3 "+CONTEXT_FIELDS[0]+";      // 48\n"+
                    "    vec2 "+CONTEXT_FIELDS[1]+";      //  8\n"+
                    "    vec2 "+CONTEXT_FIELDS[2]+";       //  8\n"+
                    "    mat3 "+CONTEXT_FIELDS[3]+";      // 48\n"+
                    "    vec4 "+CONTEXT_FIELDS[4]+";       // 16\n"+
                    "    vec4 "+CONTEXT_FIELDS[5]+";       // 16\n"+
                    "    vec2 "+CONTEXT_FIELDS[6]+";      //  8\n"+
                    "    float "+CONTEXT_FIELDS[7]+";     //  4\n"+
                    "    float "+CONTEXT_FIELDS[8]+";     //  4\n"+
                    "};\n"+
                    "\n"+
                    "// Returns an interpolation value for a box gradient\n"+
                    "float boxgradient(vec2 pt, vec2 ext, float radius, float feather) {\n"+
                    "    vec2 ext2 = ext - vec2(radius,radius);\n"+
                    "    vec2 dst = abs(pt) - ext2;\n"+
                    "    float m = min(max(dst.x,dst.y),0.0) + length(max(dst,0.0)) - radius;\n"+
                    "    return clamp((m + feather*0.5) / feather, 0.0, 1.0);\n"+
                    "}\n"+
                    "\n"+
                    "// Returns an alpha value for scissoring\n"+
                    "float scissormask(vec2 pt) {\n"+
                    "    vec2 sc = (abs(("+CONTEXT_FIELDS[0]+" * vec3(pt,1.0)).xy) - "+CONTEXT_FIELDS[1]+");\n"+
                    "    sc = vec2(0.5,0.5) - sc * "+CONTEXT_FIELDS[2]+";\n"+
                    "    return clamp(sc.x,0.0,1.0) * clamp(sc.y,0.0,1.0);\n"+
                    "}\n"+
                    "\n"+
                    "// Returns the result of a simple kernel blur\n"+
                    "vec4 blursample(vec2 coord) {\n"+
                    "    // Sample offsets\n"+
                    "    vec2 off[9] = vec2[](\n"+
                    "            vec2(-"+BLURSTEP_UNIFORM+".x,  "+BLURSTEP_UNIFORM+".y), // top-left\n"+
                    "            vec2(         0.0f,  "+BLURSTEP_UNIFORM+".y), // top-center\n"+
                    "            vec2( "+BLURSTEP_UNIFORM+".x,  "+BLURSTEP_UNIFORM+".y), // top-right\n"+
                    "            vec2(-"+BLURSTEP_UNIFORM+".x,          0.0f), // center-left\n"+
                    "            vec2(         0.0f,          0.0f), // center-center\n"+
                    "            vec2( "+BLURSTEP_UNIFORM+".x,          0.0f), // center-right\n"+
                    "            vec2(-"+BLURSTEP_UNIFORM+".x, -"+BLURSTEP_UNIFORM+".y), // bottom-left\n"+
                    "            vec2(         0.0f, -"+BLURSTEP_UNIFORM+".y), // bottom-center\n"+
                    "            vec2( "+BLURSTEP_UNIFORM+".x, -"+BLURSTEP_UNIFORM+".y)  // bottom-right\n"+
                    "    );\n"+
                    "\n"+
                    "    // Blur kernel\n"+
                    "    float kernel[9] = float[](\n"+
                    "            1.0 / 16, 2.0 / 16, 1.0 / 16,\n"+
                    "            2.0 / 16, 4.0 / 16, 2.0 / 16,\n"+
                    "            1.0 / 16, 2.0 / 16, 1.0 / 16\n"+
                    "    );\n"+
                    "\n"+
                    "    // Sample from the texture and average\n"+
                    "    vec4 result = vec4(0.0);\n"+
                    "    for(int ii = 0; ii < 9; ii++) {\n"+
                    "        result += texture("+TEXTURE_UNIFORM+", coord + off[ii])*kernel[ii];\n"+
                    "    }\n"+
                    "\n"+
                    "    return result;\n"+
                    "}\n"+
                    "\n"+
                    "void main(void) {\n"+
                    "    vec4 result;\n"+
                    "    if (mod("+DRAWTYPE_UNIFORM+", 4.0) >= 2.0) {\n"+
                    "        // Apply a gradient color\n"+
                    "        mat3  cmatrix = "+CONTEXT_FIELDS[3]+";\n"+
                    "        vec2  cextent = "+CONTEXT_FIELDS[6]+";\n"+
                    "        float cfeathr = "+CONTEXT_FIELDS[8]+";\n"+
                    "        vec2 pt = (cmatrix * vec3(outTexCoord.xy,1.0)).xy;\n"+
                    "        float d = boxgradient(pt,cextent,"+CONTEXT_FIELDS[7]+",cfeathr);\n"+
                    "        result = mix("+CONTEXT_FIELDS[4]+","+CONTEXT_FIELDS[5]+",d);\n"+
                    "    } else {\n"+
                    "        // Use a solid color\n"+
                    "        result = outColor;\n"+
                    "    }\n"+
                    "\n"+
                    "    if (mod("+DRAWTYPE_UNIFORM+", 2.0) == 1.0) {\n"+
                    "        // Include texture (tinted by color or gradient)\n"+
                    "        if ("+DRAWTYPE_UNIFORM+" >= 8.0) {\n"+
                    "            result *= blursample(outTexCoord);\n"+
                    "        } else {\n"+
                    "            result *= texture("+TEXTURE_UNIFORM+", outTexCoord);\n"+
                    "        }\n"+
                    "    }\n"+
                    "\n"+
                    "    if (mod("+DRAWTYPE_UNIFORM+", 8.0) >= 4.0) {\n"+
                    "        // Apply scissor mask\n"+
                    "        result.w *= scissormask(outTexCoord);\n"+
                    "    }\n"+
                    "\n"+
                    "    frag_color = result;\n"+
                    "}\n"+
                    "/////////// SHADER END //////////";
        }
        CUShader.pedantic = false;
        
        String vertPrefix = CUShader.prependVertexCode;
        String fragPrefix = CUShader.prependFragmentCode;
        CUShader.prependVertexCode = "#version 330\n";
        CUShader.prependFragmentCode = "#version 330\n";
        CUShader spriteShader = new CUShader(vertexShader, fragmentShader);
        CUShader.prependVertexCode = vertPrefix;
        CUShader.prependFragmentCode = fragPrefix;

        if(!spriteShader.isCompiled()){
            throw new IllegalArgumentException("Error compiling shader: " + spriteShader.getLog());
        }

        return spriteShader;
    }
}