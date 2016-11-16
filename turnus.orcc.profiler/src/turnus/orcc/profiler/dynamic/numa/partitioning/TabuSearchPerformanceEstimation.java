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
package turnus.orcc.profiler.dynamic.numa.partitioning;

import static turnus.common.TurnusOptions.ACTION_WEIGHTS;
import static turnus.common.TurnusOptions.BUFFER_SIZE_DEFAULT;
import static turnus.common.TurnusOptions.BUFFER_SIZE_FILE;
import static turnus.common.TurnusOptions.MAPPING_FILE;
import static turnus.common.TurnusOptions.MAX_ITERATIONS;
import static turnus.common.TurnusOptions.OUTPUT_DIRECTORY;
import static turnus.common.TurnusOptions.RELEASE_BUFFERS_AFTER_PROCESSING;
import static turnus.common.TurnusOptions.SCHEDULING_WEIGHTS;
import static turnus.common.TurnusOptions.TABU_GENERATOR;
import static turnus.common.TurnusOptions.TRACE_FILE;
import static turnus.common.TurnusOptions.WRITE_HIT_CONSTANT;
import static turnus.common.TurnusOptions.WRITE_MISS_CONSTANT;
import static turnus.common.util.FileUtils.changeExtension;
import static turnus.common.util.FileUtils.createDirectory;
import static turnus.common.util.FileUtils.createFileWithTimeStamp;
import static turnus.common.util.FileUtils.createOutputDirectory;

import java.io.File;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import turnus.analysis.partitioning.tabusearch.TabuSearch;
import turnus.analysis.partitioning.tabusearch.TabuSearchJOINT;
import turnus.analysis.partitioning.tabusearch.TabuSearchPROB;
import turnus.common.TurnusException;
import turnus.common.TurnusExtensions;
import turnus.common.configuration.Configuration;
import turnus.common.io.Logger;
import turnus.common.util.EcoreUtils;
import turnus.model.analysis.postprocessing.ActorStatisticsReport;
import turnus.model.mapping.BufferSize;
import turnus.model.mapping.CommunicationWeight;
import turnus.model.mapping.NetworkPartitioning;
import turnus.model.mapping.NetworkWeight;
import turnus.model.mapping.SchedulingWeight;
import turnus.model.mapping.io.XmlBufferSizeReader;
import turnus.model.mapping.io.XmlCommunicationWeightReader;
import turnus.model.mapping.io.XmlNetworkPartitioningReader;
import turnus.model.mapping.io.XmlNetworkPartitioningWriter;
import turnus.model.mapping.io.XmlNetworkWeightReader;
import turnus.model.mapping.io.XmlSchedulingWeightReader;
import turnus.model.trace.TraceProject;
import turnus.model.trace.impl.splitted.SplittedTraceLoader;
import turnus.model.trace.weighter.TraceWeighter;
import turnus.model.trace.weighter.WeighterUtils;
import turnus.orcc.profiler.dynamic.numa.OrccNumaExecution;

/**
 * 
 * @author Malgorzata Michalska
 *
 */
public class TabuSearchPerformanceEstimation {
	
	public static final int MAX_ITERATION_DEFAULT = 10;
	private int iterationMax = 0;
	private int iteration = 0;

