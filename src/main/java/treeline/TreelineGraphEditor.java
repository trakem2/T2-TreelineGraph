/*-
 * #%L
 * T2-TreelineGraph component of TrakEM2 suite.
 * %%
 * Copyright (C) 2005 - 2022 Albert Cardona, Stephan Saalfeld and others.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package treeline;

import ini.trakem2.display.Treeline;
import ini.trakem2.plugin.TPlugIn;
import ini.trakem2.utils.IJError;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;

public class TreelineGraphEditor implements TPlugIn {
	public TreelineGraphEditor() {}

	public boolean setup(Object... args) {
		System.out.println("No setup yet for TreelineGraphEditor");
		return true;
	}

	public boolean applies(final Object ob) {
		return ob instanceof Treeline;
	}

	public Object invoke(Object... args) {
		if (null == args || args.length < 1 || null == args[0] || !(args[0] instanceof Treeline)) return null;
		try {
			Class RT = Class.forName("clojure.lang.RT");
			Method var = RT.getDeclaredMethod("var", new Class[]{String.class, String.class});
			Object fn = var.invoke(null, new Object[]{"treeline.graph_editor", "as-graph"});
			return Class.forName("clojure.lang.Var")
				.getDeclaredMethod("invoke", new Class[]{Object.class})
				.invoke(fn, new Object[]{args[0]});
		} catch (Throwable e) {
			IJError.print(e);
		}
		return null;
	}

	static {
		try {
			Thread.currentThread().setContextClassLoader(ij.IJ.getClassLoader());
			Class c = Class.forName("clojure.lang.Compiler");
			Method load = c.getDeclaredMethod("load", new Class[]{Reader.class});
			load.invoke(null, new Object[]{new InputStreamReader(TreelineGraphEditor.class.getResourceAsStream("/treeline/graph_editor.clj"))});
			// As a side effect, inits clojure runtime
		} catch (Throwable t) {
			IJError.print(t);
		}
	}

}
