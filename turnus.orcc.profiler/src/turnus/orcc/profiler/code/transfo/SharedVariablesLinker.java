/* 
 * TURNUS - www.turnus.co
 * 
 * Copyright (C) 2010-2016 EPFL SCI STI MM
 *
 * This file is part of TURNUS.
 *
 * TURNUS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TURNUS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with TURNUS.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7
 * 
 * If you modify this Program, or any covered work, by linking or combining it
 * with Eclipse (or a modified version of Eclipse or an Eclipse plugin or 
 * an Eclipse library), containing parts covered by the terms of the 
 * Eclipse Public License (EPL), the licensors of this Program grant you 
 * additional permission to convey the resulting work.  Corresponding Source 
 * for a non-source form of such a combination shall include the source code 
 * for the parts of Eclipse libraries used as well as that of the  covered work.
 * 
 */
package turnus.orcc.profiler.code.transfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.orcc.df.Actor;
import net.sf.orcc.df.Network;
import net.sf.orcc.df.util.DfVisitor;
import net.sf.orcc.graph.Vertex;
import net.sf.orcc.ir.Type;
import net.sf.orcc.ir.Var;
import net.sf.orcc.util.SwitchUtil;
import net.sf.orcc.util.Void;
import turnus.common.TurnusRuntimeException;
import turnus.common.io.Logger;

/**
 * 
 * @author Simone Casale Brunet
 *
 */
public class SharedVariablesLinker extends DfVisitor<Void> {

	public static final String SHARED_VARIABLES_LIST = "$sharedVariablesList";
	public static final String SHARED_VARIABLES_PROFILE = "$sharedVariablesProfile";

	private Map<String, Collection<Var>> netSharedVars = new HashMap<String, Collection<Var>>();

	public Void caseActor(Actor actor) {

		// collect tagged variables
		for (Var var : actor.getStateVars()) {
			if (var.hasAttribute("shared")) {
				String id = var.getAttribute("shared").getValueAsString("id");
				Collection<Var> svars = netSharedVars.get(id);
				if (svars == null) {
					svars = new HashSet<Var>();
					netSharedVars.put(id, svars);
				}
				svars.add(var);
				var.setAttribute(SHARED_VARIABLES_LIST, svars);
				var.setAttribute(SHARED_VARIABLES_PROFILE, true);
			}
		}

		return SwitchUtil.DONE;
	}

	@Override
	public Void caseNetwork(Network network) {

		for (Vertex vertex : network.getVertices()) {
			doSwitch(vertex);
		}

		if (!checkConsistency()) {
			throw new TurnusRuntimeException("Shared Variables are not consistent");
		}

		if (!netSharedVars.isEmpty()) {
			Logger.info("The design has the following shared variables:");
			for (Entry<String, Collection<Var>> e : netSharedVars.entrySet()) {
				String id = e.getKey();
				for (Var var : e.getValue()) {
					Logger.info("* id=(" + id + ") actor=\"" + ((Actor) var.eContainer()).getName()
							+ "\" -> variable=\"" + var.getName() + "\", type=\"" + var.getType().toString() + "\"");
				}
			}
		}

		return SwitchUtil.DONE;
	}

	private boolean checkConsistency() {
		boolean allConsistent = true;
		for (Entry<String, Collection<Var>> e : netSharedVars.entrySet()) {
			// check consistency for this id
			String id = e.getKey();
			Type type = null;
			boolean idConsistent = true;

			for (Var var : e.getValue()) {
				if (type == null) {
					type = var.getType();
				} else {
					if (!var.getType().equals(type)) {
						idConsistent = false;
						allConsistent = false;
					}
				}
			}

			// if this id is not consistent print an error message
			if (!idConsistent) {
				Logger.error("Shared variables with id=\"" + id + "\" have not all the same type");
				for (Var var : e.getValue()) {
					Logger.error(" actor=\"" + ((Actor) var.eContainer()).getName() + "\" -> variable=\""
							+ var.getName() + "\", type=\"" + var.getType().toString() + "\"");
				}
			}
		}

		return allConsistent;

	}
}
