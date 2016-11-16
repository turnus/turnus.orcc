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
package turnus.orcc.profiler.code.ast;

import java.util.List;

import org.eclipse.core.resources.IFile;

import net.sf.orcc.cal.cal.AstAction;
import net.sf.orcc.cal.cal.AstActor;
import net.sf.orcc.cal.cal.AstExpression;
import net.sf.orcc.cal.cal.AstPort;
import net.sf.orcc.cal.cal.AstProcedure;
import net.sf.orcc.cal.cal.AstTag;
import net.sf.orcc.cal.cal.AstTransition;
import net.sf.orcc.cal.cal.ExpressionBinary;
import net.sf.orcc.cal.cal.ExpressionBoolean;
import net.sf.orcc.cal.cal.ExpressionCall;
import net.sf.orcc.cal.cal.ExpressionElsif;
import net.sf.orcc.cal.cal.ExpressionFloat;
import net.sf.orcc.cal.cal.ExpressionIf;
import net.sf.orcc.cal.cal.ExpressionIndex;
import net.sf.orcc.cal.cal.ExpressionInteger;
import net.sf.orcc.cal.cal.ExpressionList;
import net.sf.orcc.cal.cal.ExpressionString;
import net.sf.orcc.cal.cal.ExpressionUnary;
import net.sf.orcc.cal.cal.ExpressionVariable;
import net.sf.orcc.cal.cal.Function;
import net.sf.orcc.cal.cal.Generator;
import net.sf.orcc.cal.cal.Guard;
import net.sf.orcc.cal.cal.Inequality;
import net.sf.orcc.cal.cal.InputPattern;
import net.sf.orcc.cal.cal.OutputPattern;
import net.sf.orcc.cal.cal.Priority;
import net.sf.orcc.cal.cal.ScheduleFsm;
import net.sf.orcc.cal.cal.Statement;
import net.sf.orcc.cal.cal.StatementAssign;
import net.sf.orcc.cal.cal.StatementCall;
import net.sf.orcc.cal.cal.StatementElsif;
import net.sf.orcc.cal.cal.StatementForeach;
import net.sf.orcc.cal.cal.StatementIf;
import net.sf.orcc.cal.cal.StatementWhile;
import net.sf.orcc.cal.cal.Variable;
import net.sf.orcc.cal.cal.util.CalSwitch;
import net.sf.orcc.util.OrccUtil;
import turnus.analysis.profiler.code.StaticProfiler;
import turnus.analysis.profiler.code.util.SourceCodeUtil;
import turnus.common.TurnusException;

/**
 * 
 * @author Simone Casale Brunet
 *
 */
public class AstAnalyser {

	private class AstActorClassVisitor extends CalSwitch<Void> {

		private int untaggedCount;

		@Override
		public Void caseAstActor(AstActor astActor) {

			profiler.addOperator("actor");

			for (AstPort input : astActor.getInputs()) {
				// TODO add type
				profiler.addOperand(input.getName());
			}

			profiler.addOperator("==>");

			for (AstPort out : astActor.getOutputs()) {
				// TODO add type
				profiler.addOperand(out.getName());
			}

			for (Variable var : astActor.getStateVariables()) {
				// TODO add type
				profiler.addOperand(var.getName());

				AstExpression value = var.getValue();
				if (value != null) {
					doSwitch(value);
				}
			}

			ScheduleFsm fsm = astActor.getScheduleFsm();
			if (fsm != null) {
				profiler.addOperator("schedule");
				profiler.addOperator("fsm");
				profiler.addOperand(fsm.getInitialState().getName());
				for (AstTransition transition : fsm.getContents().getTransitions()) {
					profiler.addOperand(transition.getSource().getName());
					profiler.addOperator("-->");
					profiler.addOperand(transition.getTarget().getName());
				}
				profiler.addOperator("end");
			}

			List<Priority> priorityList = astActor.getPriorities();
			if (!priorityList.isEmpty()) {
				profiler.addOperator("priority");
				for (Priority priority : priorityList) {
					List<Inequality> inequalities = priority.getInequalities();
					for (Inequality inequality : inequalities) {
						profiler.addOperator(">");
						for (AstTag tag : inequality.getTags()) {
							for (String id : tag.getIdentifiers()) {
								profiler.addOperand(id);
							}
						}
					}
				}
				profiler.addOperator("end");
			}

			for (AstAction astAction : ast.getActions()) {
				String name = getActionName(astAction);
				profiler.enterAction(name);
				doSwitch(astAction);
				profiler.exitAction();
			}

			for (AstAction astAction : ast.getInitializes()) {
				profiler.enterAction("initialize");
				doSwitch(astAction);
				profiler.exitAction();
			}

			for (AstProcedure astProc : ast.getProcedures()) {
				String name = getProcedureName(astProc);
				profiler.enterProcedure(name);
				doSwitch(astProc);
				profiler.exitProcedure();
			}

			for (Function astFunc : ast.getFunctions()) {
				doSwitch(astFunc);
			}

			profiler.addOperator("end");
			return null;

		}

