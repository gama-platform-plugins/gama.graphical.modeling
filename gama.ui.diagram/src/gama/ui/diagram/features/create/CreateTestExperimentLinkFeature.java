package gama.ui.diagram.features.create;

import org.eclipse.graphiti.features.IFeatureProvider;
import gama.ui.diagram.swt.image.GamaImageProvider;

public class CreateTestExperimentLinkFeature extends CreateExperimentLinkFeature {
	public CreateTestExperimentLinkFeature(final IFeatureProvider fp) {
		super(fp, "test", GamaImageProvider.IMG_TESTXPLINK);
	}
}
