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

import java.util.Map;

import net.sf.orcc.df.Connection;
import net.sf.orcc.df.Network;
import net.sf.orcc.df.util.DfVisitor;
import turnus.common.io.Logger;

/**
 * 
 * @author Simone Casale Brunet
 *
 */
public class OrccBufferSizer extends DfVisitor<Void> {
	public static String getKey(String srcActor, String srcPort, String tgtActor, String tgtPort) {
		return srcActor + "," + srcPort + "," + tgtActor + "," + tgtPort;
	}

	public static String[] split(String fifoKey) {
		return fifoKey.split(",");
	}

	public static String getKey(Connection fifo) {
		String srcActor = ((net.sf.orcc.df.Actor) fifo.getSource()).getSimpleName();
		String tgtActor = ((net.sf.orcc.df.Actor) fifo.getTarget()).getSimpleName();
		String srcPort = fifo.getSourcePort().getName();
		String tgtPort = fifo.getTargetPort().getName();
		return getKey(srcActor, srcPort, tgtActor, tgtPort);
	}

	private final Map<String, String> customBufferSizeMap;

	public OrccBufferSizer(Map<String, String> customBufferSizeMap) {
			this.customBufferSizeMap = customBufferSizeMap;
		}

	@Override
	public Void caseNetwork(Network network) {
		if (customBufferSizeMap != null && !customBufferSizeMap.isEmpty()) {
			for (Connection connection : network.getConnections()) {
				doSwitch(connection);
			}
		}

		return null;
	}

	@Override
	public Void caseConnection(Connection connection) {
		String key = OrccBufferSizer.getKey(connection);
		if (customBufferSizeMap.containsKey(key)) {
			int size = Integer.parseInt(customBufferSizeMap.get(key));
			connection.setSize(size);
			Logger.debug("Custon buffer size: %s > %d", key, size);
		}
		return null;
	}

}
