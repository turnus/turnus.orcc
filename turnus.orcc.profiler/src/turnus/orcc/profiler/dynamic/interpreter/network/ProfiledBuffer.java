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

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;

 
import net.sf.orcc.df.Action;
import net.sf.orcc.df.Connection;
import turnus.analysis.profiler.dynamic.DynamicProfiler;
import turnus.common.TurnusRuntimeException;
import turnus.model.dataflow.Buffer;
import turnus.orcc.profiler.util.TurnusModelAdapter;

/**
 * 
 * @author Simone Casale Brunet
 *
 */
public class ProfiledBuffer {
	private DynamicProfiler profiler;

	private Buffer buffer;

	protected ArrayDeque<Object> queue;

	protected int size;

	public ProfiledBuffer(Connection connection, int size, DynamicProfiler profiler) {
		this.buffer = TurnusModelAdapter.getFrom(connection);
		this.profiler = profiler;
		this.size = size;

		queue = new ArrayDeque<Object>();
	}

	public int getSize() {
		return size;
	}

	public boolean hasSpace(Action action, int tokens) {
		if (size - queue.size() < tokens) {
			profiler.logWriteMiss(buffer, TurnusModelAdapter.getFrom(action));
			return false;
		} else {
			return true;
		}
	}

	public boolean hasTokens(Action action, int tokens) {
		if (queue.size() < tokens) {
			profiler.logReadMiss(buffer, TurnusModelAdapter.getFrom(action));
			return false;
		}
		return true;
	}

	public Object peek(Action action) {
		profiler.logPeek(buffer, TurnusModelAdapter.getFrom(action));
		return queue.peek();
	}

	public Object peek(Action action, int offset) {
		if (offset == 0) {
			return peek(action);
		}

		profiler.logPeek(buffer, TurnusModelAdapter.getFrom(action));
		Object result = null;
		Iterator<Object> it = queue.iterator();
		while (it.hasNext()) {
			result = it.next();
			if (offset-- == 0)
				break;
		}
		return result;
	}

	public Object read() {
		try {
			// TODO send a consistent token value to the profiler
			profiler.logRead(buffer, null);
			return queue.removeFirst();
		} catch (NoSuchElementException e) {
			throw new TurnusRuntimeException("Reading an empty buffer! " + buffer);
		}
	}

	public void setSize(int size) {
		this.size = size;
		queue.clear();
	}

	public void write(Object token) {
		profiler.logWrite(buffer, token);
		queue.offer(token);
	} 
}
