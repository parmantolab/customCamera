package org.geneanet.customcamera;

import android.content.Context;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer.DrawableContainerState;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import org.geneanet.customcamera.CameraPreview;
import org.geneanet.customcamera.ManagerCamera;
import org.geneanet.customcamera.BitmapUtils;
import org.geneanet.customcamera.SimpleOrientationListener;
import org.geneanet.customcamera.TmpFileUtils;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/** Activity to use customCamera. */
public class CameraActivity extends Activity {
  
  // Camera resource.
  private Camera customCamera = null;
  // Distance between fingers for the zoom.
  private static float distanceBetweenFingers;
  // Enable miniature mode.
  private boolean modeMiniature = false;
  // The image in Bitmap format of the preview photo.
  private Boolean photoTaken = false;
  // Flag to active or disable opacity function.
  private Boolean opacity = true;
  // Flag to save state of flash -> 0 : off, 1 : on, 2 : auto. 
  private int stateFlash = 0;

  private int targetWidth = 0;// not resize if both 0
  private int targetHeight = 0;

  private Boolean zoom = true;

  public static final int DEGREE_0 = 0;
  public static final int DEGREE_90 = 90;
  public static final int DEGREE_180 = 180;
  public static final int DEGREE_270 = 270;
  
  public static final int FLASH_DISABLE = 0;
  public static final int FLASH_ENABLE = 1;
  public static final int FLASH_AUTO = 2;
  
  public static final int CAMERA_BACK = 0;
  public static final int CAMERA_FRONT = 1;
  
  public static final String NAME_FILE_BACKGROUND = "background";
  public static final String NAME_FILE_BACKGROUND_OTHER = "background-other";
  public static final String NAME_FILE_PICTURE_TAKEN = "picture-taken";

  private SimpleOrientationListener mOrientationListener;
  private boolean mDisableShutter = false;
  private int mOrientationTaken;
  private int mCurrentOrientation;

