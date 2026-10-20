package gama.ui.diagram.editor;

import java.util.List;

import org.eclipse.gef.palette.MarqueeToolEntry;
import org.eclipse.gef.palette.PaletteGroup;
import org.eclipse.gef.palette.PaletteRoot;
import org.eclipse.gef.palette.PanningSelectionToolEntry;
import org.eclipse.gef.ui.palette.FlyoutPaletteComposite;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;

/** Native SWT title rendering, GEF title compatibility and selection-tool preservation. */
public final class PalettePresentationTest {
	private static int checks;

	public static void main(final String[] args) throws Exception {
		final Display display = new Display();
		final Shell shell = new Shell(display);
		try {
			new org.eclipse.gef.internal.InternalGEFPlugin();
			final var root = new PaletteRoot();
			final var group = new PaletteGroup("Palette");
			final var single = new PanningSelectionToolEntry();
			final var multiple = new MarqueeToolEntry();
			group.add(single);
			group.add(multiple);
			root.add(group);
			root.setDefaultEntry(single);
			final var singleIcon = single.getSmallIcon();
			final var multipleIcon = multiple.getSmallIcon();
			final var singleTool = single.createTool().getClass();
			final var multipleTool = multiple.createTool().getClass();
			final var behavior = new GamaPaletteBehavior(null) {
				@Override
				protected PaletteRoot createPaletteRoot() { return root; }
			};
			behavior.getPaletteRoot();
			check("Selection".equals(group.getLabel()), "Selection group is not renamed");
			check("Single selection".equals(single.getLabel()), "Single selection label missing");
			check("Multiple selection".equals(multiple.getLabel()), "Multiple selection label missing");
			check(root.getDefaultEntry() == single, "Default tool changed");
			check(single.createTool().getClass() == singleTool && multiple.createTool().getClass() == multipleTool,
					"Selection tool implementations changed");
			check(single.getSmallIcon() == singleIcon && multiple.getSmallIcon() == multipleIcon, "Tool icons changed");
			group.setLabel("Palette");
			single.setLabel("Select");
			multiple.setLabel("Marquee");
			behavior.refreshPalette();
			check("Selection".equals(group.getLabel()) && "Single selection".equals(single.getLabel())
					&& "Multiple selection".equals(multiple.getLabel()), "Refresh lost selection labels");

			final Class<?> titleType = Class.forName(FlyoutPaletteComposite.class.getName() + "$TitleCanvas",
					false, FlyoutPaletteComposite.class.getClassLoader());
			check(Canvas.class.isAssignableFrom(titleType), "GEF no longer uses a title canvas");
			final var canvas = new Canvas(shell, SWT.NONE);
			canvas.addListener(SWT.Paint, event -> {
				throw new AssertionError("Original Palette title/icon paint handler is still active");
			});
			final int[] events = new int[2];
			canvas.addListener(SWT.MouseDown, event -> events[0]++);
			canvas.addListener(SWT.DragDetect, event -> events[1]++);
			GamaPaletteComposite.configureTitle(canvas);
			check("Selection".equals(canvas.getToolTipText()), "Title tooltip is still Palette");
			check(canvas.getListeners(SWT.Paint).length == 1, "Original Palette icon/text still painted");
			check(canvas.getListeners(SWT.MouseDown).length > 0 && canvas.getListeners(SWT.DragDetect).length > 0,
					"Title lost its drag/dock handlers");
			canvas.notifyListeners(SWT.MouseDown, new Event());
			canvas.notifyListeners(SWT.DragDetect, new Event());
			check(events[0] == 1 && events[1] == 1, "Title interaction listeners were replaced");
			for (final int width : List.of(120, 200)) {
				canvas.setSize(width, 28);
				final Image actual = new Image(display, width, 28);
				final Image expected = new Image(display, width, 28);
				final GC actualGC = new GC(actual);
				final GC expectedGC = new GC(expected);
				try {
					final Event paint = new Event();
					paint.gc = actualGC;
					canvas.notifyListeners(SWT.Paint, paint);
					expectedGC.setBackground(display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
					expectedGC.fillRectangle(0, 0, width, 28);
					expectedGC.setForeground(display.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND));
					expectedGC.setFont(canvas.getFont());
					expectedGC.drawText("Selection", 4, (28 - expectedGC.textExtent("Selection").y) / 2, true);
					final var rendered = actual.getImageData();
					final var reference = expected.getImageData();
					for (int y = 0; y < 28; y++) {
						for (int x = 0; x < width; x++) {
							if (rendered.getPixel(x, y) != reference.getPixel(x, y)) {
								throw new AssertionError("Title is not plain Selection text at " + x + "," + y);
							}
						}
					}
					checks++;
				} finally {
					actualGC.dispose();
					expectedGC.dispose();
					actual.dispose();
					expected.dispose();
				}
			}
			behavior.dispose();
			System.out.println("PASS: " + checks + " native palette title, selection labels and tool preservation checks");
		} finally {
			shell.dispose();
			display.dispose();
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) throw new AssertionError(message);
		checks++;
	}
}
