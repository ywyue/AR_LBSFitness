package mobilegis.ikg.ethz.lbsfitnessapp;


import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchor;
import com.google.ar.sceneform.Node;

/**
 * This class is AnchorVisual which is used to:
 * 1. add model to anchor node
 * 2. add title to model node
 * 3. resize model and it's title (As different model has different size, we have to scale them to
 * similar level)
 *
 * @author Yuanwen Yue, Master student at ETH Zürich.
 */

class AnchorVisual {

    private final AnchorNode anchorNode;
    private TransformableNode transformableNode;
    private CloudSpatialAnchor cloudAnchor;

    public AnchorVisual(ArFragment arFragment, Anchor localAnchor) {
        anchorNode = new AnchorNode(localAnchor);

        transformableNode = new TransformableNode(arFragment.getTransformationSystem());
        transformableNode.getScaleController().setEnabled(false);
        transformableNode.getTranslationController().setEnabled(false);
        transformableNode.getRotationController().setEnabled(false);
        transformableNode.setParent(this.anchorNode);
    }

    public AnchorVisual(ArFragment arFragment, CloudSpatialAnchor cloudAnchor) {
        this(arFragment, cloudAnchor.getLocalAnchor());
        setCloudAnchor(cloudAnchor);
    }

    public AnchorNode getAnchorNode() {
        return this.anchorNode;
    }

    public CloudSpatialAnchor getCloudAnchor() {
        return this.cloudAnchor;
    }

    public Anchor getLocalAnchor() {
        return this.anchorNode.getAnchor();
    }

    public void render(ArFragment arFragment, Renderable model, ViewRenderable viewRenderable, String selectedReward) {
        MainThreadContext.runOnUiThread(() -> {
            if (model == null  ) {
                return;
            }

            // Create the transformable model and add it to the anchor.
            TransformableNode modelNode = new TransformableNode(arFragment.getTransformationSystem());
            Node titleNode = new Node();

            // Resize the model and it's title
            nodeResize(modelNode,titleNode,selectedReward);

            modelNode.setParent(anchorNode);
            modelNode.setRenderable(model);
            modelNode.getRenderableInstance().animate(true).start();
            modelNode.select();

            titleNode.setParent(modelNode);
            titleNode.setEnabled(false);

            titleNode.setRenderable(viewRenderable);
            titleNode.setEnabled(true);

            anchorNode.setParent(arFragment.getArSceneView().getScene());
        });
    }

    public void setCloudAnchor(CloudSpatialAnchor cloudAnchor) {
        this.cloudAnchor = cloudAnchor;
    }


    public void destroy() {
        MainThreadContext.runOnUiThread(() -> {
            anchorNode.setRenderable(null);
            anchorNode.setParent(null);
            Anchor localAnchor =  anchorNode.getAnchor();
            if (localAnchor != null) {
                anchorNode.setAnchor(null);
                localAnchor.detach();
            }
        });
    }

    public void nodeResize(TransformableNode modelNode, Node titleNode, String selectedReward) {
        float maxScale;
        float minScale;
        float localScale;
        switch(selectedReward) {
            case "Peach":
                maxScale = 0.03f;
                minScale = 0.02f;
                break;
            case "Watermelon":
                maxScale = 0.9f;
                minScale = 0.6f;
                break;
            case "Ice Cream":
                maxScale = 0.06f;
                minScale = 0.03f;
                break;
            case "Banana":
                maxScale = 0.006f;
                minScale = 0.003f;
                break;
            default:
                maxScale = 0.001f;
                minScale = 0.0005f;
                break;
        }
        localScale = 1/maxScale;

        // Scale the model.
        modelNode.getScaleController().setMaxScale(maxScale);
        modelNode.getScaleController().setMinScale(minScale);

        // Scale the title and adjust it's position.
        titleNode.setLocalPosition(new Vector3(0.0f, localScale/2, 0.0f));
        titleNode.setLocalScale(new Vector3(localScale, localScale, localScale));

    }


}
