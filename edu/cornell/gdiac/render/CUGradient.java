package edu.cornell.gdiac.render;

import com.badlogic.gdx.math.*;
import com.badlogic.gdx.graphics.Color;

/**
 * This class defines a two color gradient.
 *
 * All gradients, including linear and radial gradients, are variations
 * of (rounded) box gradients. A box gradient is defined by (in terms of
 * largest to smallest data):
 *
 * <ul>
 *    <li>An affine transform (for offset and rotation)</li>
 *    <li>An inner color</li>
 *    <li>An outer color</li>
 *    <li>A size vector of the gradient</li>
 *    <li>A corner radius for the rounded rectangle</li>
 *    <li>A feather factor for the transition speed.</li>
 * </ul>
 *
 * Assuming this data is in std140 format, this is a 24 element array of floats.
 * And this is the format that this data is represented in the {@link #getData}
 * method so that it can be passed to a {@link CUUniformBuffer} for improved
 * performance. It is also possible to get access to the individual components
 * of the paint gradient, to pass them to a shader directly (though the transform
 * must be inverted first if it is passed directly).
 *
 * Paint gradients are applied to surfaces in the same way textures are. The
 * gradient is defined on a unit square from (0,0) to (1,1). To be consistent
 * with textures, the origin is at the top right corner. To apply the gradient,
 * the shader should use the texture coordinates of each vertex (or an attribute
 * similar to texture coordinates) combined with the uniforms for this gradient.
 *
 * For {@link CUSpriteBatch} and scene graph compatibility, gradients may be tinted
 * by another color.  This tint is managed separately from the gradient definition.
 *
 * For simplicity we only permit two colors in a gradient.  For multicolored
 * gradients, the shape should be tesellated with multiple gradient values.
 */
public class CUGradient {
    private Affine2 inverse;
    private Color inner;
    private Color outer;
    private Vector2    extent;
    private float   radius;
    private float   feather;

    /**
     * Creates a degenerate, white-colored gradient.
     */
    public CUGradient() {
        radius = 0;
        feather = 0;
        inverse = new Affine2();
        inner = new Color(1.0f, 1.0f, 1.0f, 1.0f);
        outer = new Color(1.0f, 1.0f, 1.0f, 1.0f);
        extent = new Vector2();
    }

    /**
     * Initializes a degenerate gradient of the given color.
     *
     * @param color The gradient color
     */
    public CUGradient(Color color) {
        this();
        set(color);
    }

    /**
     * Initializes a linear gradient of the two colors.
     *
     * In a linear color, the inner starts at position start, and
     * transitions to the outer color at position end. The transition
     * is along the vector end-start.
     *
     * The values start and end are specified in texture coordinates.
     * That is, (0,0) is the top left corner of the gradient bounding
     * box and (1,1) is the bottom right corner.  It is permissible
     * to have coordinates out of range (so negative or greater than
     * 1). Such values will be interpretted accordingly.
     *
     * @param inner The inner gradient color
     * @param outer The outer gradient color
     * @param start The start position of the inner color
     * @param end   The start position of the outer color
     */
    public CUGradient(Color inner, Color outer, Vector2 start, Vector2 end) {
        this();
        set(inner,outer,start,end);
    }

    /**
     * Creates a copy of the given gradient.
     *
     * @param grad  The gradient to copy
     */
    public CUGradient(CUGradient grad) {
        inverse = grad.inverse;
        inner  = grad.inner;
        outer  = grad.outer;
        extent = grad.extent;
        radius = grad.radius;
        feather = grad.feather;
    }

