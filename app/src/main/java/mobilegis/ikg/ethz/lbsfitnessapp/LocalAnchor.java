package mobilegis.ikg.ethz.lbsfitnessapp;

import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;

/**
 * This class is LocalAnchor, which is used to render anchor from local anchorID.
 *
 * @author Yuanwen Yue, Master student at ETH Zürich.
 */
public class LocalAnchor {
    private String anchorID;
    private String modelName;
    private String modelTitle;
    private Renderable model;
    private ViewRenderable modelTitleView;

    public String getAnchorID() {
        return anchorID;
    }

    public void setAnchorID(String anchorID) {
        this.anchorID = anchorID;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelTitle() {
        return modelTitle;
    }

    public void setModelTitle(String modelTitle) {
        this.modelTitle = modelTitle;
    }

    public Renderable getModel() {
        return model;
    }

    public void setModel(Renderable model) {
        this.model = model;
    }

    public ViewRenderable getModelTitleView() {
        return modelTitleView;
    }

    public void setModelTitleView(ViewRenderable modelTitleView) {
        this.modelTitleView = modelTitleView;
    }
}
