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
package turnus.orcc.profiler.util;

import static turnus.orcc.profiler.util.TurnusModelInjector.TURNUS_MODEL;

import java.util.HashMap;
import java.util.Map;

import net.sf.orcc.ir.BlockIf;
import net.sf.orcc.ir.BlockWhile;
import net.sf.orcc.ir.ExprBinary;
import net.sf.orcc.ir.ExprUnary;
import net.sf.orcc.ir.InstAssign;
import net.sf.orcc.ir.InstCall;
import net.sf.orcc.ir.InstLoad;
import net.sf.orcc.ir.InstStore;
import net.sf.orcc.ir.OpBinary;
import net.sf.orcc.ir.OpUnary;
import turnus.model.common.EOperator;
import turnus.model.dataflow.Action;
import turnus.model.dataflow.Actor;
import turnus.model.dataflow.Buffer;
import turnus.model.dataflow.DataflowFactory;
import turnus.model.dataflow.Network;
import turnus.model.dataflow.Port;
import turnus.model.dataflow.Type;
import turnus.model.dataflow.TypeBoolean;
import turnus.model.dataflow.TypeDouble;
import turnus.model.dataflow.TypeInt;
import turnus.model.dataflow.TypeList;
import turnus.model.dataflow.TypeString;
import turnus.model.dataflow.TypeUint;
import turnus.model.dataflow.TypeUndefined;
import turnus.model.dataflow.Variable;

/**
 * 
 * @author Simone Casale Brunet
 *
 */
public class TurnusModelAdapter {

	private static final Map<OpBinary, EOperator> opBinaryMap = new HashMap<OpBinary, EOperator>();
	private static final Map<OpUnary, EOperator> opUnaryMap = new HashMap<OpUnary, EOperator>();

	static {
		// OpBinary cast map
		opBinaryMap.put(OpBinary.BITAND, EOperator.BINARY_BIT_AND);
		opBinaryMap.put(OpBinary.BITOR, EOperator.BINARY_BIT_OR);
		opBinaryMap.put(OpBinary.BITXOR, EOperator.BINARY_BIT_XOR);
		opBinaryMap.put(OpBinary.DIV, EOperator.BINARY_DIV);
		opBinaryMap.put(OpBinary.DIV_INT, EOperator.BINARY_DIV_INT);
		opBinaryMap.put(OpBinary.EQ, EOperator.BINARY_EQ);
		opBinaryMap.put(OpBinary.EXP, EOperator.BINARY_EXP);
		opBinaryMap.put(OpBinary.GE, EOperator.BINARY_GE);
		opBinaryMap.put(OpBinary.GT, EOperator.BINARY_GT);
		opBinaryMap.put(OpBinary.LE, EOperator.BINARY_LE);
		opBinaryMap.put(OpBinary.LOGIC_AND, EOperator.BINARY_LOGIC_AND);
		opBinaryMap.put(OpBinary.LOGIC_OR, EOperator.BINARY_LOGIC_OR);
		opBinaryMap.put(OpBinary.LT, EOperator.BINARY_LT);
		opBinaryMap.put(OpBinary.MINUS, EOperator.BINARY_MINUS);
		opBinaryMap.put(OpBinary.MOD, EOperator.BINARY_MOD);
		opBinaryMap.put(OpBinary.NE, EOperator.BINARY_NE);
		opBinaryMap.put(OpBinary.PLUS, EOperator.BINARY_PLUS);
		opBinaryMap.put(OpBinary.SHIFT_LEFT, EOperator.BINARY_SHIFT_LEFT);
		opBinaryMap.put(OpBinary.SHIFT_RIGHT, EOperator.BINARY_SHIFT_RIGHT);
		opBinaryMap.put(OpBinary.TIMES, EOperator.BINARY_TIMES);

		// OpUnary cast map
		opUnaryMap.put(OpUnary.BITNOT, EOperator.UNARY_BIT_NOT);
		opUnaryMap.put(OpUnary.LOGIC_NOT, EOperator.UNARY_LOGIC_NOT);
		opUnaryMap.put(OpUnary.MINUS, EOperator.UNARY_MINUS);
		opUnaryMap.put(OpUnary.NUM_ELTS, EOperator.UNARY_NUM_ELTS);
	}

