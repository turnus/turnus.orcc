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
package turnus.orcc.profiler.dynamic.interpreter.network;

import static turnus.orcc.profiler.code.transfo.SharedVariablesLinker.SHARED_VARIABLES_LIST;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.eclipse.emf.ecore.EObject;

import net.sf.orcc.OrccRuntimeException;
import net.sf.orcc.df.Action;
import net.sf.orcc.df.Actor;
import net.sf.orcc.df.Pattern;
import net.sf.orcc.df.Port;
import net.sf.orcc.df.Transition;
import net.sf.orcc.df.Unit;
import net.sf.orcc.graph.Edge;
import net.sf.orcc.ir.Arg;
import net.sf.orcc.ir.ArgByVal;
import net.sf.orcc.ir.BlockIf;
import net.sf.orcc.ir.BlockWhile;
import net.sf.orcc.ir.ExprBinary;
import net.sf.orcc.ir.ExprUnary;
import net.sf.orcc.ir.Expression;
import net.sf.orcc.ir.InstAssign;
import net.sf.orcc.ir.InstCall;
import net.sf.orcc.ir.InstLoad;
import net.sf.orcc.ir.InstStore;
import net.sf.orcc.ir.IrFactory;
import net.sf.orcc.ir.Procedure;
import net.sf.orcc.ir.Type;
import net.sf.orcc.ir.TypeList;
import net.sf.orcc.ir.Var;
import net.sf.orcc.ir.util.ValueUtil;
import net.sf.orcc.simulators.slow.ConnectedActorInterpreter;
import net.sf.orcc.util.Attribute;
import net.sf.orcc.util.util.EcoreHelper;
import turnus.analysis.profiler.dynamic.DynamicProfiler;
import turnus.common.io.Logger;
import turnus.orcc.profiler.util.TurnusModelAdapter;

/**
 * 
 * @author Simone Casale Brunet
 *
 */
public class ProfiledActor extends ConnectedActorInterpreter {

	/** the TURNUS profiler */
	private final DynamicProfiler profiler;
	/** executing Orcc action */
	private Action action;
	/** a list of actions outside the fms */
	private final Collection<Action> actionsOutsideFsm;
	/** stack protection for array indexes */
	private final boolean stackProtection;

	public ProfiledActor(Actor actor, DynamicProfiler profiler, boolean stackProtection) {
		super(actor);
		this.profiler = profiler;
		this.stackProtection = stackProtection;

		actionsOutsideFsm = new HashSet<Action>();
		actionsOutsideFsm.addAll(actor.getActionsOutsideFsm());
		actionsOutsideFsm.addAll(actor.getInitializes());
	}

	/**
	 * Increment the operations counter: If
	 */
	@Override
	public Object caseBlockIf(BlockIf node) {
		profiler.logExecute(TurnusModelAdapter.getFrom(node));
		doSwitch(node.getCondition());
		return super.caseBlockIf(node);
	}

	/**
	 * Increment the operations counter: While
	 */
	@Override
	public Object caseBlockWhile(BlockWhile node) {
		int oldBranch = branch;
		branch = 0;
		doSwitch(node.getJoinBlock());

		// Interpret first expression ("while" condition)
		Object condition = exprInterpreter.doSwitch(node.getCondition());

		// while (condition is true) do
		branch = 1;
		while (ValueUtil.isTrue(condition)) {
			profiler.logExecute(TurnusModelAdapter.getFrom(node));
			doSwitch(node.getBlocks());
			doSwitch(node.getJoinBlock());
			// Interpret next value of "while" condition
			condition = exprInterpreter.doSwitch(node.getCondition());
			// save the condition operator
			doSwitch(node.getCondition());
		}

		branch = oldBranch;
		return null;
	}

	/**
	 * Increment the operations counter: Binary Expression
	 */
	@Override
	public Object caseExprBinary(ExprBinary expr) {
		profiler.logExecute(TurnusModelAdapter.getFrom(expr));
		return super.caseExprBinary(expr);
	}

	/**
	 * Increment the operations counter: Unary Expression
	 */
	@Override
	public Object caseExprUnary(ExprUnary expr) {
		profiler.logExecute(TurnusModelAdapter.getFrom(expr));
		return super.caseExprUnary(expr);
	}

	/**
	 * Increment the operations counter: Assign
	 */
	@Override
	public Object caseInstAssign(InstAssign instr) {
		profiler.logExecute(TurnusModelAdapter.getFrom(instr));
		doSwitch(instr.getValue());
		return super.caseInstAssign(instr);
	}

