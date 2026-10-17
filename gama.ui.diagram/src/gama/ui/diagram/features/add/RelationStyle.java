package gama.ui.diagram.features.add;

import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.LayeredPane;
import org.eclipse.gef.LayerConstants;
import org.eclipse.gef.editparts.LayerManager;
import org.eclipse.graphiti.mm.algorithms.AbstractText;
import org.eclipse.graphiti.mm.algorithms.GraphicsAlgorithm;
import org.eclipse.graphiti.mm.algorithms.MultiText;
import org.eclipse.graphiti.mm.algorithms.Polyline;
import org.eclipse.graphiti.mm.algorithms.Rectangle;
import org.eclipse.graphiti.mm.algorithms.styles.Color;
import org.eclipse.graphiti.mm.algorithms.styles.Orientation;
import org.eclipse.graphiti.mm.pictograms.Connection;
import org.eclipse.graphiti.mm.pictograms.ConnectionDecorator;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.graphiti.ui.services.GraphitiUi;
import org.eclipse.graphiti.util.ColorConstant;
import org.eclipse.graphiti.util.IColorConstant;

import gama.ui.diagram.metamodel.EGamaLink;
import gama.ui.diagram.metamodel.GamaPackage;

public final class RelationStyle {

	private RelationStyle() {}

	public static void placeConnectionsBehindShapes(final LayerManager layers, final IFigure diagramFigure) {
		final IFigure connections = layers.getLayer(LayerConstants.CONNECTION_LAYER);
		final IFigure shapes = layers.getLayer(LayerConstants.PRIMARY_LAYER);
		final LayeredPane parent = (LayeredPane) connections.getParent();
		if (parent.getChildren().indexOf(connections) > parent.getChildren().indexOf(shapes)) {
			parent.remove(connections);
			parent.add(connections, LayerConstants.CONNECTION_LAYER, parent.getChildren().indexOf(shapes));
		}
		// The canvas background must not cover the connections underneath the diagram's shapes.
		diagramFigure.setOpaque(false);
	}

	public static IColorConstant color(final Connection connection) {
		final Object object = Graphiti.getLinkService().getBusinessObjectForLinkedPictogramElement(connection);
		if (!(object instanceof EGamaLink)) return ColorDisplay.RELATION_FOREGROUND;
		return switch (((EGamaLink) object).eClass().getClassifierID()) {
			case GamaPackage.ESUB_SPECIES_LINK -> new ColorConstant(143, 106, 47);
			case GamaPackage.EINHERIT_LINK -> new ColorConstant(109, 86, 145);
			case GamaPackage.EACTION_LINK -> new ColorConstant(169, 88, 52);
			case GamaPackage.EASPECT_LINK -> new ColorConstant(85, 115, 57);
			case GamaPackage.EREFLEX_LINK -> new ColorConstant(173, 79, 76);
			case GamaPackage.EDISPLAY_LINK -> new ColorConstant(55, 126, 137);
			case GamaPackage.EEXPERIMENT_LINK -> new ColorConstant(62, 98, 149);
			case GamaPackage.EPLAN_LINK -> new ColorConstant(53, 119, 98);
			case GamaPackage.ESTATE_LINK -> new ColorConstant(151, 79, 117);
			case GamaPackage.ETASK_LINK -> new ColorConstant(116, 119, 46);
			case GamaPackage.EPERCEIVE_LINK -> new ColorConstant(61, 121, 160);
			case GamaPackage.ERULE_LINK -> new ColorConstant(147, 109, 34);
			case GamaPackage.EEQUATION_LINK -> new ColorConstant(103, 103, 156);
			default -> ColorDisplay.RELATION_FOREGROUND;
		};
	}

