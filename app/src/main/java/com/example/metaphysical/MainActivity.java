package com.example.metaphysical;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MainActivity extends AppCompatActivity {
    private ArFragment fragment;
    private PointerDrawable pointer = new PointerDrawable();
    private boolean isTracking;
    private boolean isHitting;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        fragment = (ArFragment)
                getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);
        fragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
            fragment.onUpdate(frameTime);
            onUpdate();
        });
        initializeGallery();
    }


    /**
     * This method updates the tracking state.
     * Detects when user hits a plane and enables the pointer accordingly
     */
    private void onUpdate() {
        boolean trackingChanged = updateTracking();
        View contentView = findViewById(android.R.id.content);
        if (trackingChanged) {
            if (isTracking) {
                contentView.getOverlay().add(pointer);
            } else {
                contentView.getOverlay().remove(pointer);
            }
            contentView.invalidate();
        }
        if (isTracking) {
            boolean hitTestChanged = updateHitTest();
            if (hitTestChanged) {
                pointer.setEnabled(isHitting);
                contentView.invalidate();
            }
        }
    }

    /**
     * This method returns true if the tracking state has changed
     * Determined by the ARCore's camera state.
     *
     * @return
     */
    private boolean updateTracking() {
        Frame frame = fragment.getArSceneView().getArFrame();
        boolean wasTracking = isTracking;
        isTracking = frame != null &&
                frame.getCamera().getTrackingState() == TrackingState.TRACKING;
        return isTracking != wasTracking;
    }

    /**
     * This method calls Frame.hitTest() upon detecting a hit.
     * Also calls getScreenCenter() as we need the center of the screen.
     *
     * @return
     */
    private boolean updateHitTest() {
        Frame frame = fragment.getArSceneView().getArFrame();
        android.graphics.Point pt = getScreenCenter();
        List<HitResult> hits;
        boolean wasHitting = isHitting;
        isHitting = false;
        if (frame != null) {
            hits = frame.hitTest(pt.x, pt.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane &&
                        ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    isHitting = true;
                    break;
                }
            }
        }
        return wasHitting != isHitting;
    }

    private android.graphics.Point getScreenCenter() {
        View vw = findViewById(android.R.id.content);
        return new android.graphics.Point(vw.getWidth() / 2, vw.getHeight() / 2);
    }


    private void initializeGallery() {
        LinearLayout gallery = findViewById(R.id.gallery_layout);
        ImageView andy = new ImageView(this);
        andy.setImageResource(R.drawable.droid_thumb);
        andy.setContentDescription("andy");
        andy.setOnClickListener(view -> addsfbObject(Uri.parse("andy.sfb")));
        gallery.addView(andy);

        ImageView cabin = new ImageView(this);
        cabin.setImageResource(R.drawable.cabin_thumb);
        cabin.setContentDescription("Cabin");
        cabin.setOnClickListener(view -> addsfbObject(Uri.parse("Cabin.sfb")));
        gallery.addView(cabin);

        ImageView house = new ImageView(this);
        house.setImageResource(R.drawable.house_thumb);
        house.setContentDescription("house");
        house.setOnClickListener(view -> addsfbObject(Uri.parse("House.sfb")));
        gallery.addView(house);

        ImageView igloo = new ImageView(this);
        igloo.setImageResource(R.drawable.igloo_thumb);
        igloo.setContentDescription("igloo");
        igloo.setOnClickListener(view -> addsfbObject(Uri.parse("igloo.sfb")));
        gallery.addView(igloo);

        ImageView morty = new ImageView(this);
        morty.setImageResource(R.drawable.morty_thumb);
        morty.setContentDescription("morty");
        morty.setOnClickListener(view -> addsfbObject(Uri.parse("morty.sfb")));
        gallery.addView(morty);

    }

    /**
     * This method is called when the thumbnail is click.
     * Determines where the sfb object should be placed by performing hittest.
     * Then calles the placeObject method to place the object.
     *
     * @param model the sfb Object
     */
    private void addsfbObject(Uri model) {
        Frame frame = fragment.getArSceneView().getArFrame();
        android.graphics.Point pt = getScreenCenter();
        List<HitResult> hits;
        if (frame != null) {
            hits = frame.hitTest(pt.x, pt.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane &&
                        ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    placeObject(fragment, hit.createAnchor(), model);
                    break;
                }
            }
        }

    }


    /**
     * This method uses the ARCore anchor from the hitTest & builds Sceneform nodes
     * Performs asynchronous loading of the 3D model using the ModelRenderable builder.
     * Once the model is loaded as a Renderable, call addNodeToScene
     *
     * @param fragment
     * @param anchor
     * @param model
     */

    private void placeObject(ArFragment fragment, Anchor anchor, Uri model) {
        CompletableFuture<Void> completableFuture =
                ModelRenderable.builder()
                        .setSource(fragment.getContext(), model)
                        .build()
                        .thenAccept(renderable -> addNodeToScene(fragment, anchor, renderable))
                        .exceptionally((throwable -> {
                            AlertDialog.Builder builder = new AlertDialog.Builder(this);
                            builder.setMessage(throwable.getMessage())
                                    .setTitle("Error Rendering Model!");
                            AlertDialog dialog = builder.create();
                            dialog.show();
                            return null;
                        }));

    }

    /**
     * This method builds two nodes and attaches to the sfb Object
     * First node is of type AnchorNode(based on the pose of the sfb object, they stay positioned in the smaple place relative to the real world)
     * Second node is a TransformableNode(Handles the interaction of moving, scaling, and rotation based on user gestures)
     * Upon nodes being connected to each other, connect Anchornode to the scene and select a node for interactions.
     *
     * @param fragment
     * @param anchor
     * @param renderable
     */

    private void addNodeToScene(ArFragment fragment, Anchor anchor, ModelRenderable renderable) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        TransformableNode node = new TransformableNode(fragment.getTransformationSystem());
        node.setRenderable(renderable);
        node.setParent(anchorNode);
        fragment.getArSceneView().getScene().addChild(anchorNode);
        node.select();

    }

}

