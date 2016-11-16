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
package turnus.orcc.profiler.dynamic.numa.weightsgeneration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import turnus.common.io.Logger;
import turnus.model.analysis.profiler.AccessData;
import turnus.model.analysis.profiler.ActionMemoryProfilingData;
import turnus.model.analysis.profiler.BufferAccessData;
import turnus.model.analysis.profiler.MemoryAccessData;
import turnus.model.analysis.profiler.MemoryProfilingReport;
import turnus.model.mapping.data.MemoryAccess;
import turnus.model.mapping.io.util.BufferIdentifier;

/**
 * 
 * @author Manuel Selva
 *
 */
public class OrccNumaWeightsGenerator {
	
	private MemoryProfilingReport report;
	private String mode;
	
	private Map<BufferIdentifier, LatencyCollector[]> hitLatencies;
	private Map<BufferIdentifier, LatencyCollector[]> missLatencies;
	private Map<BufferIdentifier, List<MemoryAccess>> memoryData;
	
	public OrccNumaWeightsGenerator(MemoryProfilingReport report, String mode) {
		this.report = report;
		this.mode = mode;
		this.hitLatencies = new HashMap<BufferIdentifier, LatencyCollector[]>();
		this.missLatencies = new HashMap<BufferIdentifier, LatencyCollector[]>();
		this.memoryData = new HashMap<BufferIdentifier, List<MemoryAccess>>();
	}
	
	public Map<BufferIdentifier, List<MemoryAccess>> launchGeneration() {
		collectLatencies();
		
		for (Entry<BufferIdentifier, LatencyCollector[]> entry : hitLatencies.entrySet()) {
			double totalAccesses = 0;
			for (int i = 0; i < 6; i++) {
				totalAccesses += entry.getValue()[i].getAccesses();
				totalAccesses += missLatencies.get(entry.getKey())[i].getAccesses();
			}
			
			memoryData.put(entry.getKey(), new ArrayList<MemoryAccess>());
			for (int i = 0; i < 6; i++) {
				// save memory accesses for hits
				String level = entry.getValue()[i].getLevel();
				String type = entry.getValue()[i].getType();
				double latency = entry.getValue()[i].getAccesses() > 0 ? entry.getValue()[i].getTotal()/entry.getValue()[i].getAccesses() : 0;
				double percentage = totalAccesses > 0 ? entry.getValue()[i].getAccesses()/totalAccesses : 0;
				memoryData.get(entry.getKey()).add(new MemoryAccess(level, mode, type, percentage, latency));
				
				// save memory accesses for misses
				level = missLatencies.get(entry.getKey())[i].getLevel();
				type = missLatencies.get(entry.getKey())[i].getType();
				latency = missLatencies.get(entry.getKey())[i].getAccesses() > 0 ? missLatencies.get(entry.getKey())[i].getTotal()/missLatencies.get(entry.getKey())[i].getAccesses() : 0;
				percentage = totalAccesses > 0 ? missLatencies.get(entry.getKey())[i].getAccesses()/totalAccesses : 0;
				memoryData.get(entry.getKey()).add(new MemoryAccess(level, mode, type, percentage, latency));
			}
		}
		
		Logger.info("Communication weights generation done.");
		
		return memoryData;
	}
	