	public static void apply(final Diagram diagram, final Connection connection) {
		final IColorConstant color = color(connection);
		styleLine(diagram, connection.getGraphicsAlgorithm(), color);
		final var ga = Graphiti.getGaService();
		for (final ConnectionDecorator decorator : connection.getConnectionDecorators()) {
			GraphicsAlgorithm graphics = decorator.getGraphicsAlgorithm();
			final AbstractText text = label(graphics);
			if (text != null) {
				// MultiText avoids Graphiti's single-line decorator bounds override without nested GAs.
				final MultiText label;
				if (graphics instanceof MultiText) {
					label = (MultiText) graphics;
				} else {
					label = ga.createDefaultMultiText(diagram, decorator, text.getValue());
				}
				graphics = label;
				decorator.setLocationRelative(true);
				decorator.setLocation(0.5);
				decorator.setActive(true);
				graphics.setBackground(ga.manageColor(diagram, IColorConstant.WHITE));
				graphics.setFilled(true);
				graphics.setLineVisible(false);
				graphics.setTransparency(0.0);
				label.setFont(ga.manageFont(diagram, "Arial", 11, false, false));
				label.setForeground(ga.manageColor(diagram, color));
				label.setRotation(0.0);
				label.setHorizontalAlignment(Orientation.ALIGNMENT_CENTER);
				label.setVerticalAlignment(Orientation.ALIGNMENT_CENTER);
				final var size = GraphitiUi.getUiLayoutService().calculateTextSize(label);
				final int width = size.getWidth() + 12;
				final int height = size.getHeight() + 6;
				ga.setLocationAndSize(graphics, -width / 2, -height / 2, width, height);
			} else {
				styleLine(diagram, graphics, color);
			}
		}
	}

	public static boolean isCurrent(final Connection connection) {
		final IColorConstant color = color(connection);
		if (!isCurrentLine(connection.getGraphicsAlgorithm(), color)) return false;
		for (final ConnectionDecorator decorator : connection.getConnectionDecorators()) {
			final GraphicsAlgorithm graphics = decorator.getGraphicsAlgorithm();
			final AbstractText text = label(graphics);
			if (text != null) {
				final var font = text.getFont();
				if (!(graphics instanceof MultiText)
						|| !matches(graphics.getBackground(), IColorConstant.WHITE)
						|| !Boolean.TRUE.equals(graphics.getFilled()) || !Boolean.FALSE.equals(graphics.getLineVisible())
						|| !Double.valueOf(0.0).equals(graphics.getTransparency())
						|| !decorator.isActive() || !decorator.isLocationRelative() || decorator.getLocation() != 0.5
						|| !matches(text.getForeground(), color) || !matches(text.getBackground(), IColorConstant.WHITE)
						|| !Boolean.TRUE.equals(text.getFilled()) || !Boolean.FALSE.equals(text.getLineVisible())
						|| !Double.valueOf(0.0).equals(text.getTransparency())
						|| !Double.valueOf(0.0).equals(text.getRotation())
						|| font == null || !"Arial".equals(font.getName()) || font.getSize() != 11
						|| font.isBold() || font.isItalic()
						|| text.getHorizontalAlignment() != Orientation.ALIGNMENT_CENTER
						|| text.getVerticalAlignment() != Orientation.ALIGNMENT_CENTER) return false;
				final var size = GraphitiUi.getUiLayoutService().calculateTextSize(text);
				final int width = size.getWidth() + 12;
				final int height = size.getHeight() + 6;
				if (graphics.getX() != -width / 2 || graphics.getY() != -height / 2
						|| graphics.getWidth() != width || graphics.getHeight() != height) return false;
			} else if (!isCurrentLine(graphics, color)) {
				return false;
			}
		}
		return true;
	}

	private static AbstractText label(final GraphicsAlgorithm graphics) {
		if (graphics instanceof AbstractText) return (AbstractText) graphics;
		if (graphics instanceof Rectangle) {
			for (final GraphicsAlgorithm child : graphics.getGraphicsAlgorithmChildren()) {
				if (child instanceof AbstractText) return (AbstractText) child;
			}
		}
		return null;
	}

	private static void styleLine(final Diagram diagram, final GraphicsAlgorithm graphics, final IColorConstant color) {
		if (graphics instanceof Polyline) {
			graphics.setForeground(Graphiti.getGaService().manageColor(diagram, color));
			graphics.setLineWidth(1);
		}
	}

	private static boolean isCurrentLine(final GraphicsAlgorithm graphics, final IColorConstant color) {
		return !(graphics instanceof Polyline)
				|| Integer.valueOf(1).equals(graphics.getLineWidth()) && matches(graphics.getForeground(), color);
	}

	private static boolean matches(final Color actual, final IColorConstant expected) {
		return actual != null && actual.getRed() == expected.getRed() && actual.getGreen() == expected.getGreen()
				&& actual.getBlue() == expected.getBlue();
	}
}
