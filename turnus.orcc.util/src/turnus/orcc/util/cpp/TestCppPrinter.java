package turnus.orcc.util.cpp;

import java.io.File;

import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

import turnus.common.util.EcoreUtils;
import turnus.model.ModelsRegister;
import turnus.model.analysis.scheduling.MarkovSimpleSchedulerReport;

public class TestCppPrinter {

	public static void main(String[] args) {
		ModelsRegister.init();
		
		File reportFile = new File("/home/scb/Development/orcc/orc-apps/JPEG/turnus/profiling_dynamic_analysis/jpeg.decoder.Top_Decoder/20170508110243/analysis/scheduling/20170509154046.mvsched");
		File outFile = new File("/home/scb/Development/orcc/orc-apps/JPEG/turnus/profiling_dynamic_analysis/jpeg.decoder.Top_Decoder/20170508110243/analysis/scheduling/20170509154046.cpp");
		
		MarkovSimpleSchedulerReport report = EcoreUtils.loadEObject(new ResourceSetImpl(), reportFile);
		new MarkovSchedulerCppBuilder(report).write(outFile);

	}

}
