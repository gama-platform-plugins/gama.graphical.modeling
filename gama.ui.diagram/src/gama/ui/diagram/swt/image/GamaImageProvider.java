package gama.ui.diagram.swt.image;

import org.eclipse.core.runtime.Platform;
import org.eclipse.graphiti.ui.platform.AbstractImageProvider;

import gama.ui.diagram.metamodel.EAction;
import gama.ui.diagram.metamodel.EAspect;
import gama.ui.diagram.metamodel.EBatchExperiment;
import gama.ui.diagram.metamodel.EDisplay;
import gama.ui.diagram.metamodel.EEquation;
import gama.ui.diagram.metamodel.EGUIExperiment;
import gama.ui.diagram.metamodel.EGamaObject;
import gama.ui.diagram.metamodel.EGrid;
import gama.ui.diagram.metamodel.EPerceive;
import gama.ui.diagram.metamodel.EPlan;
import gama.ui.diagram.metamodel.EReflex;
import gama.ui.diagram.metamodel.ERule;
import gama.ui.diagram.metamodel.ESpecies;
import gama.ui.diagram.metamodel.EClassSpecies;
import gama.ui.diagram.metamodel.EState;
import gama.ui.diagram.metamodel.ETask;
import gama.ui.diagram.metamodel.EWorldAgent;

public class GamaImageProvider extends AbstractImageProvider {
 
    // The prefix for all identifiers of this image provider
    protected static final String PREFIX = 
              "gama.ui.diagram.";
 
    public static final String IMG_SUBSPECIESLINK= PREFIX + "subspecieslink";
    public static final String IMG_CLASS = PREFIX + "class";
    public static final String IMG_SUBGRIDLINK= PREFIX + "subgridlink";
    public static final String IMG_ACTIONLINK= PREFIX + "actionlink";
    public static final String IMG_ASPECTLINK= PREFIX + "aspectlink";
    public static final String IMG_REFLEXLINK= PREFIX + "reflexlink";
    public static final String IMG_GUIXPLINK= PREFIX + "guixplink";
    public static final String IMG_BATCHXPLINK= PREFIX + "batchxplink";
    public static final String IMG_DISPLAYLINK= PREFIX + "displaylink";
    public static final String IMG_INHERITINGLINK= PREFIX + "inheritinglink";
    public static final String IMG_EQUATION= PREFIX + "equationlink";
    public static final String IMG_PERCEIPT= PREFIX + "perceiptlink";
    public static final String IMG_WORLD = PREFIX + "world";
    public static final String IMG_STATE = PREFIX + "state";
    public static final String IMG_TASK = PREFIX + "task";
    public static final String IMG_PLAN = PREFIX + "plan";
    public static final String IMG_RULE = PREFIX + "rule";
    public static final String IMG_PALETTE_FOLDER = PREFIX + "palette.folder";
 
    
    @Override
    protected void addAvailableImages() {
        final var bundle = Platform.getBundle("gama.ui.shared");
        if (bundle == null) throw new IllegalStateException("Missing GAMA shared icon bundle");
        final var folder = bundle.getEntry("icons_svg/light/navigator/files/folder.model.svg");
        if (folder == null) throw new IllegalStateException("Missing GAMA model-folder icon");
        addImageFilePath(IMG_PALETTE_FOLDER, folder.toExternalForm());
        // register the path for each image identifier
        addOutlineImage(IMG_WORLD, "model");
        addOutlineImage(IMG_SUBSPECIESLINK, "species");
        addOutlineImage(IMG_CLASS, "class");
        addOutlineImage(IMG_SUBGRIDLINK, "grid");
        addOutlineImage(IMG_ACTIONLINK, "action");
        addOutlineImage(IMG_ASPECTLINK, "aspect");
        addOutlineImage(IMG_REFLEXLINK, "reflex");
        addOutlineImage(IMG_GUIXPLINK, "gui");
        addOutlineImage(IMG_BATCHXPLINK, "batch");
        addOutlineImage(IMG_DISPLAYLINK, "display");
        addOutlineImage(IMG_INHERITINGLINK, "hierarchy");
        addOutlineImage(IMG_EQUATION, "equation");
        addOutlineImage(IMG_PERCEIPT, "perceive");
        addOutlineImage(IMG_STATE, "state");
        addOutlineImage(IMG_TASK, "task");
        addOutlineImage(IMG_PLAN, "plan");
        addOutlineImage(IMG_RULE, "rule");
    }

    private void addOutlineImage(final String id, final String keyword) {
        addOutlineImage(id, keyword, "light");
        addOutlineImage(id + ".dark", keyword, "dark");
    }

    private void addOutlineImage(final String id, final String keyword, final String theme) {
        final var bundle = Platform.getBundle("gama.ui.shared");
        if (bundle == null) throw new IllegalStateException("Missing GAMA shared icon bundle");
        final String directory = "icons_svg/" + theme + "/gaml/_";
        var url = bundle.getEntry(directory + keyword + ".svg");
        if (url == null) url = bundle.getEntry(directory + "unknown.svg");
        if (url == null) throw new IllegalStateException("Missing GAMA " + theme + " outline icon: " + keyword);
        addImageFilePath(id, url.toExternalForm());
    }

    public static String nodeImageId(final EGamaObject object) {
        final String id = semanticImageId(object);
        return id == null ? null : id + ".dark";
    }

    private static String semanticImageId(final EGamaObject object) {
        if (object instanceof EWorldAgent) return IMG_WORLD;
        if (object instanceof EGrid) return IMG_SUBGRIDLINK;
        if (object instanceof EClassSpecies) return IMG_CLASS;
        if (object instanceof EGUIExperiment) return IMG_GUIXPLINK;
        if (object instanceof EBatchExperiment) return IMG_BATCHXPLINK;
        if (object instanceof ESpecies) return IMG_SUBSPECIESLINK;
        if (object instanceof EAction) return IMG_ACTIONLINK;
        if (object instanceof EAspect) return IMG_ASPECTLINK;
        if (object instanceof EReflex) return IMG_REFLEXLINK;
        if (object instanceof EDisplay) return IMG_DISPLAYLINK;
        if (object instanceof EEquation) return IMG_EQUATION;
        if (object instanceof EPerceive) return IMG_PERCEIPT;
        if (object instanceof EState) return IMG_STATE;
        if (object instanceof ETask) return IMG_TASK;
        if (object instanceof EPlan) return IMG_PLAN;
        if (object instanceof ERule) return IMG_RULE;
        return null;
    }
}