	private void collectLatencies() {
		for (ActionMemoryProfilingData actionData : report.getActionsData()) {
			List<MemoryAccessData> accessDataList = null;
			if (mode.equals("read"))
				accessDataList = actionData.getReads();
			else
				accessDataList = actionData.getWrites();
				
			for (MemoryAccessData accessData : accessDataList) {
				if (accessData instanceof BufferAccessData) {
					String sourceActor = ((BufferAccessData) accessData).getSourceActor();
					String sourcePort = ((BufferAccessData) accessData).getSourcePort();
					String targetActor = ((BufferAccessData) accessData).getTargetActor();
					String targetPort = ((BufferAccessData) accessData).getTargetPort();
					BufferIdentifier bufferIdentifier = addNewBufferIdentifier(sourceActor, sourcePort, targetActor, targetPort);
					for (Entry<String, AccessData> entry : accessData.getAccessesData().entrySet()) {
						String level = entry.getKey();
						AccessData data = entry.getValue();
						// retrieve hit data
						for (int i = 0; i < 6; i++) {
							if (level.contains(hitLatencies.get(bufferIdentifier)[i].getLevel())
									&& level.contains(hitLatencies.get(bufferIdentifier)[i].getType())) {
								double currentTotal = hitLatencies.get(bufferIdentifier)[i].getTotal();
								double currentAccesses = hitLatencies.get(bufferIdentifier)[i].getAccesses();
								hitLatencies.get(bufferIdentifier)[i].setTotal(currentTotal + data.getTotal());
								hitLatencies.get(bufferIdentifier)[i].setAccesses(currentAccesses + data.getAccesses());
							}
						}
						//	retrieve miss data
						for (int i = 0; i < 6; i++) {
							if (level.contains(missLatencies.get(bufferIdentifier)[i].getLevel())
									&& level.contains(missLatencies.get(bufferIdentifier)[i].getType())) {
								double currentTotal = missLatencies.get(bufferIdentifier)[i].getTotal();
								double currentAccesses = missLatencies.get(bufferIdentifier)[i].getAccesses();
								missLatencies.get(bufferIdentifier)[i].setTotal(currentTotal + data.getTotal());
								missLatencies.get(bufferIdentifier)[i].setAccesses(currentAccesses + data.getAccesses());
							}
						}
					}
				}
			}
		}
	}
	
	private BufferIdentifier addNewBufferIdentifier(String sourceActor, String sourcePort, String targetActor, String targetPort) {
		BufferIdentifier bufferIdentifier = null;
		
		for (BufferIdentifier bi : hitLatencies.keySet()) {
			if (bi.getSourceActor().equals(sourceActor)
					&& bi.getSourcePort().equals(sourcePort)
						&& bi.getTargetActor().equals(targetActor)
							&& bi.getTargetPort().equals(targetPort)) {
				bufferIdentifier = bi;
				break;
			}
		}
		
		if (bufferIdentifier == null) {
			bufferIdentifier = new BufferIdentifier(sourceActor, sourcePort, targetActor, targetPort);
			
			/*
			 * The first 5 memory levels (+ uncached memory (6)), as defined in numap_analyse.c. 
			 */
			LatencyCollector[] latenciesH = new LatencyCollector[6];
			LatencyCollector newLatency = new LatencyCollector("L1", "Hit", 0.0, 0.0);
			latenciesH[0] = newLatency;
			newLatency = new LatencyCollector("LFB", "Hit", 0.0, 0.0);
			latenciesH[1] = newLatency;
			newLatency = new LatencyCollector("L2", "Hit", 0.0, 0.0);
			latenciesH[2] = newLatency;
			newLatency = new LatencyCollector("L3", "Hit", 0.0, 0.0);
			latenciesH[3] = newLatency;
			newLatency = new LatencyCollector("Local RAM", "Hit", 0.0, 0.0);
			latenciesH[4] = newLatency;
			newLatency = new LatencyCollector("Uncached Memory", "Hit", 0.0, 0.0);
			latenciesH[5] = newLatency;
			hitLatencies.put(bufferIdentifier, latenciesH);
			
			// FIXME: the report for writes contains only the hit/miss information about L1
			LatencyCollector[] latenciesM = new LatencyCollector[6];
			newLatency = new LatencyCollector("L1", "Miss", 0.0, 0.0);
			latenciesM[0] = newLatency;
			newLatency = new LatencyCollector("LFB", "Miss", 0.0, 0.0);
			latenciesM[1] = newLatency;
			newLatency = new LatencyCollector("L2", "Miss", 0.0, 0.0);
			latenciesM[2] = newLatency;
			newLatency = new LatencyCollector("L3", "Miss", 0.0, 0.0);
			latenciesM[3] = newLatency;
			newLatency = new LatencyCollector("Local RAM", "Miss", 0.0, 0.0);
			latenciesM[4] = newLatency;
			newLatency = new LatencyCollector("Uncached Memory", "Miss", 0.0, 0.0);
			latenciesM[5] = newLatency;
			missLatencies.put(bufferIdentifier, latenciesM);
		}
		
		return bufferIdentifier;
	}
}