  /**
   * To get camera resource or stop this activity.
   * 
   * @param position The position of the camera.
   * 
   * @return boolean
   */
  protected boolean initCameraResource(Integer position) {
    if (position == null) {
      if (this.getIntent().getIntExtra("defaultCamera", CameraActivity.CAMERA_BACK) == CameraActivity.CAMERA_FRONT) {
        position = ManagerCamera.determinePositionFrontCamera();
      } else {
        position = ManagerCamera.determinePositionBackCamera();
      }
    }
    customCamera = ManagerCamera.getCameraInstance(position);
    
    if (customCamera == null) {
      this.setResult(2,
          new Intent().putExtra("errorMessage", "Camera is unavailable."));
      this.finish();

      return false;
    }

    ManagerCamera.setCameraDisplayOrientation(this);
    
    // The zoom bar progress
    final SeekBar zoomLevel = (SeekBar) findViewById(R.id.zoomLevel);
    Camera.Parameters paramsCamera = customCamera.getParameters();
    if (paramsCamera.isZoomSupported() && zoom) {
      final int zoom = paramsCamera.getZoom();
      int maxZoom = paramsCamera.getMaxZoom();
  
      // Event on change zoom with the bar.
      zoomLevel.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
        int progress = 0;
  
        @Override
        public void onProgressChanged(SeekBar seekBar, int progressValue,
            boolean fromUser) {
          Camera.Parameters paramsCamera = ManagerCamera.getCurrentCameraResource().getParameters();
          progress = progressValue;
          int newZoom = (int) (zoom + progress);
          zoomLevel.setProgress(newZoom);
          paramsCamera.setZoom(newZoom);
          ManagerCamera.getCurrentCameraResource().setParameters(paramsCamera);
        }
  
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }
  
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
      });
      
      zoomLevel.setMax(maxZoom);
      zoomLevel.setProgress(zoom);
      displayZoomLevel(View.VISIBLE);
    } else {
      displayZoomLevel(View.GONE);
    }
    
    updateStateFlash(stateFlash);
    manageDisplayButtons();
    
    return true;
  }
  
  /** Method onCreate. Handle the opacity seekBar and general configuration. */
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.i("9zai", "6.23");

    /* Remove title bar */
    this.requestWindowFeature(Window.FEATURE_NO_TITLE);
    /* Remove notification bar */
    this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN);

    setContentView(R.layout.activity_camera_view);

    opacity = this.getIntent().getBooleanExtra("opacity", true);
    stateFlash = this.getIntent().getIntExtra("defaultFlash", CameraActivity.FLASH_DISABLE);
    targetHeight = this.getIntent().getIntExtra("targetHeight",0);
    targetWidth = this.getIntent().getIntExtra("targetWidth",0);
    zoom = this.getIntent().getBooleanExtra("zoom", true);

    if (opacity) {
      // Event on change opacity.
      SeekBar switchOpacity = (SeekBar) findViewById(R.id.switchOpacity);
      switchOpacity.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
        int progress = 0;

        @Override
        public void onProgressChanged(SeekBar seekBar, int progressValue,
            boolean fromUser) {
          progress = progressValue;
          ImageView background = (ImageView) findViewById(R.id.background);
          float newOpacity = (float) (progress * 0.1);
          background.setAlpha(newOpacity);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
      });
    }

    ImageButton imgIcon = (ImageButton)findViewById(R.id.capture);
    final Activity currentActivity = this;
    
    String backgroundColor = this.getIntent().getStringExtra("cameraBackgroundColor");
    this.setCameraBackgroundColor(backgroundColor);
    this.setThumbAtSeekBar((SeekBar) findViewById(R.id.zoomLevel), backgroundColor);
    this.setThumbAtSeekBar((SeekBar) findViewById(R.id.switchOpacity), backgroundColor);
    
    imgIcon.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN:
            ((CameraActivity) currentActivity).setCameraBackgroundColor(
                    currentActivity.getIntent().getStringExtra("cameraBackgroundColorPressed"));
            break;
          case MotionEvent.ACTION_UP:
            view.performClick();
            ((CameraActivity) currentActivity).setCameraBackgroundColor(
                    currentActivity.getIntent().getStringExtra("cameraBackgroundColor"));
            ((CameraActivity) currentActivity).startTakePhoto();
            break;
          default:
            break;
        }
        return true;
      }
    });


    TextView orientWarning   = (TextView) findViewById(R.id.orientationWarning);
    LinearLayout keepPhoto   = (LinearLayout) findViewById(R.id.keepPhoto);

    orientWarning.setVisibility(View.GONE);
    keepPhoto.setVisibility(View.GONE);

    mDisableShutter = true;
    /**
     * Orientation listener
     */
    mOrientationListener = new SimpleOrientationListener(this) {

      @Override
      public void onSimpleOrientationChanged(int orientation) {
        Log.i("9zai", "orientation: " + orientation);
      }

      @Override
      public void onSimple4OrientationChanged(int orientation) {
        Log.i("9zai", "orientation4Way: " + orientation);

        mCurrentOrientation = orientation;
        manageDisplayButtonsOrientation();
      }
    };

    mOrientationListener.enable();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    mOrientationListener.disable();

  }

  /** Method onStart. Handle the zoom level seekBar and the camera orientation. */
  @Override
  protected void onStart() {
    super.onStart();
    
    setBackground();
    
    // Init camera resource.
    if (!initCameraResource(null)) {
      return;
    }

    // Adapt camera_preview to keep a ratio between screen' size and camera' size.
    DisplayMetrics dm = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(dm);
    
    FrameLayout cameraPreview = (FrameLayout) findViewById(R.id.camera_preview);
    RelativeLayout.LayoutParams paramsCameraPreview = 
        new RelativeLayout.LayoutParams(cameraPreview.getLayoutParams());
    
    Size camParametersSize = customCamera.getParameters().getPictureSize();
    
    int minSize = Math.min(camParametersSize.width, camParametersSize.height);
    int maxSize = Math.max(camParametersSize.width, camParametersSize.height);   
    int widthScreen = dm.widthPixels;
    int heightScreen = dm.heightPixels;
    int marginLeft = 0;
    int marginTop = 0;
    float ratioWidth, ratioHeight, sizeToResize, sizeToResizeMatchParent;
    if (widthScreen > heightScreen) {
      ratioWidth = ((float)maxSize / (float)widthScreen);
      ratioHeight = ((float)minSize / (float)heightScreen);
      sizeToResize = ratioWidth > ratioHeight ? minSize : maxSize;
      sizeToResizeMatchParent = ratioWidth > ratioHeight ? maxSize : minSize;
    } else {
      ratioWidth = ((float)minSize / (float)widthScreen);
      ratioHeight = ((float)maxSize / (float)heightScreen);
      sizeToResize = ratioWidth > ratioHeight ? maxSize : minSize;
      sizeToResizeMatchParent = ratioWidth > ratioHeight ? minSize : maxSize;
    }
    if (ratioWidth > ratioHeight) {
      paramsCameraPreview.height = (int)(sizeToResize / ratioWidth);
      paramsCameraPreview.width = (int)(sizeToResizeMatchParent / ratioWidth);
      marginTop = (int)(((float)(heightScreen - paramsCameraPreview.height)) / 2);
    } else {
      paramsCameraPreview.height = (int)(sizeToResizeMatchParent / ratioHeight);
      paramsCameraPreview.width = (int)(sizeToResize / ratioHeight);;
      marginLeft = (int)(((float)(widthScreen - paramsCameraPreview.width)) / 2);
    }
    paramsCameraPreview.setMargins(marginLeft, marginTop, 0, 0);
    cameraPreview.setLayoutParams(paramsCameraPreview);
    
    setPreviewSize();
    
    // Assign the render camera to the view
    CameraPreview myPreview = new CameraPreview(this, customCamera);
    cameraPreview.addView(myPreview);
    
    Log.i("9zai","lalala,on start");
    // Hide the switch camera button if the number of cameras is lower than 2.
    if(Camera.getNumberOfCameras() < 2){
      ImageButton switchCamera = (ImageButton) findViewById(R.id.switchCamera);
      switchCamera.setVisibility(View.GONE);
    }
  }
  
  /** To save some contains of the activity. */
  @Override
  protected void onSaveInstanceState(Bundle outState) {
    outState.putBoolean("modeMiniature", modeMiniature);
    outState.putBoolean("photoTaken", photoTaken);
    outState.putInt("stateFlash", stateFlash);
    super.onSaveInstanceState(outState);
  }

  /** To restore the contains saved on the method onSaveInstanceState(). */
  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    modeMiniature = savedInstanceState.getBoolean("modeMiniature");
    photoTaken = savedInstanceState.getBoolean("photoTaken");
    stateFlash = savedInstanceState.getInt("stateFlash");
    
    if (modeMiniature) {
      buttonMiniature(findViewById(R.id.miniature));
    }

    displayPicture();
    updateStateFlash(stateFlash);
    super.onRestoreInstanceState(savedInstanceState);
  }
  
  /** Method to pause the activity. */
  @Override
  protected void onPause() {
    super.onPause();
    ManagerCamera.clearCameraAccess();
    FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
    preview.removeAllViews();
  }
  
  /** 
   * Event on touch screen to call the manager of the zoom & the auto focus.
   * @return boolean
   */
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!photoTaken) {
      Camera.Parameters paramsCamera = customCamera.getParameters();
      int action = event.getAction();

      if (event.getPointerCount() > 1) {
        // If we touch with more than one finger
        if (action == MotionEvent.ACTION_POINTER_2_DOWN) {
          distanceBetweenFingers = getFingerSpacing(event);
        } else if (action == MotionEvent.ACTION_MOVE
            && paramsCamera.isZoomSupported()) {
          customCamera.cancelAutoFocus();
          handleZoom(event, paramsCamera, distanceBetweenFingers);
        }
      }
    }
    
    return true;
  }
  
  /**-------------------9zai
  * Handle volumn button event
  */
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event){
    if((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN|| keyCode == KeyEvent.KEYCODE_VOLUME_UP) && !mDisableShutter){
      startTakePhoto();
      return true;
    }
    return super.onKeyDown(keyCode,event);
  }
  
  /**
   * Set the background color of the camera button.
   * @param color The color of the background.
   */
  protected void setCameraBackgroundColor(String color) {
    ImageButton imgIcon = (ImageButton)findViewById(R.id.capture);
    GradientDrawable backgroundGradient = (GradientDrawable)imgIcon.getBackground();
    if (color.length() > 0) {
      try {
        int cameraBackgroundColor = Color.parseColor(color);
        backgroundGradient.setColor(cameraBackgroundColor);
      } catch (IllegalArgumentException e) {
        backgroundGradient.setColor(Color.TRANSPARENT);
      }
    } else {
      backgroundGradient.setColor(Color.TRANSPARENT);
    }
  }

  /**
   * Set thumb at a seekbar.
   * 
   * @param color
   */
  protected void setThumbAtSeekBar(SeekBar seekBar, String color) {
    int colorParsed = Color.parseColor(color);
    String colorAlpha = color.substring(1);
    colorAlpha = "#88"+colorAlpha;
    int colorAlphaParsed = Color.parseColor(colorAlpha);
	
    StateListDrawable selectorThumb;
    Resources res = getResources();
    try {
      selectorThumb = (StateListDrawable) Drawable.createFromXml(res, res.getXml(R.drawable.custom_thumb));
      DrawableContainerState thumbState = (DrawableContainerState) selectorThumb.getConstantState();
      GradientDrawable thumb = (GradientDrawable) thumbState.getChildren()[0];
      thumb.setColor(colorParsed);
      Resources r = getResources();
      int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, r.getDisplayMetrics());
      thumb.setStroke(px, colorAlphaParsed);
      seekBar.setThumb(thumb);
    } catch (NotFoundException e) {
      Log.e("customCamera", "Xml resource is not found.");
      e.printStackTrace();
    } catch (XmlPullParserException e) {
      Log.e("customCamera", "Xml can't be parsed.");
      e.printStackTrace();
    } catch (IOException e) {
      Log.e("customCamera", "Error to create the thumb");
      e.printStackTrace();
    }
  }
  
  /**
   * Determine the space between the first two fingers.
   * @param MotionEvent event Current event which start this calculation.
   * 
   * @return float
   */
  private float getFingerSpacing(MotionEvent event) {
    float coordX = event.getX(0) - event.getX(1);
    float coordY = event.getY(0) - event.getY(1);
    return (float) Math.sqrt(coordX * coordX + coordY * coordY);
  }

  /**
   * Manage the zoom.
   * 
   * @param MotionEvent event                  Current event which start this action.
   * @param Parameters  paramsCamera           Camera's parameter.
   * @param float       distanceBetweenFingers Distance between two fingers.
   */
  private void handleZoom(MotionEvent event, Camera.Parameters paramsCamera,
      float distanceBetweenFingers) {
    // take zoom max for the camera hardware.
    int maxZoom = paramsCamera.getMaxZoom();
    // current value for the zoom.
    int zoom = paramsCamera.getZoom();
    setZoomProgress(maxZoom, zoom);
    // new distance between fingers.
    float newDist = getFingerSpacing(event);

    if (newDist > distanceBetweenFingers) {
      // zoom in
      if (zoom < maxZoom) {
        zoom++;
      }
    } else if (newDist < distanceBetweenFingers) {
      // zoom out
      if (zoom > 0) {
        zoom--;
      }
    }
    
    paramsCamera.setZoom(zoom);
    customCamera.setParameters(paramsCamera);
  }

  /**
   * To set the seekBar zoom with the pinchZoom.
   * @param int maxZoom The max zoom of the device.
   * @param int zoom    The current zoom.
   */
  private void setZoomProgress(int maxZoom, int zoom) {
    SeekBar zoomLevel = (SeekBar) findViewById(R.id.zoomLevel);
    zoomLevel.setMax(maxZoom);
    zoomLevel.setProgress(zoom * 2);
    displayZoomLevel(View.VISIBLE);
  }

  /** To set background in the view. */
  protected void setBackground() {
    // Get the base64 picture for the background only if it's exist.
    byte[] imgBackgroundBase64 = null;
    File fileBackgroundOther = getFileStreamPath(NAME_FILE_BACKGROUND_OTHER);
    if (
      !fileBackgroundOther.exists() ||
      this.getIntent().getIntExtra("startOrientation", 1)
          == this.getResources().getConfiguration().orientation
    ) {
      imgBackgroundBase64 = TmpFileUtils.getTmpFileContent(this, NAME_FILE_BACKGROUND);
    } else {
      imgBackgroundBase64 = TmpFileUtils.getTmpFileContent(this, NAME_FILE_BACKGROUND_OTHER);
    }
    if (imgBackgroundBase64 != null) {
      ImageView background = (ImageView) findViewById(R.id.background);
      background.setImageBitmap(null);
      
      Bitmap imgBackgroundBitmap = BitmapUtils.generateOptimizeBitmap(this, imgBackgroundBase64);
      if (modeMiniature) {
        imgBackgroundBitmap = Bitmap.createScaledBitmap(imgBackgroundBitmap, imgBackgroundBitmap.getWidth() / 4, imgBackgroundBitmap.getHeight() / 4, true);
      }
      imgBackgroundBase64 = null;
      
      // set image at the view.
      background.setImageBitmap(imgBackgroundBitmap);
      imgBackgroundBitmap = null;
      // Opacity at the beginning
      if (opacity) {
        background.setAlpha((float)0.5);
      } else {
        background.setAlpha((float)1);
      }
      
      positioningBackground();
    }
  }
  
  /**
   * Resize the picture and change the icon of button.
   * @param view
   */
  public void buttonMiniature(View view) {
    ImageButton miniature = (ImageButton) view;

    setBackground();
    if (!modeMiniature) {
      miniature.setImageResource(R.drawable.minimise);
    } else {
      miniature.setImageResource(R.drawable.maximise);
    }
  }
  
  /**
   * Toggle the miniature function.
   * 
   * @param view
   */
  public void toggleMiniature(View view) {
    modeMiniature = !modeMiniature;
    buttonMiniature(view);
  }
  
  /**
   * To manage the display of the zoom bar.
   * @param displayStatus
   */
  public void displayZoomLevel(int displayStatus) {
    TextView textZoomMin = (TextView) findViewById(R.id.textZoomMin);
    TextView textZoomMax = (TextView) findViewById(R.id.textZoomMax);
    SeekBar zoomLevel = (SeekBar) findViewById(R.id.zoomLevel);

    if (!zoom) { displayStatus = View.GONE; }

    textZoomMin.setVisibility(displayStatus);
    textZoomMax.setVisibility(displayStatus);
    zoomLevel.setVisibility(displayStatus);

  }
  
  /**
   * Handle the position of the background
   */
  public void positioningBackground() {
    ImageView background = (ImageView) findViewById(R.id.background);
    RelativeLayout.LayoutParams paramsBackground = (RelativeLayout.LayoutParams) background.getLayoutParams();
    
    if (!modeMiniature) {
      paramsBackground.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
      paramsBackground.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
      paramsBackground.addRule(RelativeLayout.ALIGN_PARENT_LEFT, 0);
      paramsBackground.setMargins(0, 0, 0, 0);
    } else {
      // Position at the bottom
      paramsBackground.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
      // Position at the left
      paramsBackground.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
      
      if (photoTaken) {
        Resources res = getResources();
        
        int defaultPadding = (int)res.getDimension(R.dimen.default_padding);
        
        BitmapDrawable image = (BitmapDrawable) res.getDrawable(R.drawable.accept);
        int marginBottom = image.getBitmap().getHeight() + (defaultPadding * 2);
        paramsBackground.setMargins(0, 0, 0, marginBottom);
      } else {
        paramsBackground.setMargins(0, 0, 0, 0);
      }
    }
    
    background.setLayoutParams(paramsBackground);
  }
  
  /**
   * Manage to display buttons in function of picture is taken or not.
   */
  public void manageDisplayButtons() {
    LinearLayout keepPhoto   = (LinearLayout) findViewById(R.id.keepPhoto);
    ImageButton miniature    = (ImageButton) findViewById(R.id.miniature);
    ImageButton flash        = (ImageButton) findViewById(R.id.flash);
    ImageButton photo        = (ImageButton) findViewById(R.id.capture);
    ImageButton switchCamera = (ImageButton) findViewById(R.id.switchCamera);
    SeekBar switchOpacity    = (SeekBar) findViewById(R.id.switchOpacity);
    ImageView background     = (ImageView) findViewById(R.id.background);
    
    LayoutParams paramsLayoutMiniature = (LinearLayout.LayoutParams) miniature.getLayoutParams();
    Camera.Parameters paramsCamera = customCamera.getParameters();
    
    if (!this.getIntent().getBooleanExtra("miniature", true)) {
      miniature.setVisibility(View.GONE);
    }
    if (!opacity) {
      switchOpacity.setVisibility(View.GONE);
    }
    
    if (photoTaken) {
      // Show/hide elements when a photo is taken 
      keepPhoto.setVisibility(View.VISIBLE);  
      photo.setVisibility(View.GONE);   
      displayZoomLevel(View.GONE);
      flash.setVisibility(View.GONE);
      switchCamera.setVisibility(View.GONE);
      background.setVisibility(View.GONE);

      ((LinearLayout.LayoutParams) paramsLayoutMiniature).gravity = Gravity.TOP;
      miniature.setLayoutParams(paramsLayoutMiniature);
      
      if (modeMiniature) {
        positioningBackground();
      }
      
    } else {
      // Show/hide elements when a photo is not taken
      keepPhoto.setVisibility(View.GONE);
      photo.setVisibility(View.VISIBLE);
      background.setVisibility(View.VISIBLE);

      if (paramsCamera.isZoomSupported()) {
        displayZoomLevel(View.VISIBLE);
      }
      
      if (this.getIntent().getBooleanExtra("switchFlash", true) && hasFlash()) {
        flash.setVisibility(View.VISIBLE);
      } else {
        flash.setVisibility(View.GONE);
      }
      
      if (this.getIntent().getBooleanExtra("switchCamera", true)) {
        switchCamera.setVisibility(View.VISIBLE);
      } else {
        switchCamera.setVisibility(View.GONE);
      }
      
      ((LinearLayout.LayoutParams) paramsLayoutMiniature).gravity = Gravity.BOTTOM;
      miniature.setLayoutParams(paramsLayoutMiniature);
      
      if (modeMiniature) {
        positioningBackground();
      }

    }
  }

  private void manageDisplayButtonsOrientation() {
    LinearLayout keepPhoto   = (LinearLayout) findViewById(R.id.keepPhoto);
    ImageButton miniature    = (ImageButton) findViewById(R.id.miniature);
    ImageButton flash        = (ImageButton) findViewById(R.id.flash);
    ImageButton shutter      = (ImageButton) findViewById(R.id.capture);
    ImageButton switchCamera = (ImageButton) findViewById(R.id.switchCamera);
    SeekBar switchOpacity    = (SeekBar) findViewById(R.id.switchOpacity);
    ImageView background     = (ImageView) findViewById(R.id.background);
    TextView orientWarning   = (TextView) findViewById(R.id.orientationWarning);
    int angle = 0;

    if (!photoTaken) {
      if(mCurrentOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT || mCurrentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT){
        mDisableShutter = true;

        orientWarning.setVisibility(View.VISIBLE);
        background.setVisibility(View.INVISIBLE);
        shutter.setVisibility(View.INVISIBLE);

      } else if(mCurrentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || mCurrentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE){
        mDisableShutter = false;

        orientWarning.setVisibility(View.INVISIBLE);
        background.setVisibility(View.VISIBLE);
        shutter.setVisibility(View.VISIBLE);

        if (mCurrentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
          angle = 90;
        } else {
          angle = 270;
        }
      }

      shutter.setRotation(angle);
      flash.setRotation(angle);
      switchCamera.setRotation(angle);
    }
  }

  /**
   * Method to get the device default orientation.
   * 
   * @return int the device orientation.
   */
  public int getDeviceDefaultOrientation() {
    WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    Configuration config = getResources().getConfiguration();
    int rotation = windowManager.getDefaultDisplay().getRotation();
    
    if (
        (
            config.orientation == Configuration.ORIENTATION_LANDSCAPE 
            && (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180)
        ) || (
            config.orientation == Configuration.ORIENTATION_PORTRAIT
            && (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)
        )
    ) {
      return Configuration.ORIENTATION_LANDSCAPE;
    } else {
      return Configuration.ORIENTATION_PORTRAIT;
    }
  }

  /**
   * Start to take photo.
   */
  public void startTakePhoto() {
    ImageButton buttonCapture = (ImageButton)findViewById(R.id.capture);
    buttonCapture.setEnabled(false);
    setFlashMode();
    customCamera.autoFocus(new Camera.AutoFocusCallback() {
      @Override
      public void onAutoFocus(boolean bool, Camera camera) {
        takePhoto();
      }
    });
  }

  /**
   * Method to take picture.
   */
  public void takePhoto() {    
//    setRotationPictureTaken();
    final CameraActivity cameraActivityCurrent = this;

    mOrientationTaken = mCurrentOrientation;

    // Handles data for jpeg picture
    PictureCallback jpegCallback = new PictureCallback() {
      /**
       * Event when picture is taken.
       * @param byte[] data Picture with byte format.
       * @param Camera camera Current resource camera.
       */
      public void onPictureTaken(byte[] data, Camera camera) {
        // Preview from camera
        photoTaken = true;
        
        TmpFileUtils.createTmpFile(cameraActivityCurrent, NAME_FILE_PICTURE_TAKEN, data);
        
        // Determine if the picture need to be rotated.
        File filePictureTaken = getFileStreamPath(NAME_FILE_PICTURE_TAKEN);
        // int rotate = TmpFileUtils.determineRotateBasedOnExifFromFilePath(filePictureTaken.getAbsolutePath());
        int rotate = determineRotateBasedOnOrientation(mOrientationTaken);

        // the picture need to be rotated.
        if (rotate != 0) {
          // Temporarily storage to use for decoding
          BitmapFactory.Options opt = new BitmapFactory.Options();
          opt.inTempStorage = new byte[16 * 1024];
          FileInputStream fis;
          Bitmap photoTakenBitmap;
          try {
            fis = openFileInput(NAME_FILE_PICTURE_TAKEN);
            photoTakenBitmap = BitmapFactory.decodeStream(fis, null, opt);
            fis.close();

            Matrix mat = new Matrix();
            mat.postRotate(rotate);
            try {
              photoTakenBitmap = Bitmap.createBitmap(photoTakenBitmap, 0, 0, photoTakenBitmap.getWidth(), photoTakenBitmap.getHeight(), mat, true);

              ByteArrayOutputStream stream = new ByteArrayOutputStream();
              photoTakenBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
              byte[] byteArray = stream.toByteArray();
              stream.close();

              TmpFileUtils.createTmpFile(cameraActivityCurrent, NAME_FILE_PICTURE_TAKEN, byteArray);
              byteArray = null;
            } catch (OutOfMemoryError oom) {
              Log.e("customCamera", "Can't rotate the picture (out of memory).");
            }
          } catch (FileNotFoundException e) {
            Log.e("customCamera", "Can't load the picture to rotate it.");
          } catch (IOException e) {
            Log.e("customCamera", "Can't close the file picture. Error message: "+e.getMessage());
          } catch (OutOfMemoryError oom) {
            Log.e("customCamera", "Can't laod the picture.");
          }

          photoTakenBitmap = null;
        }
        
        displayPicture();
      }
    };
    // Start capture picture.
    customCamera.takePicture(null, null, jpegCallback);
  }
  
  /**
   * Call when the photo is accepted.
   * @param view The curretnView.
   */
  public void acceptPhoto(View view) {
    Log.i("9zai", "Photo accepted");
    final CameraActivity cameraActivityCurrent = this;
    try {
      BitmapFactory.Options opt = new BitmapFactory.Options();
      // Temporarily storage to use for decoding
      opt.inTempStorage = new byte[16 * 1024];
      byte[] data = TmpFileUtils.getTmpFileContent(this, NAME_FILE_PICTURE_TAKEN);
      Log.i("9zai phone location",NAME_FILE_PICTURE_TAKEN);
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      Bitmap photoTakenBitmap;
      try {
        photoTakenBitmap = BitmapFactory.decodeByteArray(data, 0, data.length, opt);
        Log.i("9zai","accepted and start to qualify");
        //-----9zai     resize
        if(targetHeight!=0 && targetWidth!=0){
          photoTakenBitmap = Bitmap.createScaledBitmap(photoTakenBitmap, targetWidth, targetHeight, true);
        }

        photoTakenBitmap.compress(
            CompressFormat.JPEG, this.getIntent().getIntExtra("quality", 100), stream);
        data = stream.toByteArray();
        // rewrite the file with the compression.
        TmpFileUtils.createTmpFile(cameraActivityCurrent, NAME_FILE_PICTURE_TAKEN, data);
      } catch (OutOfMemoryError oom) {
        Log.e("customCamera", "Can't compress the picture taken (out of memory).");
      }
      photoTakenBitmap = null;
      stream.close();
      
      if (this.getIntent().getBooleanExtra("saveInGallery", false)) {
        // Get path picture to storage.
        String pathPicture = Environment.getExternalStorageDirectory()
            .getPath() + "/" + Environment.DIRECTORY_DCIM + "/Camera/";
        
        // Create the directory if it doesn't exist.
        File galleryDirectory = new File(pathPicture);
        boolean directoryExist = galleryDirectory.exists();
        if (!directoryExist) {
          directoryExist = galleryDirectory.mkdirs();
        }
        
        if (directoryExist) {
          pathPicture = pathPicture
              + String.format("%d.jpeg", System.currentTimeMillis());

          // Write data in file.
          FileOutputStream outStream = new FileOutputStream(pathPicture);
          outStream.write(data);
          data = null;
          outStream.close();
          
          // Notify the gallery to display the new picture.
          Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
          File pictureFile = new File(pathPicture);
          Uri pictureFileUri = Uri.fromFile(pictureFile);
          mediaScanIntent.setData(pictureFileUri);
          this.sendBroadcast(mediaScanIntent);
        } else {
          Log.e("customCamera", "The directory for storage the picture in the gallery doesn't exist and his creation is failed.");
        }
      }
      
      // Return to success & finish current activity.
      cameraActivityCurrent.setResult(1,new Intent());
      cameraActivityCurrent.finish();
    } catch (IOException e) {
      Log.e("customCamera", "Error to write in file: "+e.getMessage());
    }
  }
  
  /**
   * Get the orientation of the current camera.
   * 
   * @return int The orientation of the current camera (FRONT OR BACK)
   */
  public int getOrientationOfCamera() {
    CameraInfo info = new Camera.CameraInfo();
    // Get info of the default camera (which is called by default)
    Camera.getCameraInfo(0, info);

    return info.facing;
  }
  
  /**
   * Call when the photo is declined.
   * @param view The current View.
   */
  public void declinePhoto(View view) {
    ImageButton imgIcon = (ImageButton)findViewById(R.id.capture);
    imgIcon.setEnabled(true);
    
    if (hasFlash()) {
      Camera.Parameters params = customCamera.getParameters();
      params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
      customCamera.setParameters(params);
    }
    
    photoTaken = false;
    displayPicture();
  }

  /** To display or not the picture taken. */
  protected void displayPicture() {
    FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
    ImageView photoResized = (ImageView) findViewById(R.id.photoResized);

    if (photoTaken) {
      // Stop link between view and camera to start the preview
      customCamera.stopPreview();

      byte[] data = TmpFileUtils.getTmpFileContent(this, NAME_FILE_PICTURE_TAKEN);
      Bitmap newBitmap = BitmapUtils.generateOptimizeBitmap(this, data);
      
      // rotate the picture of need.
//      File filePictureTaken = getFileStreamPath(NAME_FILE_PICTURE_TAKEN);
//      int rotate = TmpFileUtils.determineRotateBasedOnExifFromFilePath(filePictureTaken.getAbsolutePath());
//      if (rotate != 0) {
//        Matrix mat = new Matrix();
//        mat.postRotate(rotate);
//        try {
//          newBitmap = Bitmap.createBitmap(newBitmap, 0, 0, newBitmap.getWidth(), newBitmap.getHeight(), mat, true);
//        } catch (OutOfMemoryError oom) {
//          Log.e("customCamera", "Can't rotate the picture taken (out of memory).");
//        }
//      }
      
      photoResized.setImageBitmap(newBitmap);
      newBitmap = null;
      photoResized.setVisibility(View.VISIBLE);
      preview.setVisibility(View.GONE);
    } else {
      customCamera.startPreview();
      photoResized.setVisibility(View.GONE);
      photoResized.setImageBitmap(null);
      preview.setVisibility(View.VISIBLE);

    }

    manageDisplayButtons();
    manageDisplayButtonsOrientation();
  }
  
  /**
   * To perform the rotation.
   * @return the code of the rotation (0, 1, 2, 3)
   */
  protected int getCustomRotation() {
    int code = this.getWindowManager().getDefaultDisplay().getRotation();
    if (getDeviceDefaultOrientation() == Configuration.ORIENTATION_LANDSCAPE) {
      code ++;
    }
    Log.i("9zai:Rotation", "" + code);

    return code == 4 ? 0 : code;
  }

  /**
   * When the back button is pressed.
   */
  @Override
  public void onBackPressed() {
    this.setResult(3);
    this.finish();
  }
  
  /**
   * When the leave button is pressed.
   */
  public void leaveCamera(View v) {
	  onBackPressed();
  }
  
  /**
   * Allow to enable or disable the flash of the camera.
   * @param view The current view.
   */
  public void switchFlash(View view) {
    switch(stateFlash) {
      case CameraActivity.FLASH_DISABLE:
        updateStateFlash(CameraActivity.FLASH_ENABLE);
        break;
      case CameraActivity.FLASH_ENABLE:
        updateStateFlash(CameraActivity.FLASH_AUTO);
        break;
      case CameraActivity.FLASH_AUTO:
        updateStateFlash(CameraActivity.FLASH_DISABLE);
        break;
    }
  }
  
  protected void updateStateFlash(int newStateFlash) {
    ImageButton flash = (ImageButton)findViewById(R.id.flash);
    if (hasFlash()) {
      Camera.Parameters params = customCamera.getParameters();
      List<String> supportedFlashModes = params.getSupportedFlashModes();
      
      if (newStateFlash == CameraActivity.FLASH_AUTO
        && !supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_AUTO)
      ) {
        if (stateFlash == CameraActivity.FLASH_ENABLE) {
          newStateFlash = CameraActivity.FLASH_DISABLE;
        } else {
          newStateFlash = CameraActivity.FLASH_ENABLE;
        }
      }
      stateFlash = newStateFlash;
      
      int imgResource = R.drawable.no_flash;
      switch(stateFlash) {
        case CameraActivity.FLASH_DISABLE:
          imgResource = R.drawable.no_flash;
          break;
        case CameraActivity.FLASH_ENABLE:
          imgResource = R.drawable.flash;
          break;
        case CameraActivity.FLASH_AUTO:
          imgResource = R.drawable.flash_auto;
          break;
      }

      flash.setImageResource(imgResource);
      
      customCamera.setParameters(params);
    }
  }
  
  protected void setFlashMode() {
    if (hasFlash()) {
      String mode = Camera.Parameters.FLASH_MODE_OFF;
      switch(stateFlash) {
        case CameraActivity.FLASH_DISABLE:
          mode = Camera.Parameters.FLASH_MODE_OFF;
          break;
        case CameraActivity.FLASH_ENABLE:
          mode = Camera.Parameters.FLASH_MODE_ON;
          break;
        case CameraActivity.FLASH_AUTO:
          mode = Camera.Parameters.FLASH_MODE_AUTO;
          break;
      }
      Camera.Parameters params = customCamera.getParameters();
      params.setFlashMode(mode);
      customCamera.setParameters(params);
    }
  }
  
  /**
   * Check if camera has a flash feature.
   * @return boolean.
   */
  public boolean hasFlash() {
    if (customCamera == null) {
      return false;
    }

    Camera.Parameters parameters = customCamera.getParameters();

    if (parameters.getFlashMode() == null) {
      return false;
    }

    List<String> supportedFlashModes = parameters.getSupportedFlashModes();
    
    return !(supportedFlashModes == null || supportedFlashModes.isEmpty() ||
      (supportedFlashModes.size() == 1 && supportedFlashModes.get(0).equals(Camera.Parameters.FLASH_MODE_OFF))
    );
  }
  
  /**
   * To change the active camera.
   * @param view The current view.
   */
  public void switchCamera(View view) {
    int oppositeCamera = ManagerCamera.determineOppositeCamera();
    initCameraResource(oppositeCamera);
    FrameLayout cameraPreview = (FrameLayout) findViewById(R.id.camera_preview);
    cameraPreview.removeAllViews();
    setPreviewSize();
    CameraPreview myPreview = new CameraPreview(this, customCamera);
    cameraPreview.addView(myPreview);
  }
  
  /**
   * To set the size of the preview.
   */
  private void setPreviewSize() {
    FrameLayout cameraPreview = (FrameLayout) findViewById(R.id.camera_preview);
    RelativeLayout.LayoutParams paramsCameraPreview = 
        new RelativeLayout.LayoutParams(cameraPreview.getLayoutParams());
    Size optimalSize;
    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
      optimalSize = ManagerCamera.getOptimalPreviewSize(paramsCameraPreview.width, paramsCameraPreview.height);
    } else {
      optimalSize = ManagerCamera.getOptimalPreviewSize(paramsCameraPreview.height, paramsCameraPreview.width);
    }
    Camera.Parameters camParameters = customCamera.getParameters();
    camParameters.setPreviewSize(optimalSize.width, optimalSize.height);
    customCamera.setParameters(camParameters);
    setPictureSizeFromPreviewSize(optimalSize);
  }
  
  /**
   * To set the picture size optimized on the preview size.
   * @param Size previewSize
   */
  private void setPictureSizeFromPreviewSize(Size previewSize) {
    Size optimalPictureSize = ManagerCamera.getOptimalPictureSize(previewSize.width, previewSize.height);
    if (optimalPictureSize != null) {
      Camera.Parameters camParameters = customCamera.getParameters();
      camParameters.setPictureSize(optimalPictureSize.width, optimalPictureSize.height);
      customCamera.setParameters(camParameters);
    }    
  }
  
  /**
   * To set the orientation of the picture taken.
   */
  private void setRotationPictureTaken() {
    int defaultOrientation = getDeviceDefaultOrientation();
    int orientationCamera = getOrientationOfCamera();
    int redirect = CameraActivity.DEGREE_0;

    switch (getCustomRotation()) {
      case 0:
/*        redirect = CameraActivity.DEGREE_90;
        if (ManagerCamera.currentCameraIsFacingFront() || orientationCamera == 1) {
          redirect = CameraActivity.DEGREE_270;
        }   --- This is the situation with protrait   9zai   */
        break;
      case 1:
        redirect = CameraActivity.DEGREE_0;
        break;
      case 2:
        // Only on device with landscape mode by default.
        if (defaultOrientation == Configuration.ORIENTATION_LANDSCAPE) {
          redirect = CameraActivity.DEGREE_270;
        }
        if (ManagerCamera.currentCameraIsFacingFront() || orientationCamera == 1) {
          redirect = CameraActivity.DEGREE_90;
        }
        break;
      case 3:
        redirect = CameraActivity.DEGREE_180;
        break;
      default:
        break;
    }
    Parameters params = customCamera.getParameters();
    params.setRotation(redirect);
//    customCamera.setParameters(params);
  }

  private int determineRotateBasedOnOrientation(int orientation) {

    if (orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
      return 180;
    }

    return 0;
  }

}
