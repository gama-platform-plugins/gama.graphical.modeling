/*******************************************************************************************************
 *
 * MyGamaToolBehaviorProvider.java, in gama.ui.diagram, is part of the source code of the GAMA modeling and
 * simulation platform (v.1.9.3).
 *
 * (c) 2007-2024 UMI 209 UMMISCO IRD/SU & Partners (IRIT, MIAT, TLU, CTU)
 *
 * Visit https://github.com/gama-platform/gama for license information and contacts.
 *
 ********************************************************************************************************/
package gama.ui.diagram.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.graphiti.dt.IDiagramTypeProvider;
import org.eclipse.graphiti.features.ICreateConnectionFeature;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.IDoubleClickContext;
import org.eclipse.graphiti.features.custom.ICustomFeature;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.palette.IPaletteCompartmentEntry;
import org.eclipse.graphiti.palette.impl.ConnectionCreationToolEntry;
import org.eclipse.graphiti.palette.impl.PaletteCompartmentEntry;
import org.eclipse.graphiti.tb.DefaultToolBehaviorProvider;

import gama.api.types.map.GamaMapFactory;
import gama.ui.diagram.swt.image.GamaImageProvider;
import gama.ui.diagram.features.create.CreateActionLinkFeature;
import gama.ui.diagram.features.create.CreateAspectLinkFeature;
import gama.ui.diagram.features.create.CreateEquationLinkFeature;
import gama.ui.diagram.features.create.CreateInheritingLinkFeature;
import gama.ui.diagram.features.create.CreatePerceiveLinkFeature;
import gama.ui.diagram.features.create.CreatePlanLinkFeature;
import gama.ui.diagram.features.create.CreateReflexLinkFeature;
import gama.ui.diagram.features.create.CreateRuleLinkFeature;
import gama.ui.diagram.features.create.CreateStateLinkFeature;
import gama.ui.diagram.features.create.CreateSubGridLinkFeature;
import gama.ui.diagram.features.create.CreateSubSpeciesLinkFeature;
import gama.ui.diagram.features.create.CreateClassInheritingLinkFeature;
import gama.ui.diagram.features.create.CreateTaskLinkFeature;
import gama.ui.diagram.features.create.CreateGuiExperimentLinkFeature;
import gama.ui.diagram.features.create.CreateDisplayLinkFeature;
import gama.ui.diagram.features.edit.EditActionFeature;
import gama.ui.diagram.features.edit.EditAspectFeature;
import gama.ui.diagram.features.edit.EditDisplayFeature;
import gama.ui.diagram.features.edit.EditEquationFeature;
import gama.ui.diagram.features.edit.EditExperimentFeature;
import gama.ui.diagram.features.edit.EditPerceiveFeature;
import gama.ui.diagram.features.edit.EditPlanFeature;
import gama.ui.diagram.features.edit.EditReflexFeature;
import gama.ui.diagram.features.edit.EditRuleFeature;
import gama.ui.diagram.features.edit.EditSpeciesFeature;
import gama.ui.diagram.features.edit.EditStateFeature;
import gama.ui.diagram.features.edit.EditTaskFeature;
import gama.ui.diagram.metamodel.EAction;
import gama.ui.diagram.metamodel.EAspect;
import gama.ui.diagram.metamodel.EDisplay;
import gama.ui.diagram.metamodel.EEquation;
import gama.ui.diagram.metamodel.EExperiment;
import gama.ui.diagram.metamodel.EPerceive;
import gama.ui.diagram.metamodel.EPlan;
import gama.ui.diagram.metamodel.EReflex;
import gama.ui.diagram.metamodel.ERule;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.EState;
import gama.ui.diagram.metamodel.ETask;
import gama.ui.diagram.swt.editFrame.EditFrame;

/**
 * The Class MyGamaToolBehaviorProvider.
 */
public class MyGamaToolBehaviorProvider extends DefaultToolBehaviorProvider {

	/** The frames. */
	final Map<EObject, EditFrame> frames;

	/**
	 * Instantiates a new my gama tool behavior provider.
	 *
	 * @param diagramTypeProvider
	 *            the diagram type provider
	 */
	public MyGamaToolBehaviorProvider(final IDiagramTypeProvider diagramTypeProvider) {
		super(diagramTypeProvider);
		frames = GamaMapFactory.create();
	}

