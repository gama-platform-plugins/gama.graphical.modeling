package gama.ui.diagram.editor;

import java.lang.reflect.Proxy;
import java.util.List;

import org.eclipse.gef.EditPartViewer;
import org.eclipse.gef.ui.actions.ActionRegistry;
import org.eclipse.graphiti.dt.IDiagramTypeProvider;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.mm.pictograms.PictogramsFactory;
import org.eclipse.graphiti.ui.editor.DiagramBehavior;
import org.eclipse.graphiti.ui.editor.IDiagramContainerUI;
import org.eclipse.graphiti.ui.internal.action.SaveImageAction;
import org.eclipse.graphiti.ui.internal.action.UpdateAction;
import org.eclipse.graphiti.ui.platform.IConfigurationProvider;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.ui.actions.ActionFactory;

/** Checks the closed menu order without starting a workbench. */
public final class GamaDiagramContextMenuTest {
	public static void main(final String[] args) {
		final var registry = new ActionRegistry();
		final var undo = action(registry, ActionFactory.UNDO.getId(), "Undo", false);
		final var delete = action(registry, ActionFactory.DELETE.getId(), "Delete", true);
		final var update = action(registry, UpdateAction.ACTION_ID, "Update", false);
		final var exportImage = action(registry, SaveImageAction.ACTION_ID, "Export Diagram...", true);
		action(registry, ActionFactory.REDO.getId(), "Redo", true);
		action(registry, ActionFactory.COPY.getId(), "Copy", true);
		final var diagram = PictogramsFactory.eINSTANCE.createDiagram();
		final var typeProvider = proxy(IDiagramTypeProvider.class, (method, arguments) -> switch (method) {
			case "getDiagram" -> diagram;
			default -> throw new AssertionError(method);
		});
		final var features = proxy(IFeatureProvider.class, (method, arguments) -> switch (method) {
			case "getDiagramTypeProvider" -> typeProvider;
			default -> throw new AssertionError(method);
		});
		final var behavior = new DiagramBehavior(proxy(IDiagramContainerUI.class, (method, arguments) -> {
			throw new AssertionError(method);
		})) {
			@Override
			public PictogramElement[] getSelectedPictogramElements() {
				return new PictogramElement[] { diagram };
			}
		};
		final var configuration = proxy(IConfigurationProvider.class, (method, arguments) -> switch (method) {
			case "getDiagramBehavior" -> behavior;
			case "getDiagramTypeProvider" -> proxy(IDiagramTypeProvider.class, (name, parameters) -> {
				if ("getFeatureProvider".equals(name)) return features;
				throw new AssertionError(name);
			});
			default -> throw new AssertionError(method);
		});
		final var viewer = proxy(EditPartViewer.class, (method, arguments) -> {
			throw new AssertionError(method);
		});
		final var provider = new GamaDiagramContextMenuProvider(viewer, registry, configuration);
		final var menu = new MenuManager();
		provider.buildContextMenu(menu);
		final var items = menu.getItems();
		final var labels = java.util.Arrays.stream(items).map(item -> item.isSeparator() ? "|"
				: ((ActionContributionItem) item).getAction().getText()).toList();
		check(labels.equals(List.of("Undo", "Delete", "|", "Update", "Layout", "|", "Export as GAML", "Export...")),
				"Unexpected menu: " + labels);
		check(((ActionContributionItem) items[0]).getAction() == undo, "Undo must use the registered action");
		check(((ActionContributionItem) items[1]).getAction() == delete, "Delete must use the registered action");
		check(((ActionContributionItem) items[3]).getAction() == update, "Update must use the registered action");
		check(((ActionContributionItem) items[7]).getAction() == exportImage, "Image export must keep its action");
		check(!items[0].isEnabled() && items[1].isEnabled() && !items[3].isEnabled(), "Lost action enablement");
		check(!((ActionContributionItem) items[4]).getAction().isEnabled(), "Empty diagram cannot be laid out");
		diagram.getChildren().add(PictogramsFactory.eINSTANCE.createShape());
		check(((ActionContributionItem) items[4]).getAction().isEnabled(), "Layout enablement must follow diagram");
		check(!new GamaDiagramBehavior(proxy(IDiagramContainerUI.class, (method, arguments) -> {
			throw new AssertionError(method);
		})).shouldRegisterContextMenu(), "Workbench additions must be disabled");
		menu.dispose();
		registry.dispose();
		System.out.println("PASS: exact diagram context menu, action identity/enablement, no workbench additions");
	}

	private static IAction action(final ActionRegistry registry, final String id, final String label,
			final boolean enabled) {
		final var action = new Action(label) {};
		action.setId(id);
		action.setEnabled(enabled);
		registry.registerAction(action);
		return action;
	}

	private interface Invocation {
		Object call(String method, Object[] arguments);
	}

	private static <T> T proxy(final Class<T> type, final Invocation invocation) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
				(proxy, method, arguments) -> invocation.call(method.getName(), arguments)));
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) throw new AssertionError(message);
	}
}
