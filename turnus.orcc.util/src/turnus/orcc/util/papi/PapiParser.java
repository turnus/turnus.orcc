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
package turnus.orcc.util.papi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math.stat.descriptive.SummaryStatistics;

import turnus.common.TurnusException;
import turnus.model.dataflow.Network;
import turnus.model.mapping.NetworkWeight;
import turnus.model.mapping.data.ClockCycles;

/**
 * 
 * @author Malgorzata Michalska
 *
 */
public class PapiParser {
	
	String papiDir;
	Map<String, Map<String, SummaryStatistics>> statistics;
	
	public PapiParser(String pd) {
		this.papiDir = pd;
	}
	
	public NetworkWeight getNetworkWeight(Network network) throws TurnusException {
		NetworkWeight weights = new NetworkWeight(network);
		parse();
		for (String actor : statistics.keySet()) {
			for (String action : statistics.get(actor).keySet()) {
				double min = Double.isNaN(statistics.get(actor).get(action).getMin()) ? 0 : statistics.get(actor).get(action).getMin();
				double mean = Double.isNaN(statistics.get(actor).get(action).getMean()) ? 0 : statistics.get(actor).get(action).getMean();
				double max = Double.isNaN(statistics.get(actor).get(action).getMax()) ? 0 : statistics.get(actor).get(action).getMax();
				
				// check consistency
				if (min > mean || max < mean || max < min) {
					throw new TurnusException("Action weight not consistent for actor: " + actor + ", action: " + action);
				}
				
				ClockCycles w = weights.getWeight(actor, action);
				w.setMinClockCycles(min);
				w.setMeanClockCycles(mean);
				w.setMaxClockCycles(max);
			}
		}
		
		return weights;
	}
	
	private void parse() {
		statistics = new HashMap<String, Map<String, SummaryStatistics>>();
		File folder = new File(papiDir);
		File[] listOfFiles = folder.listFiles();
		for (File f : listOfFiles) {
			try {
				FileInputStream iStream = new FileInputStream(f);
				BufferedReader iBuffer = new BufferedReader(new InputStreamReader(iStream));
				String str;
				int id = 0;
				while ((str = iBuffer.readLine()) != null) {
					if (id != 0) {
						int fIdx = str.indexOf(',', 0);
						String stringActor = str.substring(0, fIdx);
						if (!statistics.containsKey(stringActor)) {
							statistics.put(stringActor, new HashMap<String, SummaryStatistics>());
						}
						int sIdx = str.indexOf(',', fIdx + 1);
						String stringAction = str.substring(fIdx + 1, sIdx);
						if (!statistics.get(stringActor).containsKey(stringAction)) {
							statistics.get(stringActor).put(stringAction, new SummaryStatistics());
						}
						int nextIdx = str.indexOf(',', sIdx + 1);
						nextIdx = str.indexOf(",", nextIdx + 1);
						String stringWeight = str.substring(nextIdx + 1, str.length());
						statistics.get(stringActor).get(stringAction).addValue(Double.parseDouble(stringWeight));
					}
					id++;
				}
				iBuffer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
