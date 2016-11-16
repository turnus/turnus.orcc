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

import static turnus.orcc.profiler.code.transfo.SharedVariablesLinker.SHARED_VARIABLES_PROFILE;
import static turnus.orcc.profiler.util.TurnusModelAdapter.createFrom;
import static turnus.orcc.profiler.util.TurnusModelAdapter.getFrom;

import java.io.File;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import net.sf.orcc.util.OrccLogger;
import net.sf.orcc.util.OrccUtil;
import turnus.common.TurnusException;
import turnus.common.util.FileUtils;
import turnus.model.dataflow.Action;
import turnus.model.dataflow.Actor;
import turnus.model.dataflow.ActorClass;
import turnus.model.dataflow.Buffer;
import turnus.model.dataflow.DataflowFactory;
import turnus.model.dataflow.Network;
import turnus.model.dataflow.Port;
import turnus.model.dataflow.SharedVariable;
import turnus.model.dataflow.Variable;
import turnus.model.versioning.Version;
import turnus.model.versioning.Versioner;

/**
 * 
 * @author Simone Casale Brunet
 *
 */
public class TurnusModelInjector {

	public static final String TURNUS_MODEL = "turnus.profiler.orcc.wrapper";

	private final Versioner versioner;

	public TurnusModelInjector(Versioner versioner) {
		this.versioner = versioner;
	}

	private Collection<net.sf.orcc.df.Action> getAllActions(net.sf.orcc.df.Actor actor) {
		// get all the actions
		Set<net.sf.orcc.df.Action> actions = new HashSet<net.sf.orcc.df.Action>();
		actions.addAll(actor.getActions());
		actions.addAll(actor.getInitializes());
		return actions;
	}

	private String getClassName(net.sf.orcc.df.Actor actor) {
		return actor.getFile().getProjectRelativePath().removeFirstSegments(1).removeFileExtension().toString()
				.replace("/", ".");
	}

	private String getNameSpace(net.sf.orcc.df.Actor actor) {
		return actor.getFile().getProjectRelativePath().removeLastSegments(1).removeFirstSegments(1).toString()
				.replace("/", ".");
	}

	private String getNetworkName(net.sf.orcc.df.Network orccNetwork) {
		String name = OrccUtil.getQualifiedName(orccNetwork.getFile());

		if (name == null || name.isEmpty()) {
			name = "network";
		} else {
			name = name.replaceAll(" ", "");
			name = name.startsWith(".") ? name.substring(1) : name;
		}

		return name;
	}

	private String getProjectName(net.sf.orcc.df.Network orccNetwork) {
		return orccNetwork.getFile().getProject().getName();
	}

	private String getSourceCode(net.sf.orcc.df.Actor orccActor) {
		try {
			File file = FileUtils.getFile(orccActor.getFile());
			return FileUtils.toString(file);
		} catch (Exception e) {
			OrccLogger.debugln("Source code of " + getClassName(orccActor) + " cannot be copyed");
		}
		return "";
	}

	private File getSourceFile(net.sf.orcc.df.Actor orccActor) {
		return orccActor.getFile().getRawLocation().makeAbsolute().toFile();
	}

	private File getSourceFile(net.sf.orcc.df.Network orccNetwork) {
		return orccNetwork.getFile().getRawLocation().makeAbsolute().toFile();
	}

	private String getSourceFileName(net.sf.orcc.df.Actor orccActor) {
		try {
			return FileUtils.getPortablePath(orccActor.getFile());
		} catch (TurnusException e) {
			return "";
		}
	}

	private String getSourceFileName(net.sf.orcc.df.Network orccNetwork) {
		try {
			return FileUtils.getPortablePath(orccNetwork.getFile());
		} catch (TurnusException e) {
			return "";
		}
	}

