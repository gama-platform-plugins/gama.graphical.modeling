package gama.ui.diagram.features.modelgeneration;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.ecore.EObject;

import gama.api.compilation.descriptions.IDescription;

/** Detached source identity for matching projections; never serialized into snapshots. */
public final class DiagramSourceReference extends AdapterImpl {

	private final List<String> key;

	private DiagramSourceReference(final List<String> key) {
		this.key = List.copyOf(key);
	}

	static void attach(final EObject target, final IDescription description) {
		final List<String> key = new ArrayList<>();
		final EObject source = description.getUnderlyingElement();
		key.add(source == null || source.eResource() == null ? "" : source.eResource().getURI().toString());
		for (IDescription current = description; current != null; current = current.getEnclosingDescription()) {
			key.add(current.getKeyword());
			key.add(current.getName() == null ? "" : current.getName());
		}
		target.eAdapters().add(new DiagramSourceReference(key));
	}

	public static List<String> key(final EObject object) {
		for (final var adapter : object.eAdapters()) {
			if (adapter instanceof DiagramSourceReference reference) return reference.key;
		}
		return List.of();
	}
}