	/**
	 * Increment the operations counter: Call
	 */
	@Override
	public Object caseInstCall(InstCall instr) {
		profiler.logExecute(TurnusModelAdapter.getFrom(instr));
		for (Arg arg : instr.getArguments()) {
			if (arg.isByVal()) {
				Expression expr = ((ArgByVal) arg).getValue();
				doSwitch(expr);
			}
		}
		return super.caseInstCall(instr);
	}

	/**
	 * Increment the operations counter: Load
	 */
	@Override
	public Object caseInstLoad(InstLoad instr) {
		// take the source variable
		Var source = instr.getSource().getVariable();
		boolean isaPortVariable = isInputPortVariable(source);
		boolean isaUnitVariable = isUnitVariable(source);
		if (!isaPortVariable) {
			profiler.logExecute(TurnusModelAdapter.getFrom(instr));
		}
		if (!instr.getIndexes().isEmpty()) {
			for (Expression index : instr.getIndexes()) {
				doSwitch(index);
			}
		} else if (!isaPortVariable && !isaUnitVariable && source.isGlobal()) {
			// if the read variable is a state variable
			// TODO add support for the variable logging
			profiler.logRead(TurnusModelAdapter.getFrom(source), null);
		}

		// take the target variable
		Var target = instr.getTarget().getVariable();
		if (instr.getIndexes().isEmpty()) {
			target.setValue(source.getValue());
		} else {

			Object array = source.getValue();
			Object[] indexes = new Object[instr.getIndexes().size()];
			int i = 0;
			for (Expression index : instr.getIndexes()) {
				indexes[i++] = exprInterpreter.doSwitch(index);
			}
			Type type = ((TypeList) source.getType()).getInnermostType();
			try {
				Object value = ValueUtil.get(type, array, indexes);
				target.setValue(value);
			} catch (IndexOutOfBoundsException e) {

				// try to solve the index out of bound expression
				Expression expr = null;
				if (!stackProtection) { // check if this is required
					if (type.isBool()) {
						expr = IrFactory.eINSTANCE.createExprBool(false);
					} else if (type.isFloat()) {
						expr = IrFactory.eINSTANCE.createExprFloat(0);
					} else if (type.isInt() || type.isUint()) {
						expr = IrFactory.eINSTANCE.createExprInt(0);
					} else if (type.isString()) {
						expr = IrFactory.eINSTANCE.createExprString("");
					}
				}

				// if was not possible to solve the index overflow then
				// throw an exception
				if (expr == null) {
					Logger.error("Array Index Out of Bound at line in %s at line %d",
							actor.getFile().getRawLocation().toOSString(), instr.getLineNumber());
					throw new OrccRuntimeException("Array Index Out of Bound at line " + instr.getLineNumber());
				}

				// evaluate the dummy value
				Object value = ValueUtil.getValue(expr);
				target.setValue(value);

				Logger.debug("Array Index Out of Bound at line in %s at line %d solved with an unsafe load",
						actor.getFile().getRawLocation().toOSString(), instr.getLineNumber());

			}
		}
		return null;
	}

	private boolean isUnitVariable(Var variable) {
		return variable.eContainer() instanceof Unit;
	}

	/**
	 * Increment the operations counter: Store
	 */
	@Override
	public Object caseInstStore(InstStore instr) {
		// take the target variable
		Var variable = instr.getTarget().getVariable();
		boolean isaPortVariable = isOutputPortVariable(variable);
		boolean isaUnitVariable = isUnitVariable(variable);
		if (!isaPortVariable) {
			profiler.logExecute(TurnusModelAdapter.getFrom(instr));
		}
		// continue the instrumentation
		if (!instr.getIndexes().isEmpty()) {
			for (Expression index : instr.getIndexes()) {
				doSwitch(index);
			}
		} else if (!isaPortVariable && !isaUnitVariable && variable.isGlobal()) {
			// if the written variable is a state variable
			// TODO add support for the variable logging
			profiler.logWrite(TurnusModelAdapter.getFrom(variable), null);
		}

		doSwitch(instr.getValue());

		Var target = instr.getTarget().getVariable();
		Object value = exprInterpreter.doSwitch(instr.getValue());
		if (instr.getIndexes().isEmpty()) {
			value = clipValue(target.getType(), value, instr);
			target.setValue(value);

			// set value to all the shared variables (if any...)
			if (target.hasAttribute(SHARED_VARIABLES_LIST)) {
				Collection<Var> svars = target.getValueAsObject(SHARED_VARIABLES_LIST);
				for (Var svar : svars) {
					if (svar != target) {
						svar.setValue(value);
					}
				}
			}
		} else {

			Object array = target.getValue();
			Object[] indexes = new Object[instr.getIndexes().size()];
			int i = 0;
			for (Expression index : instr.getIndexes()) {
				indexes[i++] = exprInterpreter.doSwitch(index);
			}

			Type type = ((TypeList) target.getType()).getInnermostType();
			value = clipValue(type, value, instr);
			try {
				ValueUtil.set(type, array, value, indexes);

				// set value to all the shared variables (if any...)
				if (target.hasAttribute(SHARED_VARIABLES_LIST)) {
					Collection<Var> svars = target.getValueAsObject(SHARED_VARIABLES_LIST);
					for (Var svar : svars) {
						if (svar != target) {
							array = svar.getValue();
							ValueUtil.set(type, array, value, indexes);
						}
					}
				}
			} catch (IndexOutOfBoundsException e) {
				throw new OrccRuntimeException("Array Index Out of Bound at line " + instr.getLineNumber() + "");
			}
		}

		return null;
	}

