package gama.ui.diagram.features.others;

import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.IReason;
import org.eclipse.graphiti.features.context.IUpdateContext;
import org.eclipse.graphiti.features.impl.AbstractUpdateFeature;
import org.eclipse.graphiti.features.impl.Reason;
import org.eclipse.graphiti.mm.algorithms.AbstractText;
import org.eclipse.graphiti.mm.algorithms.MultiText;
import org.eclipse.graphiti.mm.algorithms.styles.Color;
import org.eclipse.graphiti.mm.pictograms.ContainerShape;
import org.eclipse.graphiti.mm.pictograms.Shape;
import org.eclipse.graphiti.util.IColorConstant;

import gama.ui.diagram.features.add.ColorDisplay;
import gama.ui.diagram.features.layout.LayoutCommonFeature;
import gama.ui.diagram.metamodel.EGamaObject;
import gama.ui.diagram.metamodel.ESpecies;

public class UpdateEGamaObjectFeature extends AbstractUpdateFeature {

	public UpdateEGamaObjectFeature(final IFeatureProvider fp) {
		super(fp);
	}

	@Override
	public boolean canUpdate(final IUpdateContext context) {
		return context.getPictogramElement() instanceof ContainerShape
				&& getBusinessObjectForPictogramElement(context.getPictogramElement()) instanceof EGamaObject;
	}

	@Override
	public IReason updateNeeded(final IUpdateContext context) {
		if (!canUpdate(context)) return Reason.createFalseReason();
		final ContainerShape container = (ContainerShape) context.getPictogramElement();
		final EGamaObject object = (EGamaObject) getBusinessObjectForPictogramElement(container);
		final boolean error = Boolean.TRUE.equals(object.getHasError());
		final IColorConstant outline =
				error ? ColorDisplay.CLASS_FOREGROUND_ERROR : ColorDisplay.CLASS_FOREGROUND_DEFAULT;
		if (!matches(container.getGraphicsAlgorithm().getForeground(), outline)
				|| container.getGraphicsAlgorithm().getLineWidth() != (error ? 2 : 1)) {
			return Reason.createTrueReason("Node outline is out of date");
		}
		final String name = object.getName() == null ? "" : object.getName();
		final String attributes = object instanceof ESpecies ? LayoutCommonFeature.attributes((ESpecies) object) : "";
		for (final Shape child : container.getChildren()) {
			if (child.getGraphicsAlgorithm() instanceof AbstractText) {
				final AbstractText text = (AbstractText) child.getGraphicsAlgorithm();
				final boolean title = LayoutCommonFeature.isTitle(text);
				if (!(title ? name : attributes).equals(text.getValue())
						|| !matches(text.getForeground(), ColorDisplay.nodeTextColor(object.getColorPicto()))
						|| !title && !(text instanceof MultiText)) {
					return Reason.createTrueReason("Node contents are out of date");
				}
			}
		}
		return Reason.createFalseReason();
	}

	@Override
	public boolean update(final IUpdateContext context) {
		if (!canUpdate(context)) return false;
		final ContainerShape container = (ContainerShape) context.getPictogramElement();
		final EGamaObject object = (EGamaObject) getBusinessObjectForPictogramElement(container);
		final boolean error = Boolean.TRUE.equals(object.getHasError());
		container.getGraphicsAlgorithm().setForeground(manageColor(
				error ? ColorDisplay.CLASS_FOREGROUND_ERROR : ColorDisplay.CLASS_FOREGROUND_DEFAULT));
		container.getGraphicsAlgorithm().setLineWidth(error ? 2 : 1);
		LayoutCommonFeature.layout(container, getDiagram(), object, false);
		return true;
	}

	private boolean matches(final Color actual, final IColorConstant expected) {
		return actual != null && actual.getRed() == expected.getRed() && actual.getGreen() == expected.getGreen()
				&& actual.getBlue() == expected.getBlue();
	}
}