    /**
     * Sets this to be a simple radial gradient of the two colors.
     *
     * In a simple radial gradient, the inner color starts at the center
     * and transitions smoothly to the outer color at the given radius.
     *
     * The center and radius are specified in texture coordinates.
     * That is, (0,0) is the top left corner of the gradient bounding
     * box and (1,1) is the bottom right corner.  It is permissible
     * to a center value out of range (so coordinates negative or
     * greater than 1). Such values will be interpretted accordingly.
     *
     * @param inner     The inner gradient color
     * @param outer     The outer gradient color
     * @param center    The center of the radial gradient
     * @param radius    The radius for the outer color
     *
     * @return this gradient, returned for chaining
     */
    public CUGradient(Color inner, Color outer, Vector2 center, float radius) {
        float r = radius*0.5f;
        inverse = new Affine2();
        inverse.m02 = -center.x;
        inverse.m12 = -center.y;

        this.inner = inner;
        this.outer = outer;
        extent = new Vector2(r,r);
        this.radius  = r;
        feather = radius;
    }

    /**
     * Initializes a general radial gradient of the two colors.
     *
     * In a general radial gradient, the inner color starts at the center
     * and continues to the inner radius.  It then transitions smoothly
     * to the outer color at the outer radius.
     *
     * The center and radii are all specified in texture coordinates.
     * That is, (0,0) is the top left corner of the gradient bounding
     * box and (1,1) is the bottom right corner.  It is permissible
     * to a center value out of range (so coordinates negative or
     * greater than 1). Such value will be interpretted accordingly.
     *
     * @param inner     The inner gradient color
     * @param outer     The outer gradient color
     * @param center    The center of the radial gradient
     * @param iradius   The radius for the inner color
     * @param oradius   The radius for the outer color
     */
    public CUGradient(Color inner, Color outer, Vector2 center, float iradius, float oradius) {
        this();
        set(inner,outer,center,iradius,oradius);
    }

    /**
     * Initializes a box gradient of the two colors.
     *
     * Box gradients paint the inner color in a rounded rectangle, and
     * then use a feather setting to transition to the outer color. The
     * box position and corner radius are given in texture coordinates.
     * That is, (0,0) is the top left corner of the gradient bounding
     * box and (1,1) is the bottom right corner. It is permissible for
     * these coordinates to be out of range (so negative values or greater
     * than 1). Such values will be interpretted accordingly.
     *
     * To be well-defined, the corner radius should be no larger than
     * half the width and height (at which point it defines an ellipse).
     * Shapes with abnormally large radii are undefined.
     *
     * The feather value acts like the inner and outer radius of a radial
     * gradient. If a line is drawn from the center of the round rectangle
     * to a corner, consider two segments.  The first starts at the corner
     * and moves towards the center of the rectangle half-feather in
     * distance.  The end of this segment is the end of the inner color
     * The second second starts at the corner and moves in the opposite
     * direction the same amount.  The end of this segement is the other
     * color.  In between, the colors are smoothly interpolated.
     *
     * So, if feather is 0, there is no gradient and the shift from
     * inner color to outer color is immediate.  On the other hand,
     * if feather is larger than the width and hight of the rectangle,
     * the inner color immediately transitions to the outer color.
     *
     * @param inner     The inner gradient color
     * @param outer     The outer gradient color
     * @param box       The bounds of the rounded rectangle.
     * @param radius    The corner radius of the rounded rectangle.
     * @param feather   The feather value for color interpolation
     */
    public CUGradient(Color inner, Color outer, Rectangle box, float radius, float feather) {
        this();
        set(inner,outer,box,radius,feather);
    }

    //region Setters
    /**
     * Sets this gradient to be a copy of the given one.
     *
     * All internal attributes are cloned.
     *
     * @param grad  The gradient to copy
     *
     * @return this gradient, returned for chaining
     */
    public CUGradient set(CUGradient grad) {
        inverse.set(grad.inverse);
        inner.set(grad.inner);
        outer.set(grad.outer);
        extent.set(grad.extent);
        radius = grad.radius;
        feather = grad.feather;
        return this;
    }

    /**
     * Sets this to be a degenerate gradient with the given color.
     *
     * The inner color and outer color will be the same, so there
     * will be no transition.
     *
     * @param color  The gradient color
     *
     * @return this gradient, returned for chaining
     */
    public CUGradient set(Color color) {
        inverse.idt();
        inner.set(color);
        outer.set(color);
        return this;
    }

