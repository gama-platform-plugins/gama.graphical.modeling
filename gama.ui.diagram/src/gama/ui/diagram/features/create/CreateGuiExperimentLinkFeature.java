package gama.ui.diagram.features.create;

import org.eclipse.graphiti.features.IFeatureProvider;
import gama.ui.diagram.swt.image.GamaImageProvider;

public class CreateGuiExperimentLinkFeature extends CreateExperimentLinkFeature {
	public CreateGuiExperimentLinkFeature(final IFeatureProvider fp) {
		super(fp, "gui", GamaImageProvider.IMG_GUIXPLINK);
	}
}
