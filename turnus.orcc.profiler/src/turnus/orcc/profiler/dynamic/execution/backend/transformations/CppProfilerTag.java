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
package turnus.orcc.profiler.dynamic.execution.backend.transformations;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.sf.orcc.df.Action;
import net.sf.orcc.df.Actor;
import net.sf.orcc.df.Port;
import net.sf.orcc.df.util.DfVisitor;
import net.sf.orcc.ir.BlockBasic;
import net.sf.orcc.ir.BlockIf;
import net.sf.orcc.ir.BlockWhile;
import net.sf.orcc.ir.ExprBinary;
import net.sf.orcc.ir.ExprUnary;
import net.sf.orcc.ir.InstAssign;
import net.sf.orcc.ir.InstCall;
import net.sf.orcc.ir.InstLoad;
import net.sf.orcc.ir.InstStore;
import net.sf.orcc.ir.Procedure;
import net.sf.orcc.ir.util.AbstractIrVisitor;
import turnus.orcc.profiler.util.TurnusModelAdapter;

/**
 * Adds Profiling information on Block, BlockIf and BlockWhile
 * 
 * @author Endri Bezati
 *
 */

public class CppProfilerTag extends DfVisitor<Void> {

	Set<String> inputPorts;
	Set<String> outputPorts;

	@Override
	public Void caseActor(Actor actor) {
		for (Procedure procedure : actor.getProcs()) {
			new InnerVisitor().doSwitch(procedure);
		}

		for (Action action : actor.getActions()) {
			inputPorts = new HashSet<String>();
			outputPorts = new HashSet<String>();
			
			for (Port port : action.getInputPattern().getPorts()) {
				inputPorts.add(port.getName());
			}
			
			for (Port port : action.getOutputPattern().getPorts()) {
				outputPorts.add(port.getName());
			}
			
			new InnerVisitor().doSwitch(action.getBody());
			new TagCollector().doSwitch(action.getBody());
		}
		return null;
	}

	private class TagCollector extends AbstractIrVisitor<Void> {

		Set<String> opTags;

		@Override
		public Void caseBlockBasic(BlockBasic block) {
			if (block.hasAttribute("profiling")) {
				@SuppressWarnings("unchecked")
				Map<String, Integer> ops = (Map<String, Integer>) block.getAttribute("profiling").getObjectValue();
				for (String op : ops.keySet()) {
					opTags.add(op);
				}
			}
			return null;
		}

		@Override
		public Void caseBlockIf(BlockIf blockIf) {
			if (blockIf.hasAttribute("profiling")) {
				@SuppressWarnings("unchecked")
				Map<String, Integer> ops = (Map<String, Integer>) blockIf.getAttribute("profiling").getObjectValue();
				for (String op : ops.keySet()) {
					opTags.add(op);
				}
			}
			return super.caseBlockIf(blockIf);
		}

		@Override
		public Void caseBlockWhile(BlockWhile blockWhile) {
			if (blockWhile.hasAttribute("profiling")) {
				@SuppressWarnings("unchecked")
				Map<String, Integer> ops = (Map<String, Integer>) blockWhile.getAttribute("profiling").getObjectValue();
				for (String op : ops.keySet()) {
					opTags.add(op);
				}
			}
			return super.caseBlockWhile(blockWhile);
		}

		@Override
		public Void caseProcedure(Procedure procedure) {
			opTags = new HashSet<String>();

			this.procedure = procedure;
			doSwitch(procedure.getBlocks());
			procedure.setAttribute("OpTags", opTags);
			procedure.setAttribute("ProfInputPorts", inputPorts);
			procedure.setAttribute("ProfOutputPorts", outputPorts);

			return null;
		}

	}

	private class InnerVisitor extends AbstractIrVisitor<Void> {
		public InnerVisitor() {
			// -- Full visit
			super(true);
		}

		private Map<String, Integer> information;

		@Override
		public Void caseBlockBasic(BlockBasic block) {
			information = new HashMap<String, Integer>();
			super.caseBlockBasic(block);
			block.setAttribute("profiling", information);
			return null;
		}

		@Override
		public Void caseBlockIf(BlockIf blockIf) {
			information = new HashMap<String, Integer>();
			doSwitch(blockIf.getCondition());
			addInfo(TurnusModelAdapter.getFrom(blockIf).getName());
			blockIf.setAttribute("profiling", information);
			doSwitch(blockIf.getThenBlocks());
			doSwitch(blockIf.getElseBlocks());
			doSwitch(blockIf.getJoinBlock());
			return null;
		}

		@Override
		public Void caseBlockWhile(BlockWhile blockWhile) {
			information = new HashMap<String, Integer>();
			doSwitch(blockWhile.getCondition());
			addInfo(TurnusModelAdapter.getFrom(blockWhile).getName());
			blockWhile.setAttribute("profiling", information);

			doSwitch(blockWhile.getBlocks());
			doSwitch(blockWhile.getJoinBlock());

			return null;
		}

		@Override
		public Void caseExprBinary(ExprBinary expr) {
			addInfo(TurnusModelAdapter.getFrom(expr).getName());
			return super.caseExprBinary(expr);
		}

		@Override
		public Void caseExprUnary(ExprUnary expr) {
			addInfo(TurnusModelAdapter.getFrom(expr).getName());
			return super.caseExprUnary(expr);
		}

		@Override
		public Void caseInstAssign(InstAssign assign) {
			addInfo(TurnusModelAdapter.getFrom(assign).getName());
			return super.caseInstAssign(assign);
		}

		@Override
		public Void caseInstCall(InstCall call) {
			addInfo(TurnusModelAdapter.getFrom(call).getName());
			super.caseInstCall(call);

			return null;
		}

		@Override
		public Void caseInstLoad(InstLoad load) {
			if (load.getSource().getVariable().isGlobal())
				addInfo("LOAD_" + load.getSource().getVariable().getName());

			addInfo(TurnusModelAdapter.getFrom(load).getName());
			return super.caseInstLoad(load);
		}

		@Override
		public Void caseInstStore(InstStore store) {
			if (store.getTarget().getVariable().isGlobal())
				addInfo("STORE_" + store.getTarget().getVariable().getName());

			addInfo(TurnusModelAdapter.getFrom(store).getName());
			return super.caseInstStore(store);
		}

		private void addInfo(String string) {
			if (information.containsKey(string)) {
				int i = information.get(string);
				i++;
				information.put(string, i);
			} else {
				information.put(string, 1);
			}
		}
	}
}