		public Void caseAstAction(AstAction astAction) {
			profiler.addOperator("action");
			profiler.addOperator("do");

			// check action tags
			AstTag tag = astAction.getTag();
			if (tag != null) {
				for (String id : tag.getIdentifiers()) {
					profiler.addOperand(id);
				}
			}

			// check inputs
			for (InputPattern input : astAction.getInputs()) {

				profiler.addOperand(input.getPort().getName());
				profiler.addOperator("[");
				profiler.addOperator("]");

				for (Variable var : input.getTokens()) {
					profiler.addOperand(var.getName());
				}

				AstExpression repeat = input.getRepeat();
				if (repeat != null) {
					profiler.addOperator("repeat");
					doSwitch(repeat);
				}
			}

			profiler.addOperator("==>");

			for (OutputPattern out : astAction.getOutputs()) {
				profiler.addOperand(out.getPort().getName());
				profiler.addOperator("[");
				profiler.addOperator("]");

				for (AstExpression value : out.getValues()) {
					doSwitch(value);
				}

				AstExpression repeat = out.getRepeat();
				if (repeat != null) {
					profiler.addOperator("repeat");
					doSwitch(repeat);
				}
			}

			// check guards
			Guard guard = astAction.getGuard();
			if (guard != null) {
				profiler.addOperator("guard");
				for (AstExpression exp : guard.getExpressions()) {
					doSwitch(exp);
				}
			}

			// check variables
			List<Variable> vars = astAction.getVariables();
			if (!vars.isEmpty()) {
				profiler.addOperator("var");
				for (Variable var : vars) {
					profiler.addOperand(var.getName());
					AstExpression exp = var.getValue();
					if (exp != null) {
						profiler.addOperator(":=");
						doSwitch(exp);
					}
				}
			}

			// check statements
			for (Statement statmenet : astAction.getStatements()) {
				doSwitch(statmenet);
			}

			profiler.addOperator("end");

			return null;
		}

		@Override
		public Void caseAstProcedure(AstProcedure proc) {
			profiler.addOperator("procedure");

			profiler.addOperand(proc.getName());

			profiler.addOperator("begin");

			profiler.addOperator("(");
			for (Variable par : proc.getParameters()) {
				profiler.addOperator(par.getName());
			}
			profiler.addOperator(")");

			List<Variable> vars = proc.getVariables();
			if (!vars.isEmpty()) {
				profiler.addOperator("var");
				for (Variable var : vars) {
					profiler.addOperator(var.getName());
				}
			}

			for (Statement stm : proc.getStatements()) {
				doSwitch(stm);
			}

			profiler.addOperator("end");

			return null;
		}

		@Override
		public Void caseExpressionBinary(ExpressionBinary exp) {
			profiler.addOperator(exp.getOperator());

			doSwitch(exp.getLeft());
			doSwitch(exp.getRight());

			return null;
		}

		@Override
		public Void caseExpressionBoolean(ExpressionBoolean exp) {
			String operand = Boolean.toString(exp.isValue());
			profiler.addOperand(operand);

			return null;
		}

		@Override
		public Void caseExpressionCall(ExpressionCall exp) {
			profiler.addOperator(exp.getFunction().getName());

			profiler.addOperator("(");
			for (AstExpression param : exp.getParameters()) {
				doSwitch(param);
			}
			profiler.addOperator(")");

			return null;
		}

		@Override
		public Void caseExpressionElsif(ExpressionElsif exp) {
			profiler.addOperator("elsif");
			doSwitch(exp.getCondition());

			profiler.addOperator("then");
			doSwitch(exp.getThen());

			return null;
		}

		@Override
		public Void caseExpressionFloat(ExpressionFloat exp) {
			String operand = Float.toString(exp.getValue());
			profiler.addOperand(operand);

			return null;
		}

		@Override
		public Void caseExpressionIf(ExpressionIf exp) {
			profiler.addOperator("if");
			doSwitch(exp.getCondition());

			profiler.addOperator("then");
			doSwitch(exp.getThen());

			for (ExpressionElsif subExp : exp.getElsifs()) {
				doSwitch(subExp);
			}

			profiler.addOperator("else");
			doSwitch(exp.getElse());

			profiler.addOperator("end");

			return null;
		}

		@Override
		public Void caseExpressionIndex(ExpressionIndex exp) {
			profiler.addOperand(exp.getSource().getVariable().getName());

			profiler.addOperator("[");
			for (AstExpression index : exp.getIndexes()) {
				doSwitch(index);
			}
			profiler.addOperator("]");

			return null;
		}