	@Override
	public ICustomFeature getDoubleClickFeature(final IDoubleClickContext context) {
		ICustomFeature customFeature = null;
		PictogramElement[] pes = context.getPictogramElements();
		if (pes != null && pes.length == 1) {
			Object bo = getDiagramTypeProvider().getFeatureProvider().getBusinessObjectForPictogramElement(pes[0]);
			if (bo instanceof EExperiment) {
				customFeature = new EditExperimentFeature(getFeatureProvider(), frames.get(bo), this);
			} else if (bo instanceof ESpecies) {
				customFeature = new EditSpeciesFeature(getFeatureProvider(), frames.get(bo), this);
			} else if (bo instanceof EAction) {
				customFeature = new EditActionFeature(getFeatureProvider(), frames.get(bo), this);
			} else if (bo instanceof EReflex) {
				customFeature = new EditReflexFeature(getFeatureProvider(), frames.get(bo), this);
			} else if (bo instanceof EPlan) {
				customFeature = new EditPlanFeature(getFeatureProvider(), frames.get(bo), this);
			} else if (bo instanceof EPerceive) {
				customFeature = new EditPerceiveFeature(getFeatureProvider(), frames.get(bo), this);
			} else if (bo instanceof ERule) {
				customFeature = new EditRuleFeature(getFeatureProvider(), frames.get(bo), this);
			} else if (bo instanceof ETask) {
				customFeature = new EditTaskFeature(getFeatureProvider(), frames.get(bo), this);
			} else if (bo instanceof EState) {
				customFeature = new EditStateFeature(getFeatureProvider(), frames.get(bo), this);
			} else if (bo instanceof EAspect) {
				customFeature = new EditAspectFeature(getFeatureProvider(), frames.get(bo), this);
			} else if (bo instanceof EEquation) {
				customFeature = new EditEquationFeature(getFeatureProvider(), frames.get(bo), this);
			} else if (bo instanceof EDisplay) {
				customFeature = new EditDisplayFeature(getFeatureProvider(), frames.get(bo), this);
			} /*
				 * else { customFeature = new RenameEGamaObjectFeature(getFeatureProvider()); }
				 */
			// canExecute() tests especially if the context contains a EClass
			if (customFeature != null && customFeature.canExecute(context)) return customFeature;
		}

		return super.getDoubleClickFeature(context);
	}

	@Override
	public IPaletteCompartmentEntry[] getPalette() {
		List<IPaletteCompartmentEntry> ret = new ArrayList<>();

		// add new compartment at the end of the existing compartments
		PaletteCompartmentEntry compartmentAgentEntry = new PaletteCompartmentEntry("World", GamaImageProvider.IMG_SECTION_NEUTRAL);
		PaletteCompartmentEntry compartmentAgentFeatureEntry = new PaletteCompartmentEntry("Species", GamaImageProvider.IMG_SECTION_ORANGE);
		PaletteCompartmentEntry compartmentClasses = new PaletteCompartmentEntry("Classes", GamaImageProvider.IMG_SECTION_CLASS);
		PaletteCompartmentEntry compartmentExperimentEntry = new PaletteCompartmentEntry("Experiments", GamaImageProvider.IMG_SECTION_BLUE);
		PaletteCompartmentEntry compartmentBDI = new PaletteCompartmentEntry("BDI Architecture", GamaImageProvider.IMG_SECTION_GREEN);
		PaletteCompartmentEntry compartmentFSM = new PaletteCompartmentEntry("Finite State Machine", GamaImageProvider.IMG_SECTION_PINK);
		PaletteCompartmentEntry compartmentTask = new PaletteCompartmentEntry("Task-based Architecture", GamaImageProvider.IMG_SECTION_GREEN);
		ret.add(compartmentAgentEntry);
		ret.add(compartmentClasses);
		ret.add(compartmentAgentFeatureEntry);
		ret.add(compartmentExperimentEntry);
		ret.add(compartmentBDI);
		ret.add(compartmentFSM);
		ret.add(compartmentTask);

		IFeatureProvider featureProvider = getFeatureProvider();
		ICreateConnectionFeature[] createConnectionFeatures = featureProvider.getCreateConnectionFeatures();
		for (ICreateConnectionFeature cf : createConnectionFeatures) {
			ConnectionCreationToolEntry connectionCreationToolEntry = new ConnectionCreationToolEntry(
					cf.getCreateName(), cf.getCreateDescription(), cf.getCreateImageId(), cf.getCreateLargeImageId());
			connectionCreationToolEntry.addCreateConnectionFeature(cf);
			if (cf instanceof CreateActionLinkFeature) {
				final var classAction = new ConnectionCreationToolEntry(cf.getCreateName(), cf.getCreateDescription(),
						cf.getCreateImageId(), cf.getCreateLargeImageId());
				classAction.addCreateConnectionFeature(cf);
				compartmentClasses.addToolEntry(classAction);
			}
			if (cf instanceof CreateClassInheritingLinkFeature) {
				compartmentClasses.addToolEntry(connectionCreationToolEntry);
			} else if (cf instanceof CreateSubSpeciesLinkFeature || cf instanceof CreateSubGridLinkFeature
					|| cf instanceof CreateInheritingLinkFeature || cf instanceof CreateGuiExperimentLinkFeature) {
				compartmentAgentEntry.addToolEntry(connectionCreationToolEntry);
			} else if (cf instanceof CreateActionLinkFeature || cf instanceof CreateAspectLinkFeature
					|| cf instanceof CreateReflexLinkFeature || cf instanceof CreateEquationLinkFeature) {
				compartmentAgentFeatureEntry.addToolEntry(connectionCreationToolEntry);
			} else if (cf instanceof CreatePerceiveLinkFeature || cf instanceof CreateRuleLinkFeature
					|| cf instanceof CreatePlanLinkFeature) {
				compartmentBDI.addToolEntry(connectionCreationToolEntry);
			} else if (cf instanceof CreateStateLinkFeature) {
				compartmentFSM.addToolEntry(connectionCreationToolEntry);
			} else if (cf instanceof CreateTaskLinkFeature) {
				compartmentTask.addToolEntry(connectionCreationToolEntry);
			} else if (cf instanceof CreateDisplayLinkFeature) {
				compartmentExperimentEntry.addToolEntry(connectionCreationToolEntry);
			}
		}
		return ret.toArray(new IPaletteCompartmentEntry[ret.size()]);
	}

	/**
	 * Gets the frames.
	 *
	 * @return the frames
	 */
	public Map<EObject, EditFrame> getFrames() { return frames; }

}
