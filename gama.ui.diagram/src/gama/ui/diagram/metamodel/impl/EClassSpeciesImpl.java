package gama.ui.diagram.metamodel.impl;

import org.eclipse.emf.ecore.EClass;

import gama.ui.diagram.metamodel.EClassSpecies;
import gama.ui.diagram.metamodel.GamaPackage;

public class EClassSpeciesImpl extends ESpeciesImpl implements EClassSpecies {
	@Override
	protected EClass eStaticClass() {
		return GamaPackage.Literals.ECLASS_SPECIES;
	}
}