		@Override
		public Void caseExpressionInteger(ExpressionInteger exp) {
			profiler.addOperand(exp.getValue().toString());

			return null;
		}

		@Override
		public Void caseExpressionList(ExpressionList exp) {

			for (AstExpression subExp : exp.getExpressions()) {
				doSwitch(subExp);
			}

			return null;
		}

		@Override
		public Void caseExpressionString(ExpressionString exp) {
			profiler.addOperand(exp.getValue());

			return null;
		}

		@Override
		public Void caseExpressionUnary(ExpressionUnary exp) {
			profiler.addOperator(exp.getUnaryOperator());

			doSwitch(exp.getExpression());
			return null;
		}

		@Override
		public Void caseExpressionVariable(ExpressionVariable exp) {
			profiler.addOperand(exp.getValue().getVariable().getName());

			return null;
		}

		@Override
		public Void caseGenerator(Generator gen) {
			profiler.addOperand(gen.getVariable().getName());

			doSwitch(gen.getHigher());
			doSwitch(gen.getLower());

			return null;
		}

		@Override
		public Void caseStatementAssign(StatementAssign stm) {
			profiler.addOperand(stm.getTarget().getVariable().getName());

			List<AstExpression> indexes = stm.getIndexes();
			if (!indexes.isEmpty()) {
				profiler.addOperator("[");
				for (AstExpression exp : indexes) {
					doSwitch(exp);
				}
				profiler.addOperator("]");
			}

			profiler.addOperator(":=");

			doSwitch(stm.getValue());
			return null;
		}

		@Override
		public Void caseStatementCall(StatementCall stm) {
			profiler.addOperand(stm.getProcedure().getName());

			for (AstExpression arg : stm.getArguments()) {
				doSwitch(arg);
			}

			return null;
		}

		@Override
		public Void caseStatementElsif(StatementElsif stm) {

			profiler.addOperator("elsif");
			doSwitch(stm.getCondition());

			profiler.addOperator("then");
			for (Statement subStm : stm.getThen()) {
				doSwitch(subStm);
			}

			return null;
		}

		@Override
		public Void caseStatementForeach(StatementForeach stm) {
			profiler.addOperator("foreach");
			profiler.addOperand(stm.getVariable().getName());
			profiler.addOperator("in");
			doSwitch(stm.getHigher());
			profiler.addOperator("..");
			doSwitch(stm.getLower());

			profiler.addOperator("do");
			for (Statement subStm : stm.getStatements()) {
				doSwitch(subStm);
			}

			profiler.addOperator("end");
			return null;
		}

		@Override
		public Void caseStatementIf(StatementIf stm) {
			profiler.addOperator("if");
			doSwitch(stm.getCondition());

			profiler.addOperator("then");
			for (Statement subStm : stm.getThen()) {
				doSwitch(subStm);
			}

			for (StatementElsif subStm : stm.getElsifs()) {
				doSwitch(subStm);
			}

			List<Statement> stmsElse = stm.getElse();
			if (!stmsElse.isEmpty()) {
				profiler.addOperator("else");

				for (Statement subStm : stmsElse) {
					doSwitch(subStm);
				}
			}

			profiler.addOperator("end");

			return null;
		}

		@Override
		public Void caseStatementWhile(StatementWhile stm) {
			profiler.addOperator("while");
			doSwitch(stm.getCondition());

			profiler.addOperator("do");
			for (Statement subStm : stm.getStatements()) {
				doSwitch(subStm);
			}

			profiler.addOperator("end");

			return null;
		}

		private String getProcedureName(AstProcedure astProc) {
			return astProc.getName();
		}

		private String getActionName(AstAction astAction) {
			AstTag astTag = astAction.getTag();
			String name;
			if (astTag == null) {
				name = "untagged_" + untaggedCount++;
			} else {
				name = OrccUtil.toString(astAction.getTag().getIdentifiers(), "_");
			}
			return name;
		}

	}

	private final StaticProfiler profiler;
	private final IFile calFile;
	private final AstActor ast;
	private AstActorClassVisitor analyser;

	public AstAnalyser(AstActor ast, IFile calFile, StaticProfiler profiler) {
		this.ast = ast;
		this.calFile = calFile;
		this.profiler = profiler;

	}

	public void run() throws TurnusException {
		try {

			String name = calFile.getProjectRelativePath().removeFirstSegments(1).removeFileExtension().toString()
					.replace("/", ".");
			int nol = SourceCodeUtil.numberOfLines(calFile);
			profiler.enterActor(name, nol);

			// create the analyzer and run it
			analyser = new AstActorClassVisitor();
			analyser.doSwitch(ast);

			profiler.exitActor();
		} catch (Exception e) {
			throw new TurnusException("AST analysis failed for the CAL file \"" + calFile + "\"", e);
		}
	}
}
