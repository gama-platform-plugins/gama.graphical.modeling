package gama.ui.diagram.features.modelgeneration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.graphics.ImageLoader;

import gama.ui.diagram.features.add.ColorDisplay;
import gama.ui.diagram.swt.image.GamaImageProvider;

/** Pass the gama.ui.diagram/icons/palette directory; checks palette section circles follow the node colors. */
public final class PaletteCircleTest {
	public static void main(final String[] args) throws Exception {
		final Path directory = Path.of(args[0]);
		final Map<String, List<Integer>> fills = Map.of("neutral", ColorDisplay.NODE_NEUTRAL, "green",
				ColorDisplay.NODE_GREEN, "blue", ColorDisplay.NODE_BLUE, "pink", ColorDisplay.NODE_PINK, "orange",
				ColorDisplay.NODE_ORANGE, "class", ColorDisplay.NODE_CLASS);
		int checks = 0;
		for (final String circle : GamaImageProvider.SECTION_CIRCLES) {
			final List<Integer> fill = fills.get(circle);
			if (fill == null) throw new AssertionError("No reference color for circle " + circle);
			final Path file = directory.resolve(circle + ".svg");
			final String svg = Files.readString(file);
			final var stroke = ColorDisplay.nodeTextColor(fill);
			if (!svg.contains("<circle") || !svg.contains("fill=\"#" + hex(fill.get(0), fill.get(1), fill.get(2)) + "\"")
					|| !svg.contains("stroke=\"#" + hex(stroke.getRed(), stroke.getGreen(), stroke.getBlue()) + "\"")) {
				throw new AssertionError("Palette circle colors diverge from diagram colors: " + circle);
			}
			try (final var stream = Files.newInputStream(file)) {
				final var images = new ImageLoader().load(stream);
				if (images.length != 1 || images[0].width != 16 || images[0].height != 16) {
					throw new AssertionError("Cannot decode 16px palette circle: " + circle);
				}
			}
			if (!(GamaImageProvider.class.getField("IMG_SECTION_" + circle.toUpperCase()).get(null))
					.equals("gama.ui.diagram.palette.section." + circle)) {
				throw new AssertionError("Wrong section image id for " + circle);
			}
			checks += 3;
		}
		System.out.println("PASS: " + checks + " palette section circle color, decoding and id checks");
	}

	private static String hex(final int red, final int green, final int blue) {
		return String.format("%02X%02X%02X", red, green, blue);
	}
}