    /**
     * Sets this to be a linear gradient of the two colors.
     *
     * In a linear color, the inner starts at position start, and
     * transitions to the outer color at position end. The transition
     * is along the vector end-start.
     *
     * The values start and end are specified in texture coordinates.
     * That is, (0,0) is the top left corner of the gradient bounding
     * box and (1,1) is the bottom right corner.  It is permissible
     * to have coordinates out of range (so negative or greater than
     * 1). Such values will be interpretted accordingly.
     *
     * @param inner The inner gradient color
     * @param outer The outer gradient color
     * @param start The start position of the inner color
     * @param end   The start position of the outer color
     *
     * @return this gradient, returned for chaining
     */
    public CUGradient set(Color inner, Color outer, Vector2 start, Vector2 end) {
        float dx, dy, d;
        float large = 1e5f;

        // Calculate transform aligned to the line
        dx = end.x - start.x;
        dy = end.y - start.y;
        d = (float)Math.sqrt(dx*dx + dy*dy);
        if (d > 0.0001f) {
            dx /= d;
            dy /= d;
        } else {
            dx = 0;
            dy = 1;
        }
        inverse.m00 = dy; inverse.m10 = -dx;
        inverse.m01 = dx; inverse.m11 = dy;
        inverse.m02 = start.x - dx*large;
        inverse.m12 = start.y - dy*large;
        inverse.inv();

        this.inner.set(inner == null ? Color.WHITE : inner);
        this.outer.set(outer == null ? Color.WHITE : outer);
        extent.set(large, large +d*0.5f);
        radius = 0.0f;
        feather = d;
        return this;
    }

    /**
     * Sets this to be a simple radial gradient of the two colors.
     *
     * In a simple radial gradient, the inner color starts at the center
     * and transitions smoothly to the outer color at the given radius.
     *
     * The center and radius are specified in texture coordinates.
     * That is, (0,0) is the top left corner of the gradient bounding
     * box and (1,1) is the bottom right corner.  It is permissible
     * to a center value out of range (so coordinates negative or
     * greater than 1). Such values will be interpretted accordingly.
     *
     * @param inner     The inner gradient color
     * @param outer     The outer gradient color
     * @param center    The center of the radial gradient
     * @param radius    The radius for the outer color
     *
     * @return this gradient, returned for chaining
     */
    public CUGradient set(Color inner, Color outer, Vector2 center, float radius) {
        inverse.idt();
        inverse.m02 = -center.x;
        inverse.m12 = -center.y;

        this.inner.set(inner == null ? Color.WHITE : inner);
        this.outer.set(outer == null ? Color.WHITE : outer);
        extent.set(radius,radius);
        this.radius  = radius;
        feather = 0.0f;
        return this;
    }

    /**
     * Sets this to be a general radial gradient of the two colors.
     *
     * In a general radial gradient, the inner color starts at the center
     * and continues to the inner radius.  It then transitions smoothly
     * to the outer color at the outer radius.
     *
     * The center and radii are all specified in texture coordinates.
     * That is, (0,0) is the top left corner of the gradient bounding
     * box and (1,1) is the bottom right corner.  It is permissible
     * to a center value out of range (so coordinates negative or
     * greater than 1). Such value will be interpretted accordingly.
     *
     * @param inner     The inner gradient color
     * @param outer     The outer gradient color
     * @param center    The center of the radial gradient
     * @param iradius   The radius for the inner color
     * @param oradius   The radius for the outer color
     *
     * @return this gradient, returned for chaining
     */
    public CUGradient set(Color inner, Color outer, Vector2 center, float iradius, float oradius) {
        float r = (iradius+oradius)*0.5f;
        inverse.idt();
        inverse.m02 = -center.x;
        inverse.m12 = -center.y;

        this.inner.set(inner == null ? Color.WHITE : inner);
        this.outer.set(outer == null ? Color.WHITE : outer);
        extent.set(r,r);
        radius  = r;
        feather = oradius-iradius;
        return this;
    }

