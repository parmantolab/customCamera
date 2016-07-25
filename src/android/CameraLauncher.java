package org.geneanet.customcamera;

import XXX_NAME_CURRENT_PACKAGE_XXX.CameraActivity;

import android.content.Intent;
import android.util.Base64;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;


public class CameraLauncher extends CordovaPlugin {

  protected CallbackContext callbackContext;

  protected static final int RESULT_SUCCESS = 1;
  protected static final int RESULT_ERROR = 2;
  protected static final int RESULT_BACK = 3;

  protected static final int REQUEST_CODE = 88224646;

  /**
   * Execute the plugin.
   * 
   * @param action Action executed.
   * @param args Parameters for the plugin.
   * @param callbackContext Context of callback.
   * @return boolean
   * @throws JSONException Exception JSON if error in generateError.
   */
  public boolean execute(String action, JSONArray args,
      CallbackContext callbackContext) throws JSONException {
        Log.i("9zai_Launcher","in the execute");
    if (action.equals("startCamera")) {
      this.callbackContext = callbackContext;

      Intent intent = new Intent(this.cordova.getActivity(),
          CameraActivity.class);

      if (args.getString(0) != "null") {
        byte[] imgBackgroundBase64;
        try {
          imgBackgroundBase64 = Base64
              .decode(args.getString(0), Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
          this.callbackContext.error(generateError(CameraLauncher.RESULT_ERROR,
              "Error decode base64 picture."));

          return false;
        }
        TmpFileUtils.createTmpFile(this.cordova.getActivity(), CameraActivity.NAME_FILE_BACKGROUND, imgBackgroundBase64);
        imgBackgroundBase64 = null;
      }

      if (args.getString(1) != "null") {
        byte[] imgBackgroundBase64OtherOrientation;
        try {
          imgBackgroundBase64OtherOrientation = Base64
              .decode(args.getString(1), Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
          this.callbackContext.error(generateError(CameraLauncher.RESULT_ERROR,
              "Error decode base64 picture."));

          return false;
        }
        TmpFileUtils.createTmpFile(this.cordova.getActivity(), CameraActivity.NAME_FILE_BACKGROUND_OTHER, imgBackgroundBase64OtherOrientation);
        imgBackgroundBase64OtherOrientation = null;
      }

      // If we don't have a background image, disable miniature and opacity options.
      if (args.getString(0) == "null") {
        intent.putExtra("miniature", false);
        intent.putExtra("opacity", false);
      } else {
        intent.putExtra("miniature", args.getBoolean(2));
        intent.putExtra("opacity", args.getBoolean(7));
      }
      
      intent.putExtra("saveInGallery", args.getBoolean(3));
      intent.putExtra("cameraBackgroundColor", args.getString(4));
      intent.putExtra("cameraBackgroundColorPressed", args.getString(5));
      if (args.getInt(6) >= 0 && args.getInt(6) <= 100) {
        intent.putExtra("quality", args.getInt(6));
      }
      intent.putExtra("startOrientation", this.cordova.getActivity().getResources().getConfiguration().orientation);

      intent.putExtra("defaultFlash", args.getInt(8));
      intent.putExtra("switchFlash", args.getBoolean(9));

      intent.putExtra("defaultCamera", args.getInt(10));
      intent.putExtra("switchCamera", args.getBoolean(11));

            //9zai
      intent.putExtra("targetWidth",args.getInt(12));
      intent.putExtra("targetHeight",args.getInt(13));

      intent.putExtra("zoom",args.getBoolean(14));

      cordova.startActivityForResult((CordovaPlugin) this, intent,
          CameraLauncher.REQUEST_CODE);

      // PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
      // r.setKeepCallback(true);
      // callbackContext.sendPluginResult(r);

      return true;
    }

    return false;
  }

  /**
   * Call when the CameraActivity is over.
   * @param requestCode Code used for the activity luncher.
   * @param resultCode Return code.
   * @param intent Contains parameters of the activity.
   */
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    // remove temporary files.
    this.cordova.getActivity().deleteFile(CameraActivity.NAME_FILE_BACKGROUND);
    this.cordova.getActivity().deleteFile(CameraActivity.NAME_FILE_BACKGROUND_OTHER);
    if (requestCode == CameraLauncher.REQUEST_CODE) {
      switch (resultCode) {
        case CameraLauncher.RESULT_ERROR:
          this.callbackContext.error(generateError(CameraLauncher.RESULT_ERROR,
              intent.getStringExtra("errorMessage")));
          break;
        case CameraLauncher.RESULT_BACK:
          this.callbackContext.error(generateError(CameraLauncher.RESULT_BACK,
              "Error because back camera."));
          break;
        case CameraLauncher.RESULT_SUCCESS:
          try {
            byte[] output = Base64.encode(TmpFileUtils.getTmpFileContent(this.cordova.getActivity(), CameraActivity.NAME_FILE_PICTURE_TAKEN),
              Base64.NO_WRAP);
            this.cordova.getActivity().deleteFile(CameraActivity.NAME_FILE_PICTURE_TAKEN);
            String jsOut = new String(output);
            output = null;

            this.callbackContext.success(jsOut);
          } catch (Exception e) {
            this.callbackContext.error("Error to get content file.");
          }
          break;
        default:
          this.callbackContext.error("Camera has crashed.");
      }
    }
  }

  /**
   * Generate error in the plugin. 
   * 
   * @param code Error code.
   * @param message Error message.
   * 
   * @return a JSON Object.
   */
  protected JSONObject generateError(int code, String message) {
    JSONObject resultForPlugin = new JSONObject();
    try {
      resultForPlugin.put("code", code);
      resultForPlugin.put("message", message);
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return resultForPlugin;
  }
}
