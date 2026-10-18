package gama.ui.diagram.features.create;

import org.eclipse.graphiti.features.IFeatureProvider;
import gama.ui.diagram.swt.editFrame.DiagramBehavior;
import gama.ui.diagram.swt.image.GamaImageProvider;

public class CreateRuleLinkFeature extends CreateBehaviorLinkFeature {
	public CreateRuleLinkFeature(final IFeatureProvider fp) {
		super(fp, DiagramBehavior.RULE, GamaImageProvider.IMG_RULE);
	}
}