    /**
     * Sets this to be a box gradient of the two colors.
     *
     * Box gradients paint the inner color in a rounded rectangle, and
     * then use a feather setting to transition to the outer color. The
     * box position and corner radius are given in texture coordinates.
     * That is, (0,0) is the top left corner of the gradient bounding
     * box and (1,1) is the bottom right corner. It is permissible for
     * these coordinates to be out of range (so negative values or greater
     * than 1). Such values will be interpretted accordingly.
     *
     * To be well-defined, the corner radius should be no larger than
     * half the width and height (at which point it defines an ellipse).
     * Shapes with abnormally large radii are undefined.
     *
     * The feather value acts like the inner and outer radius of a radial
     * gradient. If a line is drawn from the center of the round rectangle
     * to a corner, consider two segments.  The first starts at the corner
     * and moves towards the center of the rectangle half-feather in
     * distance.  The end of this segment is the end of the inner color
     * The second second starts at the corner and moves in the opposite
     * direction the same amount.  The end of this segement is the other
     * color.  In between, the colors are smoothly interpolated.
     *
     * So, if feather is 0, there is no gradient and the shift from
     * inner color to outer color is immediate.  On the other hand,
     * if feather is larger than the width and hight of the rectangle,
     * the inner color immediately transitions to the outer color.
     *
     * @param inner     The inner gradient color
     * @param outer     The outer gradient color
     * @param box       The bounds of the rounded rectangle.
     * @param radius    The corner radius of the rounded rectangle.
     * @param feather   The feather value for color interpolation
     *
     * @return this gradient, returned for chaining
     */
    public CUGradient set(Color inner, Color outer, Rectangle box, float radius, float feather) {
        inverse.idt();
        inverse.m02 = box.x+box.width*0.5f;
        inverse.m12 = box.y+box.height*0.5f;
        inverse.inv();

        this.inner.set(inner == null ? Color.WHITE : inner);
        this.outer.set(outer == null ? Color.WHITE : outer);
        extent.set(box.width*0.5f,box.height*0.5f);
        this.radius  = radius;
        this.feather = feather;
        return this;
    }
    //endregion

    //region Attributes
    /**
     * Returns (a copy of) the transform component of this gradient
     *
     * The transform maps the origin of the current coordinate system to
     * the center and rotation of the rounded rectangular box with the
     * inner color. Applying further transforms will adjust the gradient
     * in texture space.
     *
     * The transform is primarily for representing rotation.  It typically
     * only has a scale component when the gradient is linear.
     *
     * If this transform is passed directly to a gradient shader, it should
     * be inverted first.  If you really need to pass individual components
     * to a shader, you should use {@link #getComponents} instead.
     *
     * @return the transform component of this gradient
     */
    public Affine2 getTransform() {
        return new Affine2(inverse).inv();
    }

    /**
     * Sets the transform component of this gradient
     *
     * The transform maps the origin of the current coordinate system to
     * the center and rotation of the rounded rectangular box with the
     * inner color. Applying further transforms will adjust the gradient
     * in texture space.
     *
     * The transform is primarily for representing rotation.  It typically
     * only has a scale component when the gradient is linear.
     *
     * If this transform is passed directly to a gradient shader, it should
     * be inverted first.  If you really need to pass individual components
     * to a shader, you should use {@link #getComponents} instead.
     *
     * @param transform The transform component of this gradient
     */
    public void setTransform(Affine2 transform) {
        inverse.set(transform);
        inverse.inv();
    }


    /**
     * Sets the transform component of this gradient
     *
     * The transform maps the origin of the current coordinate system to
     * the center and rotation of the rounded rectangular box with the
     * inner color. Applying further transforms will adjust the gradient
     * in texture space.
     *
     * The transform is primarily for representing rotation.  It typically
     * only has a scale component when the gradient is linear.
     *
     * If this transform is passed directly to a gradient shader, it should
     * be inverted first.  If you really need to pass individual components
     * to a shader, you should use {@link #getComponents} instead.
     *
     * @param transform The transform component of this gradient
     */
    public  void setTransform(Matrix4 transform) {
        inverse.set(transform);
        inverse.inv();
    }

