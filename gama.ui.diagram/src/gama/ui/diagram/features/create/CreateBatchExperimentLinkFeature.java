package gama.ui.diagram.features.create;

import org.eclipse.graphiti.features.IFeatureProvider;
import gama.ui.diagram.swt.image.GamaImageProvider;

public class CreateBatchExperimentLinkFeature extends CreateExperimentLinkFeature {
	public CreateBatchExperimentLinkFeature(final IFeatureProvider fp) {
		super(fp, "batch", GamaImageProvider.IMG_BATCHXPLINK);
	}
}