	@Override
	protected Object callNativeProcedure(Procedure procedure, List<Arg> arguments) {
		int numParams = arguments.size();
		Class<?>[] parameterTypes = new Class<?>[numParams];
		Object[] args = new Object[numParams];
		int i = 0;
		for (Arg arg : arguments) {
			if (arg.isByVal()) {
				Expression expr = ((ArgByVal) arg).getValue();
				args[i] = exprInterpreter.doSwitch(expr);
				parameterTypes[i] = args[i].getClass();
			}

			i++;
		}

		// get packageName and containerName for calling the correct native
		// function
		EObject entity = procedure.eContainer();
		String name = EcoreHelper.getFeature(entity, "name");
		int index = name.lastIndexOf('.');
		if (index != -1) {
			String base = name.substring(0, index);
			if (base.equals("turnus.cal.lib")) {
				name = "co.turnus.analysis.profiler.orcc.dynamic.lib" + name.substring(index);
			} else {
				name = "net.sf.orcc.simulators.runtime." + base + ".impl" + name.substring(index);
			}

		}
		try {
			Class<?> clasz = Class.forName(name);
			return clasz.getMethod(procedure.getName(), parameterTypes).invoke(null, args);
		} catch (Exception e) {
			throw new OrccRuntimeException("Native procedure call Exception for " + procedure.getName(), e);
		}
	}

	/**
	 * Return {@code true} if the action guards are enabled.
	 * 
	 * @param action
	 *            the action
	 * @return {@code true} if the guards are enabled
	 */
	protected boolean checkGuards(Action action) {
		// check peek pattern: allocates peeked variables
		Pattern peekPattern = action.getPeekPattern();

		for (Port port : peekPattern.getPorts()) {
			int numTokens = peekPattern.getNumTokens(port);
			ProfiledBuffer buffer = (ProfiledBuffer) port.getAttribute("fifo").getObjectValue();

			Var peeked = peekPattern.getVariable(port);
			if (peeked != null) {
				peeked.setValue(ValueUtil.createArray((TypeList) peeked.getType()));
			}

			Type type = ((TypeList) peeked.getType()).getInnermostType();
			Object array = peeked.getValue();
			for (int i = 0; i < numTokens; i++) {
				Object value = buffer.peek(action, i);
				ValueUtil.set(type, array, value, i);
			}
		}

		// get the action scheduler
		Procedure scheduler = action.getScheduler();

		// evaluate the action guards
		return ValueUtil.isTrue(doSwitch(scheduler));
	}

