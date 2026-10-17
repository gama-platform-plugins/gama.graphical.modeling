package gama.ui.diagram.features.layout;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.graphiti.datatypes.IDimension;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.ILayoutContext;
import org.eclipse.graphiti.features.impl.AbstractLayoutFeature;
import org.eclipse.graphiti.mm.algorithms.AbstractText;
import org.eclipse.graphiti.mm.algorithms.GraphicsAlgorithm;
import org.eclipse.graphiti.mm.algorithms.Image;
import org.eclipse.graphiti.mm.algorithms.MultiText;
import org.eclipse.graphiti.mm.algorithms.Polyline;
import org.eclipse.graphiti.mm.algorithms.styles.Font;
import org.eclipse.graphiti.mm.algorithms.styles.Orientation;
import org.eclipse.graphiti.mm.pictograms.ContainerShape;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.Shape;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.graphiti.services.IGaService;
import org.eclipse.graphiti.ui.services.GraphitiUi;
import org.eclipse.graphiti.util.IColorConstant;

import gama.ui.diagram.features.add.ColorDisplay;
import gama.ui.diagram.metamodel.EGamaObject;
import gama.ui.diagram.metamodel.ESpecies;

public class LayoutCommonFeature extends AbstractLayoutFeature {

	private static final int PADDING = 12;
	private static final int ICON_SPACE = 32;

	public LayoutCommonFeature(final IFeatureProvider fp) {
		super(fp);
	}

	@Override
	public boolean canLayout(final ILayoutContext context) {
		return context.getPictogramElement() instanceof ContainerShape
				&& getBusinessObjectForPictogramElement(context.getPictogramElement()) instanceof EGamaObject;
	}

	@Override
	public boolean layout(final ILayoutContext context) {
		final ContainerShape shape = (ContainerShape) context.getPictogramElement();
		final EGamaObject object = (EGamaObject) getBusinessObjectForPictogramElement(shape);
		return layout(shape, getDiagram(), object, false);
	}

	public static String attributes(final ESpecies species) {
		return species.getVariables().stream()
				.map(variable -> (variable.getType() == null || variable.getType().isEmpty() ? "var" : variable.getType())
						+ " " + variable.getName())
				.collect(Collectors.joining("\n"));
	}

	public static boolean isTitle(final AbstractText text) {
		return !(text instanceof MultiText) && text.getY() < 25;
	}

	public static boolean layout(final ContainerShape container, final Diagram diagram, final EGamaObject object,
			final boolean fitContents) {
		final IGaService ga = Graphiti.getGaService();
		final GraphicsAlgorithm box = container.getGraphicsAlgorithm();
		final Font titleFont = ga.manageFont(diagram, "Arial", 14, false, true);
		final Font bodyFont = ga.manageFont(diagram, "Arial", 12, false, false);
		final String title = object.getName() == null ? "" : object.getName();
		final IDimension titleSize = GraphitiUi.getUiLayoutService().calculateTextSize(title, titleFont);
		final int headerHeight = Math.max(36, titleSize.getHeight() + 2 * PADDING);
		final String body = object instanceof ESpecies ? attributes((ESpecies) object) : "";
		int bodyWidth = 0;
		int bodyHeight = 0;
		final int lineHeight = GraphitiUi.getUiLayoutService().calculateTextSize("Ag", bodyFont).getHeight() + 4;
		if (!body.isEmpty()) {
			final String[] lines = body.split("\n", -1);
			for (final String line : lines) {
				bodyWidth = Math.max(bodyWidth,
						GraphitiUi.getUiLayoutService().calculateTextSize(line, bodyFont).getWidth());
			}
			bodyHeight = lines.length * lineHeight;
		}
		final int contentWidth = Math.max(object instanceof ESpecies ? 180 : 150,
				Math.max(titleSize.getWidth() + 2 * (PADDING + ICON_SPACE), bodyWidth + 2 * PADDING));
		final int contentHeight = Math.max(50, headerHeight + (bodyHeight == 0 ? 0 : bodyHeight + 2 * PADDING));
		final int width = fitContents ? contentWidth : Math.max(box.getWidth(), contentWidth);
		final int height = fitContents ? contentHeight : Math.max(box.getHeight(), contentHeight);
		boolean changed = box.getWidth() != width || box.getHeight() != height;
		ga.setSize(box, width, height);
		ColorDisplay.updateLegacyNodeColor(object.getColorPicto());
		final List<Integer> rgb = object.getColorPicto();
		box.setBackground(ga.manageColor(diagram, rgb.get(0), rgb.get(1), rgb.get(2)));
		final IColorConstant textColor = ColorDisplay.nodeTextColor(rgb);
		final var foreground = ga.manageColor(diagram, textColor);
		for (final Shape child : container.getChildren()) {
			GraphicsAlgorithm graphics = child.getGraphicsAlgorithm();
			if (graphics instanceof AbstractText) {
				AbstractText text = (AbstractText) graphics;
				final boolean isTitle = isTitle(text);
				if (!isTitle && !(text instanceof MultiText)) {
					text = ga.createDefaultMultiText(diagram, child, body);
					changed = true;
				}
				final String value = isTitle ? title : body;
				if (!value.equals(text.getValue())) changed = true;
				text.setValue(value);
				text.setForeground(foreground);
				text.setFont(isTitle ? titleFont : bodyFont);
				text.setHorizontalAlignment(isTitle ? Orientation.ALIGNMENT_CENTER : Orientation.ALIGNMENT_LEFT);
				text.setVerticalAlignment(isTitle ? Orientation.ALIGNMENT_CENTER : Orientation.ALIGNMENT_TOP);
				changed |= setBounds(ga, text, isTitle ? PADDING + ICON_SPACE : PADDING,
						isTitle ? 8 : headerHeight + PADDING,
						isTitle ? width - 2 * (PADDING + ICON_SPACE) : width - 2 * PADDING,
						isTitle ? (body.isEmpty() ? height - 16 : headerHeight - 16) : bodyHeight);
			} else if (graphics instanceof Polyline) {
				final Polyline line = (Polyline) graphics;
				if (line.getPoints().size() >= 2) {
					line.getPoints().get(0).setX(PADDING);
					line.getPoints().get(0).setY(headerHeight);
					line.getPoints().get(1).setX(width - PADDING);
					line.getPoints().get(1).setY(headerHeight);
				}
				line.setForeground(ga.manageColor(diagram, ColorDisplay.CLASS_FOREGROUND_DEFAULT));
				line.setLineWidth(1);
				child.setVisible(!body.isEmpty());
			} else if (graphics instanceof Image) {
				changed |= setBounds(ga, graphics, PADDING,
						(body.isEmpty() ? height : headerHeight) / 2 - graphics.getHeight() / 2,
						graphics.getWidth(), graphics.getHeight());
			}
		}
		return changed;
	}

	private static boolean setBounds(final IGaService ga, final GraphicsAlgorithm graphics,
			final int x, final int y, final int width, final int height) {
		final boolean changed = graphics.getX() != x || graphics.getY() != y || graphics.getWidth() != width
				|| graphics.getHeight() != height;
		if (changed) ga.setLocationAndSize(graphics, x, y, width, height);
		return changed;
	}
}
