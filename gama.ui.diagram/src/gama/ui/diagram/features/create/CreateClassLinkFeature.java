package gama.ui.diagram.features.create;

import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.ICreateConnectionContext;

import gama.ui.diagram.metamodel.EWorldAgent;

public final class CreateClassLinkFeature extends CreateSubSpeciesLinkFeature {
	public CreateClassLinkFeature(final IFeatureProvider fp) {
		super(fp, true);
	}

	@Override
	public boolean canStartConnection(final ICreateConnectionContext context) {
		return getESpecies(context.getSourceAnchor()) instanceof EWorldAgent;
	}

	@Override
	public boolean canCreate(final ICreateConnectionContext context) {
		return canStartConnection(context);
	}
}