	/**
	 * Check the action input pattern. Return {@code true} if the input
	 * conditions are satisfied (i.e. enough tokens in the input FIFOs).
	 * 
	 * @param inputPattern
	 *            the action input pattern
	 * 
	 * @return Return {@code true} if the input conditions are satisfied
	 */
	protected boolean checkInputPattern(Action action) {
		// check input pattern
		Pattern pattern = action.getInputPattern();
		for (Port port : pattern.getPorts()) {
			Attribute att = port.getAttribute("fifo");
			if (att == null) {
				// this port is not connected, consequently this action cannot
				// be scheduled
				return false;
			}
			ProfiledBuffer buffer = (ProfiledBuffer) att.getObjectValue();
			if (!buffer.hasTokens(action, pattern.getNumTokens(port))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check the action output pattern. Return {@code true} if the output
	 * conditions are satisfied (i.e. enough tokens places in the output FIFOs).
	 * 
	 * @param action
	 * @return
	 */
	protected boolean checkOutputPattern(Action action) {
		// check output pattern
		Pattern pattern = action.getOutputPattern();
		if (pattern != null) {
			for (Port port : pattern.getPorts()) {
				Attribute attr = port.getAttribute("fifo");
				if (attr != null) {
					@SuppressWarnings("unchecked")
					List<ProfiledBuffer> buffers = (List<ProfiledBuffer>) attr.getObjectValue();
					for (ProfiledBuffer buffer : buffers) {
						if (!buffer.hasSpace(action, pattern.getNumTokens(port))) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	@Deprecated
	protected boolean checkOutputPattern(Pattern outputPattern) {
		return checkOutputPattern((Action) outputPattern.eContainer());
	}

	@Override
	@SuppressWarnings("unchecked")
	public void execute(Action action) {

		// set the current action as the firing action
		this.action = action;
		profiler.startFiring(TurnusModelAdapter.getFrom(action), !actionsOutsideFsm.contains(action));

		// extract the input and output pattern
		Pattern inputPattern = action.getInputPattern();
		Pattern outputPattern = action.getOutputPattern();

		// allocate patterns variables
		allocatePattern(inputPattern);
		allocatePattern(outputPattern);

		// read input tokens
		for (Port port : inputPattern.getPorts()) {
			ProfiledBuffer buffer = (ProfiledBuffer) port.getAttribute("fifo").getObjectValue();
			Var variable = inputPattern.getVariable(port);
			Type type = ((TypeList) variable.getType()).getInnermostType();
			Object array = variable.getValue();

			for (int i = 0; i < inputPattern.getNumTokens(port); i++) {
				Object value = buffer.read();
				ValueUtil.set(type, array, value, i);
			}

		}

		// Interpret the whole action
		doSwitch(action.getBody());

		// write output tokens
		for (Port port : outputPattern.getPorts()) {
			Attribute attr = port.getAttribute("fifo");
			// look only for connected fifos
			if (attr != null) {
				List<ProfiledBuffer> buffers = (List<ProfiledBuffer>) attr.getObjectValue();
				Var variable = outputPattern.getVariable(port);
				Type type = ((TypeList) variable.getType()).getInnermostType();
				Object array = variable.getValue();
				for (int i = 0; i < outputPattern.getNumTokens(port); i++) {
					Object value = ValueUtil.get(type, array, i);
					for (ProfiledBuffer buffer : buffers) {
						buffer.write(value);
					}
				}
			}
		}

		// end the firing of this action
		profiler.endFiring();
	}

	/**
	 * Get the next schedulable action to be executed for this actor. <b> Update
	 * the FSM state. </b>
	 * 
	 * @return the schedulable action or null
	 */
	@Override
	public Action getNextAction() {
		// Check next schedulable action in respect of the priority order
		for (Action action : actor.getActionsOutsideFsm()) {
			// check if it is schedulable
			if (isSchedulable(action)) {
				if (checkOutputPattern(action)) {
					return action;
				}
			}
		}

		if (actor.hasFsm()) {
			// Then check for next FSM transition
			for (Edge edge : fsmState.getOutgoing()) {
				Transition transition = (Transition) edge;
				Action action = transition.getAction();
				// check if it is schedulable
				if (isSchedulable(action)) {
					// Update FSM state
					if (checkOutputPattern(action)) {
						fsmState = transition.getTarget();
						return action;
					}
					return null;
				}
			}
		}

		return null;
	}

	/*
	 * @Override public void initialize() { profiler.startFiring(null, false);
	 * super.initialize(); }
	 */

	/**
	 * Check if the current variable behaves to an input port
	 * 
	 * @param variable
	 *            the current variable
	 * @return {@code true} if the current variable behaves to an input port
	 */
	private boolean isInputPortVariable(Var variable) {
		if ((!variable.getType().isList()) || (action == null))
			return false;
		return action.getInputPattern().getVarToPortMap().keySet().contains(variable);
	}

	/**
	 * Check if the current variable behaves to an output port
	 * 
	 * @param variable
	 *            the current variable
	 * @return {@code true} if the current variable behaves to an output port
	 */
	private boolean isOutputPortVariable(Var variable) {
		if ((!variable.getType().isList()) || (action == null))
			return false;
		return action.getOutputPattern().getVarToPortMap().keySet().contains(variable);
	}

	/**
	 * Check if the action is schedulable
	 * 
	 * <pre>
	 * (i.e. there are enough input tokens and if the guard is enabled)
	 * </pre>
	 */
	@Override
	protected boolean isSchedulable(Action action) {
		return checkInputPattern(action) && checkGuards(action);
	}
}