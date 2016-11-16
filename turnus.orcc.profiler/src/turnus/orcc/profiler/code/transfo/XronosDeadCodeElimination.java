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

import java.util.List;

import net.sf.orcc.ir.Block;
import net.sf.orcc.ir.BlockIf;
import net.sf.orcc.ir.Expression;
import net.sf.orcc.ir.util.AbstractIrVisitor;
import net.sf.orcc.ir.util.IrUtil;
import net.sf.orcc.ir.util.ValueUtil;
import net.sf.orcc.util.OrccLogger;
import net.sf.orcc.util.util.EcoreHelper;

/**
 * 
 * @author Endri Bezati
 *
 */
public class XronosDeadCodeElimination extends AbstractIrVisitor<Void> {

	Boolean debug;

	public XronosDeadCodeElimination() {
		this(false);
	}

	public XronosDeadCodeElimination(Boolean debug) {
		this.debug = debug;
	}

	@Override
	public Void caseBlockIf(BlockIf blockIf) {
		Expression condition = blockIf.getCondition();
		XronosExprEvaluator exprEvaluator = new XronosExprEvaluator();
		Object value = exprEvaluator.doSwitch(condition);
		if (value != null) {
			if (ValueUtil.isBool(value)) {
				Boolean val = (Boolean) value;
				if (val) {
					// 1. Get parent Blocks
					List<Block> parentBlocks = EcoreHelper
							.getContainingList(blockIf);

					// 2. Get then Blocks
					List<Block> thenBlocks = blockIf.getThenBlocks();

					// 3. Add the blocks to the parents one
					parentBlocks.addAll(indexBlock, thenBlocks);

					// 4. Remove all blocks from the else
					parentBlocks.remove(blockIf);
					IrUtil.delete(blockIf);
					if (debug) {
						OrccLogger.warnln("Xronos Tranformation: BlockIf line: "
								+ blockIf.getLineNumber()
								+ " removed, all then blocks copied");
					}

				} else {
					// 1. Get parent Blocks
					List<Block> parentBlocks = EcoreHelper
							.getContainingList(blockIf);
					if (!blockIf.getElseBlocks().isEmpty()) {

						// 2. Get then Blocks
						List<Block> elseBlocks = blockIf.getElseBlocks();

						// 3. Add the blocks to the parents one
						parentBlocks.addAll(indexBlock, elseBlocks);
						if (debug) {
							OrccLogger.warnln("Xronos Tranformation: BlockIf line: "
									+ blockIf.getLineNumber()
									+ " removed, all else blocks copied");
						}
					}
					// 5. Remove all blocks from the else
					parentBlocks.remove(blockIf);
					IrUtil.delete(blockIf);
					if (debug) {
						OrccLogger.warnln("Xronos Tranformation: BlockIf line: "
								+ blockIf.getLineNumber() + " removed");
					}
				}
			}
		}

		return null;
	}
}