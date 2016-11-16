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

import static turnus.common.TurnusOptions.CAL_PROJECT;
import static turnus.common.TurnusOptions.CAL_XDF;
import static turnus.common.TurnusOptions.OUTPUT_DIRECTORY;

import java.io.File;

import org.eclipse.core.resources.IProject;

import net.sf.orcc.util.OrccLogger;
import net.sf.orcc.util.OrccUtil;
import turnus.common.TurnusException;
import turnus.common.TurnusOptions;
import turnus.common.configuration.Configuration;
import turnus.common.util.EcoreUtils;
import turnus.common.util.FileUtils;

/**
 * This class contains utilities methods for creating a proper output path
 * according to the given configuration. The configuration should contain the
 * {@link TurnusOptions#CAL_PROJECT}. If the
 * {@link TurnusOptions#OUTPUT_DIRECTORY} has been already set set, then this
 * one it will be used.
 * 
 * @author Simone Casale Brunet
 *
 */
public class OutputDirectoryBuilder {

	/**
	 * Create an output directory if not specified by the configuration
	 * 
	 * @param configuration
	 * @return
	 * @throws TurnusException
	 */
	public static File create(String analysis, Configuration configuration) throws TurnusException {
		File outPath = null;
		if (!configuration.hasValue(OUTPUT_DIRECTORY)) {
			String project = configuration.getStringValue(CAL_PROJECT);
			String xdf = configuration.getStringValue(CAL_XDF);
			IProject pojo = EcoreUtils.getProject(project);
			// network name
			String network = OrccUtil.getQualifiedName(pojo.getFile(xdf));
			network = network.replaceAll(" ", "");
			network = network.startsWith(".") ? network.substring(1) : network;
			// project path
			outPath = pojo.getLocation().makeAbsolute().toFile();
			outPath = new File(outPath, "turnus" + File.separator + analysis + File.separator + network);
			outPath = FileUtils.createDirectoryWithTimeStamp(outPath);
		} else {
			outPath = configuration.getValue(OUTPUT_DIRECTORY);
			if (outPath.list().length > 0) {
				outPath = FileUtils.createDirectoryWithTimeStamp(outPath);
				OrccLogger.warnln(
						"The selected output path is not empty. " + " Files will be stored in \"" + outPath + "\"");
			}
		}

		if (!outPath.exists() && !outPath.mkdirs()) {
			throw new TurnusException("Impossible to create the output directory \"" + outPath + "\"");
		}

		configuration.setValue(OUTPUT_DIRECTORY, outPath);
		
		return outPath;

	}
}