	public static Type createFrom(net.sf.orcc.ir.Type orccType) {
		if (orccType instanceof net.sf.orcc.ir.TypeInt) {
			return createFrom((net.sf.orcc.ir.TypeInt) orccType);
		} else if (orccType instanceof net.sf.orcc.ir.TypeUint) {
			return createFrom((net.sf.orcc.ir.TypeUint) orccType);
		} else if (orccType instanceof net.sf.orcc.ir.TypeFloat) {
			return createFrom((net.sf.orcc.ir.TypeFloat) orccType);
		} else if (orccType instanceof net.sf.orcc.ir.TypeList) {
			return createFrom((net.sf.orcc.ir.TypeList) orccType);
		} else if (orccType instanceof net.sf.orcc.ir.TypeBool) {
			return createFrom((net.sf.orcc.ir.TypeBool) orccType);
		} else if (orccType instanceof net.sf.orcc.ir.TypeString) {
			return createFrom((net.sf.orcc.ir.TypeString) orccType);
		} else {
			// if the type cannot be handled return a generic complex type
			TypeUndefined type = DataflowFactory.eINSTANCE.createTypeUndefined();
			type.setSize(orccType.getSizeInBits());
			return type;
		}
	}

	public static TypeBoolean createFrom(net.sf.orcc.ir.TypeBool orccType) {
		return DataflowFactory.eINSTANCE.createTypeBoolean();
	}

	public static TypeDouble createFrom(net.sf.orcc.ir.TypeFloat orccType) {
		TypeDouble type = DataflowFactory.eINSTANCE.createTypeDouble();
		type.setSize(orccType.getSize());
		return type;
	}

	public static TypeInt createFrom(net.sf.orcc.ir.TypeInt orccType) {
		TypeInt type = DataflowFactory.eINSTANCE.createTypeInt();
		type.setSize(orccType.getSize());
		return type;
	}

	public static TypeList createFrom(net.sf.orcc.ir.TypeList orccType) {
		TypeList type = DataflowFactory.eINSTANCE.createTypeList();
		type.setElements(orccType.getSize());
		type.setListType(createFrom(orccType.getInnermostType()));
		return type;
	}

	public static TypeString createFrom(net.sf.orcc.ir.TypeString orccType) {
		TypeString type = DataflowFactory.eINSTANCE.createTypeString();
		type.setSize(orccType.getSize());
		return type;
	}

	public static TypeUint createFrom(net.sf.orcc.ir.TypeUint orccType) {
		TypeUint type = DataflowFactory.eINSTANCE.createTypeUint();
		type.setSize(orccType.getSize());
		return type;
	}

	public static EOperator getFrom(BlockIf node) {
		return EOperator.FLOWCONTROL_IF;
	}

	public static EOperator getFrom(BlockWhile node) {
		return EOperator.FLOWCONTROL_WHILE;
	}

	public static EOperator getFrom(ExprBinary expr) {
		return opBinaryMap.get(expr.getOp());
	}

	public static EOperator getFrom(ExprUnary expr) {
		return opUnaryMap.get(expr.getOp());
	}

	public static EOperator getFrom(InstAssign instr) {
		return EOperator.DATAHANDLING_ASSIGN;
	}

	public static EOperator getFrom(InstCall instr) {
		return EOperator.DATAHANDLING_CALL;
	}

	public static EOperator getFrom(InstLoad instr) {
		return instr.getSource().getVariable().getType().isList() ? EOperator.DATAHANDLING_LIST_LOAD
				: EOperator.DATAHANDLING_LOAD;
	}

	public static EOperator getFrom(InstStore instr) {
		return instr.getTarget().getVariable().getType().isList() ? EOperator.DATAHANDLING_LIST_STORE
				: EOperator.DATAHANDLING_STORE;
	}

	public static Action getFrom(net.sf.orcc.df.Action action) {
		return (Action) action.getAttribute(TURNUS_MODEL).getObjectValue();
	}

	public static Actor getFrom(net.sf.orcc.df.Actor actor) {
		return (Actor) actor.getAttribute(TURNUS_MODEL).getObjectValue();
	}

	public static Buffer getFrom(net.sf.orcc.df.Connection fifo) {
		return (Buffer) fifo.getAttribute(TURNUS_MODEL).getObjectValue();
	}

	public static Network getFrom(net.sf.orcc.df.Network network) {
		return (Network) network.getAttribute(TURNUS_MODEL).getObjectValue();
	}

	public static Port getFrom(net.sf.orcc.df.Port port) {
		return (Port) port.getAttribute(TURNUS_MODEL).getObjectValue();
	}

	public static Variable getFrom(net.sf.orcc.ir.Var var) {
		return (Variable) var.getAttribute(TURNUS_MODEL).getObjectValue();
	}

}
