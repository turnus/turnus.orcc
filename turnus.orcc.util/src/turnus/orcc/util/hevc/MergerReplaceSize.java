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
package turnus.orcc.util.hevc;

import static turnus.common.TurnusOptions.BUFFER_SIZE_FILE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import turnus.common.TurnusException;
import turnus.common.configuration.Configuration;
import turnus.common.configuration.Configuration.CliParser;
import turnus.common.configuration.Option;
import turnus.common.io.Logger;

/**
 * 
 * @author Malgorzata Michalska
 *
 */
public class MergerReplaceSize {
	
	public static final Option<File> MERGER_C_CODE = Option.create().//
			setName("merger").//
			setDescription("Merger C code. ").//
			setType(File.class).build();
	
	private static Configuration configuration;

	public static void main(String args[]) {
		try {
			parse(args);
			
			Map<String, String> mergerInputPorts = new HashMap<String, String>();
			Map<String, String> mergerOutputPorts = new HashMap<String, String>();
			
			try {
				File bufferFile = configuration.getValue(BUFFER_SIZE_FILE);
				FileInputStream input = new FileInputStream(bufferFile);
				BufferedReader br = new BufferedReader(new InputStreamReader(input));
				String line = null;
				int startId = 0;
				int endId = 0;
				String port = null;
				String size = null;
				while ((line = br.readLine()) != null) {
					if (line.contains("Parsers_Merger")) {
						startId = line.indexOf("source-port=\"") + 13;
						endId = line.indexOf("\"", startId);
						port = line.substring(startId, endId);
						
						startId = line.indexOf("size=\"") + 6;
						endId = line.indexOf("\"", startId);
						size = line.substring(startId, endId);
						
						if (port.contains("op")) {
							mergerOutputPorts.put(port, size);
						}
						else {
							startId = line.indexOf("target-port=\"") + 13;
							endId = line.indexOf("\"", startId);
							port = line.substring(startId, endId);
							mergerInputPorts.put(port, size);
						}
					}
				}
				Logger.info("Buffer size file parsed.");
				br.close();
			} catch (Exception e) {
				try {
					throw new TurnusException("Buffer size file cannot be loaded.", e);
				} catch (TurnusException e1) {
					e1.printStackTrace();
				}
			}
			
			try {
				File mergerFile = configuration.getValue(MERGER_C_CODE);
				FileInputStream input = new FileInputStream(mergerFile);
				BufferedReader br = new BufferedReader(new InputStreamReader(input));
				
				String newName = mergerFile.getAbsolutePath();
				if (newName.contains("_Orig"))
					newName = newName.replace("_Orig", "");
				else {
					int pos = newName.lastIndexOf(".");
					if (pos > 0) {
						newName = newName.substring(0, pos) + "_Updated.c";
					}
				}
				FileOutputStream output = new FileOutputStream(new File(newName));
				OutputStreamWriter osw = new OutputStreamWriter(output);
				
				String line = null;
				int startId = 0;
				int endId = 0;
				
				while ((line = br.readLine()) != null) {
					startId = line.indexOf("#define SIZE_op");
					if (startId >= 0) {
						startId = line.indexOf("SIZE_op");
						endId = line.indexOf(" " , startId);
						line = line.substring(0, line.lastIndexOf(" ") + 1) + mergerOutputPorts.get(line.substring(startId + 5, endId));
					}
					else {
						startId = line.indexOf("#define SIZE_in");
						if (startId >= 0) {
							startId = line.indexOf("SIZE_in");
							endId = line.indexOf(" " , startId);
							line = line.substring(0, line.lastIndexOf(" ") + 1) + mergerInputPorts.get(line.substring(startId + 5, endId));
						}
					}
					osw.write(line + "\n");
				}
				Logger.info("Merger C file parsed.");
				br.close();
				Logger.info("Merger C file update done.");
				osw.close();
			} catch (Exception e) {
				try {
					throw new TurnusException("Merger C code cannot be loaded.", e);
				} catch (TurnusException e1) {
					e1.printStackTrace();
				}
			}
			
		} catch (TurnusException e2) {
			e2.printStackTrace();
		}
	}
	
	private static void parse(String[] args) throws TurnusException {
		CliParser cliParser = new CliParser()
				.setOption(BUFFER_SIZE_FILE, true)//
				.setOption(MERGER_C_CODE, true);

		configuration = cliParser.parse(args);
	}
}
