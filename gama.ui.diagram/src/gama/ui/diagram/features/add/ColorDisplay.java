package gama.ui.diagram.features.add;

import java.util.List;
import java.util.Map;

import org.eclipse.graphiti.util.ColorConstant;
import org.eclipse.graphiti.util.IColorConstant;

public class ColorDisplay {
	public static final List<Integer> NODE_NEUTRAL = List.of(250, 244, 229);
	public static final List<Integer> NODE_GREEN = List.of(232, 239, 229);
	public static final List<Integer> NODE_BLUE = List.of(228, 240, 240);
	public static final List<Integer> NODE_PINK = List.of(249, 231, 228);
	public static final List<Integer> NODE_ORANGE = List.of(245, 234, 222);

	public static final IColorConstant CLASS_FOREGROUND_ERROR = new ColorConstant(220, 70, 80);
	public static final IColorConstant CLASS_FOREGROUND_DEFAULT = new ColorConstant(205, 211, 220);
	public static final IColorConstant RELATION_FOREGROUND = new ColorConstant(91, 119, 124);
	public static final IColorConstant RELATION_TEXT_FOREGROUND = new ColorConstant(55, 83, 88);
	public static final IColorConstant BLACK = new ColorConstant(39, 49, 66);

	private static final Map<List<Integer>, List<Integer>> LEGACY_NODE_COLORS = Map.ofEntries(
			Map.entry(List.of(255, 204, 153), NODE_ORANGE), Map.entry(List.of(255, 204, 150), NODE_ORANGE),
			Map.entry(List.of(152, 251, 152), NODE_GREEN), Map.entry(List.of(153, 204, 255), NODE_BLUE),
			Map.entry(List.of(127, 255, 0), NODE_BLUE), Map.entry(List.of(254, 196, 255), NODE_BLUE),
			Map.entry(List.of(135, 206, 250), NODE_BLUE), Map.entry(List.of(239, 255, 82), NODE_BLUE),
			Map.entry(List.of(230, 190, 12), NODE_GREEN), Map.entry(List.of(221, 160, 221), NODE_PINK),
			Map.entry(List.of(255, 180, 28), NODE_ORANGE), Map.entry(List.of(255, 255, 150), NODE_NEUTRAL),
			Map.entry(List.of(255, 225, 217), NODE_PINK), Map.entry(List.of(225, 255, 209), NODE_GREEN),
			Map.entry(List.of(255, 255, 210), NODE_NEUTRAL));

	public static IColorConstant nodeTextColor(final List<Integer> background) {
		if (background.isEmpty()) return nodeTextColor(NODE_NEUTRAL);
		if (background.equals(NODE_NEUTRAL)) return new ColorConstant(112, 77, 20);
		if (background.equals(NODE_GREEN)) return new ColorConstant(66, 91, 50);
		if (background.equals(NODE_BLUE)) return new ColorConstant(42, 94, 109);
		if (background.equals(NODE_PINK)) return new ColorConstant(148, 55, 43);
		if (background.equals(NODE_ORANGE)) return new ColorConstant(143, 69, 28);
		final int red = background.get(0);
		final int green = background.get(1);
		final int blue = background.get(2);
		final double luminance = luminance(red, green, blue);
		final boolean lightText = luminance < 0.179;
		final ColorConstant candidate;
		if (lightText) {
			candidate = new ColorConstant(red + (255 - red) * 75 / 100, green + (255 - green) * 75 / 100,
					blue + (255 - blue) * 75 / 100);
		} else {
			final int min = Math.min(red, Math.min(green, blue));
			final int max = Math.max(red, Math.max(green, blue));
			// Restore the hue from a pale custom fill instead of darkening its white component.
			candidate = max == min ? new ColorConstant(65, 65, 65)
					: new ColorConstant(30 + (red - min) * 100 / (max - min),
							30 + (green - min) * 100 / (max - min), 30 + (blue - min) * 100 / (max - min));
		}
		int r = candidate.getRed(), g = candidate.getGreen(), b = candidate.getBlue();
		while ((Math.max(luminance, luminance(r, g, b)) + 0.05)
				/ (Math.min(luminance, luminance(r, g, b)) + 0.05) < 4.5) {
			r = lightText ? r + (255 - r + 4) / 5 : r * 4 / 5;
			g = lightText ? g + (255 - g + 4) / 5 : g * 4 / 5;
			b = lightText ? b + (255 - b + 4) / 5 : b * 4 / 5;
		}
		return new ColorConstant(r, g, b);
	}

	private static double luminance(final int red, final int green, final int blue) {
		return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue);
	}

	private static double linear(final int channel) {
		final double value = channel / 255.0;
		return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
	}

	public static List<Integer> updatedNodeColor(final List<Integer> color) {
		if (color.isEmpty()) return NODE_NEUTRAL;
		return LEGACY_NODE_COLORS.getOrDefault(color, color);
	}

	public static void updateLegacyNodeColor(final List<Integer> color) {
		final List<Integer> replacement = updatedNodeColor(color);
		if (color.isEmpty() || !replacement.equals(color)) {
			color.clear();
			color.addAll(replacement);
		}
	}

	public static void applyDefaultColor(final List<Integer> currentColor, final List<Integer> previousDefault,
			final List<Integer> newDefault) {
		if ( currentColor.isEmpty() || currentColor.equals(previousDefault) ) {
			currentColor.clear();
			currentColor.addAll(newDefault);
		}
	}

}
