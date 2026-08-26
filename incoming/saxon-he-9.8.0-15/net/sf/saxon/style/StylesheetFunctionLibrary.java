////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.UserFunctionCall;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.om.Function;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;


/**
 * A StylesheetFunctionLibrary contains functions defined by the user in a stylesheet. This library is used at
 * compile time only, as it contains references to the actual XSLFunction objects. Binding to a function in this
 * library registers the function call on a fix-up list to be notified when the actual compiled function becomes
 * available.
 */

public class StylesheetFunctionLibrary implements FunctionLibrary {

    private StylesheetPackage pack;
    private boolean overrideExtensionFunction;

    /**
     * Create a FunctionLibrary that provides access to stylesheet functions
     *
     * @param sheet                     The XSLT package
     * @param overrideExtensionFunction set to true if this library is to contain functions specifying override="yes",
     *                                  or to false if it is to contain functions specifying override="no". (XSLT uses two instances
     *                                  of this class, one for overrideExtensionFunction functions and one for non-overrideExtensionFunction functions.)
     */
    public StylesheetFunctionLibrary(StylesheetPackage sheet, boolean overrideExtensionFunction) {
        this.pack = sheet;
        this.overrideExtensionFunction = overrideExtensionFunction;
    }

    /**
     * Ask whether the functions in this library are "overrideExtensionFunction" functions, that is, defined with
     * xsl:function override="yes".
     *
     * @return true if these are overrideExtensionFunction functions, false otherwise
     */

    public boolean isOverrideExtensionFunction() {
        return overrideExtensionFunction;
    }

    /**
     * Get the stylesheet package to which this function library relates
     * @return the stylesheet package
     */

    public StylesheetPackage getStylesheetPackage() {
        return pack;
    }

    /**
     * Bind a function, given the URI and local parts of the function name,
     * and the list of expressions supplied as arguments. This method is called at compile
     * time.
     *
     * @param functionName   The name of the function
     * @param staticArgs   The expressions supplied statically in the function call. The intention is
     *                     that the static type of the arguments (obtainable via getItemType() and getCardinality() may
     *                     be used as part of the binding algorithm.
     * @param env          The static context
     * @return An object representing the extension function to be called, if one is found;
     *         null if no extension function was found matching the required name and arity.
     * @throws net.sf.saxon.trans.XPathException
     *          if a function is found with the required name and arity, but
     *          the implementation of the function cannot be loaded or used; or if an error occurs
     *          while searching for the function; or if this function library "owns" the namespace containing
     *          the function call, but no function was found.
     */

    public Expression bind(SymbolicName.F functionName, Expression[] staticArgs, StaticContext env)
            throws XPathException {
        UserFunction fn = pack.getFunction(functionName);
        if (fn == null) {
            return null;
        }

        final UserFunctionCall fc = new UserFunctionCall();
        fc.setFunctionName(functionName.getComponentName());
        fc.setArguments(staticArgs);
        fc.setFunction(fn);

        if (env instanceof ExpressionContext) {
            // compile-time binding of a static function call in XSLT
            final PrincipalStylesheetModule psm = ((ExpressionContext) env).getStyleElement().getCompilation().getPrincipalStylesheetModule();
            final ExpressionVisitor visitor = ExpressionVisitor.make(env);
            psm.addFixupAction(new PrincipalStylesheetModule.Action() {
                public void doAction() throws XPathException {
                    if (fc.getFunction() == null) {
                        Component target = psm.getComponent(fc.getSymbolicName());
                        UserFunction fn = (UserFunction) target.getActor();
                        if (fn != null) {

                            // TODO: next line is needed only in odd cases like key-089 where the function name isn't known during ufCall.typeCheck()
                            // See bug 2879
                            fc.checkFunctionCall(fn, visitor);
                            fc.computeArgumentEvaluationModes();
                            fc.setStaticType(fn.getResultType());
                        } else {
                            XPathException err = new XPathException("There is no available function named " + fc.getDisplayName() +
                                                                            " with " + fc.getArity() + " arguments", "XPST0017");
                            err.setLocator(fc.getLocation());
                            throw err;
                        }
                    }
                }
            });
        } else {
            // must be a call within xsl:evaluate
        }


        return fc;
    }



    /**
     * Test whether a function with a given name and arity is available
     * <p>This supports the function-available() function in XSLT.</p>
     *
     * @param functionName the qualified name of the function being called
     * @return true if a function of this name and arity is available for calling
     */
    public boolean isAvailable(SymbolicName.F functionName) {
        return pack.getFunction(functionName) != null;
    }

    /**
     * This method creates a copy of a FunctionLibrary: if the original FunctionLibrary allows
     * new functions to be added, then additions to this copy will not affect the original, or
     * vice versa.
     *
     * @return a copy of this function library. This must be an instance of the original class.
     */

    public FunctionLibrary copy() {
        return this;
    }

}

