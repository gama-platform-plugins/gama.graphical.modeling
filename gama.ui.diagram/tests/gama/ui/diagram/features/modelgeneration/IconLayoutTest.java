package gama.ui.diagram.features.modelgeneration;

import org.eclipse.graphiti.mm.pictograms.ContainerShape;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.graphiti.ui.internal.figures.GFImageFigure;
import org.eclipse.swt.widgets.Display;

import gama.ui.diagram.features.layout.LayoutCommonFeature;
import gama.ui.diagram.metamodel.GamaFactory;
import gama.ui.diagram.swt.image.GamaImageProvider;

/** Real SWT/Graphiti regression for legacy cropped icons and proportional image fitting. */
public final class IconLayoutTest {
	public static void main(final String[] args) {
		final var display = new Display();
		try {
			final var ga = Graphiti.getGaService();
			final var pe = Graphiti.getPeCreateService();
			final var diagram = pe.createDiagram("test", "icons");
			final var species = GamaFactory.eINSTANCE.createESpecies();
			species.setName("Species");
			final ContainerShape shape = pe.createContainerShape(diagram, true);
			ga.createRoundedRectangle(shape, 12, 12);
			ga.setLocationAndSize(shape.getGraphicsAlgorithm(), 37, 49, 180, 60);
			final var icon = ga.createImage(pe.createShape(shape, false), "test");
			ga.setLocationAndSize(icon, -70, 3, 30, 15);
			if (LayoutCommonFeature.hasCurrentIconSize(icon)) throw new AssertionError("Legacy icon not detected");
			for (final boolean hasAttributes : new boolean[] { false, true }) {
				if (hasAttributes) {
					final var variable = GamaFactory.eINSTANCE.createEVariable();
					variable.setName("value");
					variable.setType("int");
					species.getVariables().add(variable);
				}
				LayoutCommonFeature.layout(shape, diagram, species, false);
				if (!LayoutCommonFeature.hasCurrentIconSize(icon)) throw new AssertionError("Icon not migrated");
				if (!(GamaImageProvider.IMG_SUBSPECIESLINK + ".dark").equals(icon.getId())) {
					throw new AssertionError("Node did not switch to its dark-theme icon");
				}
				if (icon.getX() < 0 || icon.getY() < 0
						|| icon.getX() + icon.getWidth() > shape.getGraphicsAlgorithm().getWidth()
						|| icon.getY() + icon.getHeight() > shape.getGraphicsAlgorithm().getHeight()) {
					throw new AssertionError("Icon outside node");
				}
				if (shape.getGraphicsAlgorithm().getX() != 37 || shape.getGraphicsAlgorithm().getY() != 49) {
					throw new AssertionError("Migration moved node");
				}
				if (LayoutCommonFeature.layout(shape, diagram, species, false)) {
					throw new AssertionError("Layout not idempotent");
				}
			}
			// Exercise the actual Graphiti renderer with square and non-square source images.
			for (final int[] size : new int[][] { { 32, 32 }, { 48, 24 }, { 24, 48 } }) {
				final var source = new org.eclipse.swt.graphics.Image(display, size[0], size[1]);
				final var figure = new GFImageFigure(icon);
				try {
					figure.setImage(source);
					final var bounds = figure.getImage().getBounds();
					if (bounds.width > icon.getWidth() || bounds.height > icon.getHeight()
							|| bounds.width * size[1] != bounds.height * size[0]) {
						throw new AssertionError("Graphiti clips or distorts image");
					}
					final var classNode = GamaFactory.eINSTANCE.createEClassSpecies();
					classNode.setName("Class");
					classNode.getColorPicto().addAll(gama.ui.diagram.features.add.ColorDisplay.NODE_CLASS);
					LayoutCommonFeature.layout(shape, diagram, classNode, false);
					if (!(GamaImageProvider.IMG_CLASS + ".dark").equals(icon.getId())
							|| shape.getGraphicsAlgorithm().getBackground().getRed() != 237
							|| shape.getGraphicsAlgorithm().getBackground().getGreen() != 231
							|| shape.getGraphicsAlgorithm().getBackground().getBlue() != 247) {
						throw new AssertionError("Class layout lost its icon/lavender color");
					}
				} finally {
					figure.setImage(null);
					source.dispose();
				}
			}
			System.out.println("PASS: legacy icon migration, node bounds, idempotence and proportional Graphiti scaling");
		} finally {
			display.dispose();
		}
	}
}