	public IStatus run(Configuration configuration) throws TurnusException {	
		Logger.info("===== TABU SEARCH WITH DYNAMIC NUMA PROFILING AND PERFORMANCE ESTIMATION ====");
		
		OrccNumaExecution profiler = null;
		
		TraceProject project = null;
		TraceWeighter weighter = null;
		NetworkPartitioning partitioning = null;
		BufferSize bufferSize = null;
		int defaultBufferSize = 0;
		CommunicationWeight communication = null;
		SchedulingWeight schedWeight = null;
		String generator = null;
		
		ActorStatisticsReport report = null;

		{ // STEP 1 : parse the configuration
			Logger.info("Parsing the configuration for tabu search");
			try {
				File traceFile = configuration.getValue(TRACE_FILE);
				project = TraceProject.open(traceFile);
				project.loadTrace(new SplittedTraceLoader(), configuration);
			} catch (Exception e) {
				throw new TurnusException("The trace project cannot be loaded", e);
			}
			try {
				File weightsFile = configuration.getValue(ACTION_WEIGHTS);
				NetworkWeight weights = new XmlNetworkWeightReader().load(weightsFile);
				weighter = WeighterUtils.getTraceWeighter(configuration, weights);
			} catch (Exception e) {
				throw new TurnusException("The weights file cannot be loaded", e);
			}
			try {
				File mappingFile = configuration.getValue(MAPPING_FILE);
				XmlNetworkPartitioningReader reader = new XmlNetworkPartitioningReader();
				partitioning = reader.load(mappingFile);	
			} catch (Exception e) {
				throw new TurnusException("The mapping file cannot be loaded", e);
			}
			if (configuration.hasValue(BUFFER_SIZE_FILE)) {
				File bufferFile = configuration.getValue(BUFFER_SIZE_FILE);
				XmlBufferSizeReader reader = new XmlBufferSizeReader();
				bufferSize = reader.load(bufferFile);
			} 
			else if (configuration.hasValue(BUFFER_SIZE_DEFAULT)) { // if both parameters are specified, then the default one is ignored
				defaultBufferSize = configuration.getValue(BUFFER_SIZE_DEFAULT);
				bufferSize = new BufferSize(project.getNetwork());
				bufferSize.setDefaultSize(defaultBufferSize);
			} 
			else {
				throw new TurnusException("The buffer size cannot be configured");
			}
			
			if (configuration.hasValue(SCHEDULING_WEIGHTS)) {
				File schWeightsFile = configuration.getValue(SCHEDULING_WEIGHTS);
				schedWeight = new XmlSchedulingWeightReader().load(schWeightsFile);
			} 
			
			iterationMax = configuration.getValue(MAX_ITERATIONS, MAX_ITERATION_DEFAULT);
		}
		
	
			try {
				File mappingFile = configuration.getValue(MAPPING_FILE);
				XmlNetworkPartitioningReader reader = new XmlNetworkPartitioningReader();
				partitioning = reader.load(mappingFile);	
			} catch (Exception e) {
				throw new TurnusException("The mapping file cannot be loaded", e);
			}
		
			iteration = 0;
			
			while (iteration < iterationMax) {
				{ // STEP 2: run numa profiling to get the communication weights
					Logger.info("NUMA profiling and communication weights generation");
					try {
						profiler = new OrccNumaExecution();
						profiler.run(configuration);
					} catch (Exception e) {
						Logger.error("The profiler exited with errors: %s", e.getMessage());
					}
				}
				
				if (profiler.getCommunicationWeightLocation() != null) {
					File communicationWeightsFile = new File(profiler.getCommunicationWeightLocation());
					XmlCommunicationWeightReader reader = new XmlCommunicationWeightReader(project.getNetwork());
					communication = reader.load(communicationWeightsFile);
					
					if (configuration.hasValue(WRITE_HIT_CONSTANT)) {
						communication.setWriteHitConstant(configuration.getValue(WRITE_HIT_CONSTANT));
					}
					if (configuration.hasValue(WRITE_MISS_CONSTANT)) {
						communication.setWriteMissConstant(configuration.getValue(WRITE_MISS_CONSTANT));
					}
				}
				/*
				 * (TODO?)This profiles only the reads. For writes another profiling run + merging is required.
				 */
		
				{ // STEP 3 : Run the analysis
					Logger.info("Run tabu search iteration " + (iteration + 1) + "/" + iterationMax);
					try {
						generator = configuration.getValue(TABU_GENERATOR); 
						boolean release = configuration.hasValue(RELEASE_BUFFERS_AFTER_PROCESSING) ? configuration.getValue(RELEASE_BUFFERS_AFTER_PROCESSING) : false;
						
						if (generator.equals("PROB")) {
							TabuSearchPROB tabuSearchProb = new TabuSearchPROB(project, weighter, communication, schedWeight, bufferSize, release);
							tabuSearchProb.setConfiguration(configuration);
							tabuSearchProb.loadPartitioning(partitioning);
							report = tabuSearchProb.run();
						}
						else if (generator.equals("JOINT")) {
							TabuSearchJOINT tabuSearchJoint = new TabuSearchJOINT(project, weighter, communication, schedWeight, bufferSize, release);
							tabuSearchJoint.setConfiguration(configuration);
							tabuSearchJoint.loadPartitioning(partitioning);
							report = tabuSearchJoint.run();
						}
						else {
							TabuSearch tabuSearch = new TabuSearch(project, weighter, communication, schedWeight, bufferSize, release);
							tabuSearch.setConfiguration(configuration);
							tabuSearch.setGenerator(generator);
							tabuSearch.loadPartitioning(partitioning);
							report = tabuSearch.run();
						}
						Logger.infoRaw(report.toString());
					} catch (Exception e) {
						throw new TurnusException("The analysis cannot be completed", e);
					}
				}
		
				File xcfFile = null;
				{ // STEP 4 : Store the results
					Logger.info("Store the results of tabu search iteration");
					try {
						File outputPath = null;
						if (configuration.hasValue(OUTPUT_DIRECTORY)) {
							outputPath = configuration.getValue(OUTPUT_DIRECTORY);
							createDirectory(outputPath);
						} else {
							outputPath = createOutputDirectory("partitioning", configuration);
						}
		
						File reportFile = createFileWithTimeStamp(outputPath, TurnusExtensions.POST_PROCESSING_ACTOR_REPORT);
						EcoreUtils.storeEObject(report, project.getResourceSet(), reportFile);
						Logger.info("Tabu search partitioning report stored in \"%s\"", reportFile);
		
						xcfFile = changeExtension(reportFile, TurnusExtensions.NETWORK_PARTITIONING);
						new XmlNetworkPartitioningWriter().write(report.asNetworkPartitioning(), xcfFile);
						Logger.info("Network partitioning configuration stored in \"%s\"", xcfFile);
		
					} catch (Exception e) {
						Logger.error("The report file cannot be stored");
						String message = e.getLocalizedMessage();
						if (message != null) {
							Logger.error(" cause: %s", message);
						}
					}
				}
	
				partitioning = report.asNetworkPartitioning();
				configuration.setValue(MAPPING_FILE, xcfFile);
				
				iteration++;
			}
		
		Logger.info("===== TABU SEARCH DONE ====");
		return Status.OK_STATUS;
	}

	public void stop() {
	}

}