    /**
     * Returns (a copy of) the inner color of this gradient
     *
     * The inner color is the color inside of the rounded rectangle
     * defining the gradient.
     *
     * @return the inner color of this gradient
     */
    public Color getInnerColor() {
        return new Color(inner);
    }

    /**
     * Sets the inner color of this gradient
     *
     * The inner color is the color inside of the rounded rectangle
     * defining the gradient.
     *
     * @param color The inner color of this gradient
     */
    public void setInnerColor(Color color) {
        inner.set(color == null ? Color.WHITE : color);
    }

    /**
     * Returns (a copy of) the outer color of this gradient
     *
     * The outer color is the color outside of the rounded rectangle
     * defining the gradient.
     *
     * @return the outer color of this gradient
     */
    public Color getOuterColor() {
        return new Color(outer);
    }

    /**
     * Sets the outer color of this gradient
     *
     * The outer color is the color outside of the rounded rectangle
     * defining the gradient.
     *
     * @param color The outer color of this gradientt
     */
    public void setOuterColor(Color color) {
        outer.set(color == null ? Color.WHITE : color);
    }

    /**
     * Returns (a copy of) the extent of this gradient
     *
     * The extent is the vector from the center of the rounded
     * rectangle to one of its corners.  It defines the size of
     * the rounded rectangle.
     *
     * @return the extent of this gradient
     */
    public Vector2 getExtent() {
        return new Vector2(extent);
    }

    /**
     * Sets the extent of this gradient
     *
     * The extent is the vector from the center of the rounded
     * rectangle to one of its corners.  It defines the size of
     * the rounded rectangle.
     *
     * @param extent    The extent of this gradient
     */
    public void setExtent(Vector2 extent) {
        this.extent.set(extent == null ? Vector2.Zero : extent);
    }

    /**
     * Returns the corner radius of the gradient rectangle
     *
     * The corner radius is the radius of the circle inscribed
     * in (each) corner of the rounded rectangle.
     *
     * To be well-defined, it should be no more than half the width
     * and height. When it is equal to both half the width and
     * half the height, the rectangle becomes a circle. For large
     * values this inner rectangle will collapse and disappear
     * completely.
     *
     * @return the corner radius of the gradient rectangle
     */
    public float getRadius() {
        return radius;
    }

    /**
     * Sets the corner radius of the gradient rectangle
     *
     * The corner radius is the radius of the circle inscribed
     * in (each) corner of the rounded rectangle.
     *
     * To be well-defined, it should be no more than half the width
     * and height. When it is equal to both half the width and
     * half the height, the rectangle becomes a circle. For large
     * values this inner rectangle will collapse and disappear
     * completely.
     *
     * @param radius    The corner radius of the gradient rectangle
     */
    public void setRadius(float radius) {
        this.radius = radius;
    }

    /**
     * Returns the feather value for this gradient.
     *
     * The feature value is perhaps the trickiest value to understand.
     * This value acts like the inner and outer radius of a radial
     * gradient. If a line is drawn from the center of the round rectangle
     * to a corner, consider two segments.  The first starts at the corner
     * and moves towards the center of the rectangle half-feather in
     * distance.  The end of this segment is the end of the inner color
     * The second second starts at the corner and moves in the opposite
     * direction the same amount.  The end of this segement is the other
     * color.  In between, the colors are smoothly interpolated.
     *
     * So, if feather is 0, there is no gradient and the shift from
     * inner color to outer color is immediate.  On the other hand,
     * if feather is larger than the width and hight of the rectangle,
     * the inner color immediately transitions to the outer color.
     *
     * @return the feather value for this gradient.
     */
    public float getFeather() {
        return feather;
    }

    /**
     * Sets the feather value for this gradient.
     *
     * The feature value is perhaps the trickiest value to understand.
     * This value acts like the inner and outer radius of a radial
     * gradient. If a line is drawn from the center of the round rectangle
     * to a corner, consider two segments.  The first starts at the corner
     * and moves towards the center of the rectangle half-feather in
     * distance.  The end of this segment is the end of the inner color
     * The second second starts at the corner and moves in the opposite
     * direction the same amount.  The end of this segement is the other
     * color.  In between, the colors are smoothly interpolated.
     *
     * So, if feather is 0, there is no gradient and the shift from
     * inner color to outer color is immediate.  On the other hand,
     * if feather is larger than the width and hight of the rectangle,
     * the inner color immediately transitions to the outer color.
     *
     * @param feather   The feather value for this gradient.
     */
    public void setFeather(float feather) {
        this.feather = feather;
    }
    //endregion

