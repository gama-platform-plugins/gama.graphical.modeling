package gama.ui.diagram.editor;

import org.eclipse.gef.ui.palette.FlyoutPaletteComposite;
import org.eclipse.gef.ui.palette.PaletteViewerProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.ui.IWorkbenchPage;

final class GamaPaletteComposite extends FlyoutPaletteComposite {
	GamaPaletteComposite(final Composite parent, final IWorkbenchPage page,
			final PaletteViewerProvider provider, final FlyoutPreferences preferences) {
		super(parent, SWT.NONE, page, provider, preferences);
		if (configureTitles(this) == 0) {
			throw new IllegalStateException("Unsupported GEF flyout palette: missing title canvas");
		}
	}

	private static int configureTitles(final Composite parent) {
		int titles = 0;
		for (final Control child : parent.getChildren()) {
			// GEF exposes no title API. Replace only its title painting, retaining drag, dock and focus listeners.
			if (child instanceof Canvas canvas
					&& child.getClass().getName().equals(FlyoutPaletteComposite.class.getName() + "$TitleCanvas")) {
				configureTitle(canvas);
				titles++;
			} else if (child instanceof Composite composite) {
				titles += configureTitles(composite);
			}
		}
		return titles;
	}

	static void configureTitle(final Canvas canvas) {
		for (final var listener : canvas.getListeners(SWT.Paint)) {
			canvas.removeListener(SWT.Paint, listener);
		}
		canvas.addListener(SWT.Paint, event -> {
			final var area = canvas.getClientArea();
			event.gc.setBackground(canvas.getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
			event.gc.fillRectangle(area);
			event.gc.setForeground(canvas.getDisplay().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND));
			event.gc.setFont(canvas.getFont());
			final int height = event.gc.textExtent("Selection").y;
			event.gc.drawText("Selection", 4, Math.max(0, (area.height - height) / 2), true);
		});
		canvas.setToolTipText("Selection");
		canvas.getAccessible().addAccessibleListener(new AccessibleAdapter() {
			@Override
			public void getName(final AccessibleEvent event) { event.result = "Selection"; }

			@Override
			public void getDescription(final AccessibleEvent event) { event.result = "Selection tools"; }
		});
	}
}
