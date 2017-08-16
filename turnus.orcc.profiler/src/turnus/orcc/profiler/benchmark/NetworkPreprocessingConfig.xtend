package turnus.orcc.profiler.benchmark

import net.sf.orcc.df.Network

class NetworkPreprocessingConfig {
	val Network baseNetwork;
	new(Network network) {
		this.baseNetwork = network
	}
	
}