    //region Transforms
    /**
     * Applies a rotation to this gradient.
     *
     * The rotation is in radians, counter-clockwise about the given axis.
     *
     * @param angle The angle (in radians).
     *
     * @return This gradient, after rotation.
     */
    public CUGradient rotate(float angle) {
        Affine2 temp = new Affine2(  );
        temp.rotate(-angle);
        inverse.preMul(temp);
        return this;
    }

    /**
     * Applies a uniform scale to this gradient.
     *
     * @param value The scalar to multiply by.
     *
     * @return This gradient, after scaling.
     */
    public CUGradient scale(float value) {
        if (value == 0) {
            inverse.m00 = 0;
            inverse.m01 = 0;
            inverse.m02 = 0;
            inverse.m10 = 0;
            inverse.m11 = 0;
            inverse.m12 = 0;
            return this;
        }
        Affine2 temp  = new Affine2(  );
        temp.scale(1/value,1/value);
        inverse.preMul(temp);
        return this;
    }

    /**
     * Applies a non-uniform scale to this gradient.
     *
     * @param s        The vector storing the individual scaling factors
     *
     * @return This gradient, after scaling.
     */
    public CUGradient scale(Vector2 s) {
        if (s.x == 0 || s.y == 0) {
            inverse.m00 = 0;
            inverse.m01 = 0;
            inverse.m02 = 0;
            inverse.m10 = 0;
            inverse.m11 = 0;
            inverse.m12 = 0;
            return this;
        }
        Affine2 temp = new Affine2(  );
        temp.scale(1/s.x,1/s.y);
        inverse.preMul(temp);
        return this;
    }

    /**
     * Applies a non-uniform scale to this gradient.
     *
     * @param sx    The amount to scale along the x-axis.
     * @param sy    The amount to scale along the y-axis.
     *
     * @return This gradient, after scaling.
     */
    public CUGradient scale(float sx, float sy) {
        if (sx == 0 || sy == 0) {
            inverse.m00 = 0;
            inverse.m01 = 0;
            inverse.m02 = 0;
            inverse.m10 = 0;
            inverse.m11 = 0;
            inverse.m12 = 0;
            return this;
        }
        Affine2 temp = new Affine2(  );
        temp.scale(1/sx,1/sy);
        inverse.preMul(temp);
        return this;
    }

    /**
     * Applies a translation to this gradient.
     *
     * The translation should be in texture coordinates, which (generally)
     * have values 0 to 1.
     *
     * @param t     The vector storing the individual translation offsets
     *
     * @return This gradient, after translation.
     */
    public CUGradient translate(Vector2 t) {
        Affine2 temp = new Affine2(  );
        temp.translate(-t.x,-t.y);
        inverse.preMul( temp );
        return this;
    }

    /**
     * Applies a translation to this gradient.
     *
     * The translation should be in texture coordinates, which (generally)
     * have values 0 to 1.
     *
     * @param tx    The translation offset for the x-axis.
     * @param ty    The translation offset for the y-axis.
     *
     * @return This gradient, after translation.
     */
    public CUGradient translate(float tx, float ty) {
        Affine2 temp = new Affine2(  );
        temp.translate(-tx,-ty);
        inverse.preMul( temp );
        return this;
    }

    /**
     * Applies the given transform to this gradient.
     *
     * This transform is applied after the existing gradient transform
     * (which is natural, since the transform defines the gradient shape).
     * To pre-multiply a transform, set the transform directly.
     *
     * @param mat The matrix to multiply by.
     *
     * @return A reference to this (modified) Gradient for chaining.
     */
    public CUGradient multiply(Matrix4 mat) {
        Affine2 temp = new Affine2(  );
        temp.set( mat );
        temp.inv();
        inverse.preMul( temp );
        return this;
    }

