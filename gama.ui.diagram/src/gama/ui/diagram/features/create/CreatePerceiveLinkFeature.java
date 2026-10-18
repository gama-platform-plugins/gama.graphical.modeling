package gama.ui.diagram.features.create;

import org.eclipse.graphiti.features.IFeatureProvider;
import gama.ui.diagram.swt.editFrame.DiagramBehavior;
import gama.ui.diagram.swt.image.GamaImageProvider;

public class CreatePerceiveLinkFeature extends CreateBehaviorLinkFeature {
	public CreatePerceiveLinkFeature(final IFeatureProvider fp) {
		super(fp, DiagramBehavior.PERCEIVE, GamaImageProvider.IMG_PERCEIPT);
	}
}