	public Network inject(net.sf.orcc.df.Network orccNetwork) {
		Date now = new Date();

		DataflowFactory factory = DataflowFactory.eINSTANCE;
		Network network = factory.createNetwork();
		network.setProject(getProjectName(orccNetwork));
		network.setSourceFile(getSourceFileName(orccNetwork));
		network.setName(getNetworkName(orccNetwork));
		orccNetwork.setAttribute(TURNUS_MODEL, (Object) network);

		Version version = versioner.getVersion(getSourceFile(orccNetwork));
		version.setVersionDate(now);
		network.setVersion(version);

		// actors
		for (net.sf.orcc.graph.Vertex vertex : orccNetwork.getChildren()) {
			net.sf.orcc.df.Actor orccActor = vertex.getAdapter(net.sf.orcc.df.Actor.class);

			String className = getClassName(orccActor);
			ActorClass actorClass = network.getActorClass(className);

			// if the actor-class is not yet defined, create a new one
			if (actorClass == null) {
				actorClass = factory.createActorClass();
				actorClass.setName(className);
				actorClass.setSourceFile(getSourceFileName(orccActor));
				actorClass.setNameSpace(getNameSpace(orccActor));
				actorClass.setSourceCode(getSourceCode(orccActor));

				network.getActorClasses().add(actorClass);

				version = versioner.getVersion(getSourceFile(orccActor));
				version.setVersionDate(now);
				actorClass.setVersion(version);
			}

			// actor instance
			Actor actor = factory.createActor();
			actor.setName(orccActor.getSimpleName());
			actor.setActorClass(actorClass);
			network.getActors().add(actor);
			orccActor.setAttribute(TURNUS_MODEL, (Object) actor);

			// incoming actor ports
			for (net.sf.orcc.df.Port orccPort : orccActor.getInputs()) {
				Port port = factory.createPort();
				port.setName(orccPort.getName());
				actor.getInputPorts().add(port);
				orccPort.setAttribute(TURNUS_MODEL, (Object) port);
			}

			// output actor ports
			for (net.sf.orcc.df.Port orccPort : orccActor.getOutputs()) {
				Port port = factory.createPort();
				port.setName(orccPort.getName());
				actor.getOutputPorts().add(port);
				orccPort.setAttribute(TURNUS_MODEL, (Object) port);
			}

			// actions
			for (net.sf.orcc.df.Action orccAction : getAllActions(orccActor)) {
				Action action = factory.createAction();
				action.setName(orccAction.getName());
				actor.getActions().add(action);
				orccAction.setAttribute(TURNUS_MODEL, (Object) action);

				// input patterns
				for (net.sf.orcc.df.Port orccPort : orccAction.getInputPattern().getPorts()) {
					Port port = (Port) getFrom(orccPort);
					action.getInputPorts().add(port);
				}

				// output patterns
				for (net.sf.orcc.df.Port orccPort : orccAction.getOutputPattern().getPorts()) {
					Port port = (Port) getFrom(orccPort);
					action.getOutputPorts().add(port);
				}

			}

			// instance state variables
			for (net.sf.orcc.ir.Var orccSvar : orccActor.getStateVars()) {
				Variable svar = null;
				if (orccSvar.hasAttribute(SHARED_VARIABLES_PROFILE)) {
					SharedVariable shvar = factory.createSharedVariable();
					String id = orccSvar.getAttribute("shared").getValueAsString("id");
					shvar.setTag(id);
					// FIXME shared variables takes the name of the id
					shvar.setName(id);
					shvar.setAttribute("realName", orccSvar.getName());
					
					svar = shvar;
				} else {
					svar = factory.createVariable();
					svar.setName(orccSvar.getName());
				}
				svar.setType(createFrom(orccSvar.getType()));
				actor.getVariables().add(svar);
				orccSvar.setAttribute(TURNUS_MODEL, (Object) svar);
			}

			// threat parameters as state variables
			for (net.sf.orcc.ir.Var orccParam : orccActor.getParameters()) {
				Variable svar = factory.createVariable();
				svar.setName(orccParam.getName());
				svar.setType(createFrom(orccParam.getType()));
				actor.getVariables().add(svar);
				orccParam.setAttribute(TURNUS_MODEL, (Object) svar);
			}

		}

		// fifos
		for (net.sf.orcc.df.Connection orccFifo : orccNetwork.getConnections()) {
			net.sf.orcc.graph.Vertex srcVertex = orccFifo.getSource();
			net.sf.orcc.graph.Vertex tgtVertex = orccFifo.getTarget();
			if (srcVertex instanceof net.sf.orcc.df.Actor && tgtVertex instanceof net.sf.orcc.df.Actor) {
				// get the communication end-points
				net.sf.orcc.df.Port srcPort = orccFifo.getSourcePort();
				net.sf.orcc.df.Port tgtPort = orccFifo.getTargetPort();

				// get source and target actors
				if ((srcPort != null) && (tgtPort != null)) {
					// get the input and output cal ports
					Port tpSource = (Port) getFrom(srcPort);
					Port tpTarget = (Port) getFrom(tgtPort);

					// create the connection
					Buffer buffer = factory.createBuffer();
					buffer.setSource(tpSource);
					buffer.setTarget(tpTarget);

					buffer.setType(createFrom(srcPort.getType()));
					network.getBuffers().add(buffer);
					orccFifo.setAttribute(TURNUS_MODEL, (Object) buffer);
				}
			}
		}

		return network;
	}

}