    /**
     * Applies the given transform to this gradient.
     *
     * This transform is applied after the existing gradient transform
     * (which is natural, since the transform defines the gradient shape).
     * To pre-multiply a transform, set the transform directly.
     *
     * @param aff The matrix to multiply by.
     *
     * @return A reference to this (modified) Gradient for chaining.
     */
    public CUGradient multiply(Affine2 aff) {
        Affine2 temp = new Affine2(  );
        temp.set( aff );
        temp.inv();
        inverse.preMul( temp );
        return this;
    }
    //endregion

    //region Conversion
    /**
     * Reads the gradient into the provided array
     *
     * The gradient is written to the given array in std140 format.
     * That is (1) 12 floats for the affine transform (as a 3x3
     * homogenous matrix), (2) 4 floats for the inner color, (3)
     * 4 floats for the outer color, (4) 2 floats for the extent,
     * (5) 1 float for the corner radius, and (6) 1 float for the
     * feather value.  Values are written in this order.
     *
     * @param array     The array to store the values
     * @param offset    The offset in the array to begin writing
     *
     * @return a reference to the array for chaining
     */
    public float[] getData(float[] array, int offset) {
        array[offset  ] = inverse.m00;
        array[offset+1 ] = inverse.m10;
        array[offset+2 ] = 0;
        array[offset+3 ] = 0;
        array[offset+4 ] = inverse.m01;
        array[offset+5 ] = inverse.m11;
        array[offset+6 ] = 0;
        array[offset+7 ] = 0;
        array[offset+8 ] = inverse.m02;
        array[offset+9 ] = inverse.m12;
        array[offset+10] = 1;
        array[offset+11] = 0;

        array[offset+12] = inner.r;
        array[offset+13] = inner.g;
        array[offset+14] = inner.b;
        array[offset+15] = inner.a;
        array[offset+16] = outer.r;
        array[offset+17] = outer.g;
        array[offset+18] = outer.b;
        array[offset+19] = outer.a;

        array[offset+20] = extent.x;
        array[offset+21] = extent.y;
        array[offset+22] = radius;
        array[offset+23] = feather;
        return array;
    }

    /**
     * Reads the gradient into the provided array
     *
     * The gradient is written to the array so that it can be passed
     * the the shader one component at a time (e.g. NOT in std140 format).
     * It differs from getData in that it only uses 9 floats for the
     * affine transform (as a 3x3 homogenous matrix).
     *
     * @param array     The array to store the values
     * @param offset    The offset in the array to begin writing
     *
     * @return a reference to the array for chaining
     */
    public float[] getComponents(float[] array, int offset) {
        array[offset   ] = inverse.m00;
        array[offset+1 ] = inverse.m10;
        array[offset+2 ] = 0;
        array[offset+3 ] = inverse.m01;
        array[offset+4 ] = inverse.m11;
        array[offset+5 ] = 0;
        array[offset+6 ] = inverse.m02;
        array[offset+7 ] = inverse.m12;
        array[offset+8 ] = 1;

        array[offset+9 ] = inner.r;
        array[offset+10] = inner.g;
        array[offset+11] = inner.b;
        array[offset+12] = inner.a;
        array[offset+13] = outer.r;
        array[offset+14] = outer.g;
        array[offset+15] = outer.b;
        array[offset+16] = outer.a;

        array[offset+17] = extent.x;
        array[offset+18] = extent.y;
        array[offset+19] = radius;
        array[offset+20] = feather;
        return array;
    }

    /**
     * Returns a string representation of this gradient for debuggging purposes.
     *
     * If verbose is true, the string will include class information.  This
     * allows us to unambiguously identify the class.
     *
     * @return a string representation of this gradient for debuggging purposes.
     */
    @Override
    public String toString() {
        String result = "Gradient[";
        result += inverse;
        result += "; ";
        result += extent;
        result += "; ";
        result += radius;
        result += "; ";
        result += feather;
        result += "]";
        return result;
    }
}
