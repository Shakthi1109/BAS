package SecuGen.bas;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

import SecuGen.Driver.Constant;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.widget.TextView;

import SecuGen.FDxSDKPro.*;

public class JSGDActivity extends Activity
        implements View.OnClickListener, java.lang.Runnable, SGFingerPresentEvent {

    private static final String TAG = "SecuGen USB";
    private static final int IMAGE_CAPTURE_TIMEOUT_MS = 10000;
    private static final int IMAGE_CAPTURE_QUALITY = 50;
    
    private Button mButtonCapture;
    private Button mButtonRegister;
    private Button mButtonMatch;
    private Button mButtonLed;
    private Button mSDKTest;
    private EditText mEditLog;
    private android.widget.TextView mTextViewResult;
    private android.widget.CheckBox mCheckBoxMatched;
    private android.widget.ToggleButton mToggleButtonSmartCapture;
    private android.widget.ToggleButton mToggleButtonCaptureModeN;
    private android.widget.ToggleButton mToggleButtonAutoOn;
    private android.widget.ToggleButton mToggleButtonNFIQ;
    private android.widget.ToggleButton mToggleButtonUSBBulkMode64;
    private PendingIntent mPermissionIntent;
    private ImageView mImageViewFingerprint;
    private ImageView mImageViewRegister;
    private ImageView mImageViewVerify;
    private byte[] mRegisterImage;
    private byte[] mVerifyImage;
    private byte[] mRegisterTemplate;
    private byte[] mVerifyTemplate;
	private int[] mMaxTemplateSize;
	private int mImageWidth;
	private int mImageHeight;
	private int mImageDPI;
	private int[] grayBuffer;
    private Bitmap grayBitmap;
    private IntentFilter filter; //2014-04-11
    private SGAutoOnEventNotifier autoOn;
    private boolean mLed;
    private boolean mAutoOnEnabled;
    private int nCaptureModeN;
    private Button mButtonSetBrightness0;
    private Button mButtonSetBrightness100;
    private Button mButtonReadSN;
    private boolean bSecuGenDeviceOpened;
    private JSGFPLib sgfplib;
    private boolean usbPermissionRequested;


    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    private void debugMessage(String message) {
        this.mEditLog.append(message);
        this.mEditLog.invalidate(); //TODO trying to get Edit log to update after each line written
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    //This broadcast receiver is necessary to get user permissions to access the attached USB device
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
    	public void onReceive(Context context, Intent intent) {
    		String action = intent.getAction();
    		//Log.d(TAG,"Enter mUsbReceiver.onReceive()");
    		if (ACTION_USB_PERMISSION.equals(action)) {
    			synchronized (this) {
    				UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    				if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
    					if(device != null){
    						//DEBUG Log.d(TAG, "Vendor ID : " + device.getVendorId() + "\n");
    						//DEBUG Log.d(TAG, "Product ID: " + device.getProductId() + "\n");
    						debugMessage("USB BroadcastReceiver VID : " + device.getVendorId() + "\n");
    						debugMessage("USB BroadcastReceiver PID: " + device.getProductId() + "\n");
    					}
    					else
        					Log.e(TAG, "mUsbReceiver.onReceive() Device is null");			
    				} 
    				else
    					Log.e(TAG, "mUsbReceiver.onReceive() permission denied for device " + device);    				
    			}
    		}
    	}
    };  
    
    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    //This message handler is used to access local resources not
    //accessible by SGFingerPresentCallback() because it is called by
    //a separate thread.
    public Handler fingerDetectedHandler = new Handler(){ 
    	// @Override
	    public void handleMessage(Message msg) {
	       //Handle the message
			CaptureFingerPrint();
	    	if (mAutoOnEnabled) {
				mToggleButtonAutoOn.toggle(); 
		    	EnableControls();
	    	}
	    }
    };

    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
	public void EnableControls(){ 
		this.mButtonCapture.setClickable(true);
		this.mButtonCapture.setTextColor(getResources().getColor(android.R.color.white));		
		this.mButtonRegister.setClickable(true);
		this.mButtonRegister.setTextColor(getResources().getColor(android.R.color.white));		
		this.mButtonMatch.setClickable(true);
		this.mButtonMatch.setTextColor(getResources().getColor(android.R.color.white));	
	    mButtonSetBrightness0.setClickable(true);
	    mButtonSetBrightness100.setClickable(true);
	    mButtonReadSN.setClickable(true);
		this.mButtonLed.setClickable(true); 
		this.mButtonLed.setTextColor(getResources().getColor(android.R.color.white));		
	}

    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
	public void DisableControls(){
		this.mButtonCapture.setClickable(false);
		this.mButtonCapture.setTextColor(getResources().getColor(android.R.color.black));
		this.mButtonRegister.setClickable(false);
		this.mButtonRegister.setTextColor(getResources().getColor(android.R.color.black));		
		this.mButtonMatch.setClickable(false);
		this.mButtonMatch.setTextColor(getResources().getColor(android.R.color.black));	
	    mButtonSetBrightness0.setClickable(false);;
	    mButtonSetBrightness100.setClickable(false);;
	    mButtonReadSN.setClickable(false);
		this.mButtonLed.setClickable(false);
		this.mButtonLed.setTextColor(getResources().getColor(android.R.color.black));		
	}
	
	
    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    @SuppressLint("SetTextI18n")
	@Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.d(TAG, "Enter onCreate()");	
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.launcher);
        mButtonCapture = (Button)findViewById(R.id.buttonCapture);
        mButtonCapture.setOnClickListener(this);
        mButtonRegister = (Button)findViewById(R.id.buttonRegister);
        mButtonRegister.setOnClickListener(this);
        mButtonMatch = (Button)findViewById(R.id.buttonMatch);
        mButtonMatch.setOnClickListener(this);
        mButtonLed = (Button)findViewById(R.id.buttonLedOn);
        mButtonLed.setOnClickListener(this);
        mSDKTest = (Button)findViewById(R.id.buttonSDKTest);
        mSDKTest.setOnClickListener(this);
        mEditLog = (EditText)findViewById(R.id.editLog);
        mTextViewResult = (android.widget.TextView)findViewById(R.id.textViewResult);
        mCheckBoxMatched = (android.widget.CheckBox) findViewById(R.id.checkBoxMatched);
        mToggleButtonSmartCapture = (android.widget.ToggleButton) findViewById(R.id.toggleButtonSmartCapture);
        mToggleButtonSmartCapture.setOnClickListener(this);
        mToggleButtonCaptureModeN = (android.widget.ToggleButton) findViewById(R.id.toggleButtonCaptureModeN);
        mToggleButtonCaptureModeN.setOnClickListener(this);
        mToggleButtonAutoOn = (android.widget.ToggleButton) findViewById(R.id.toggleButtonAutoOn);
        mToggleButtonAutoOn.setOnClickListener(this);        
        mToggleButtonNFIQ = (android.widget.ToggleButton) findViewById(R.id.toggleButtonNFIQ);
        mToggleButtonNFIQ.setOnClickListener(this);        
        mToggleButtonUSBBulkMode64 = (android.widget.ToggleButton) findViewById(R.id.ToggleButtonUSBBulkMode64);
        mToggleButtonUSBBulkMode64.setOnClickListener(this);        
        mImageViewFingerprint = (ImageView)findViewById(R.id.imageViewFingerprint);
        mImageViewRegister = (ImageView)findViewById(R.id.imageViewRegister);
        mImageViewVerify = (ImageView)findViewById(R.id.imageViewVerify);
	    mButtonSetBrightness0 = (Button)findViewById(R.id.buttonSetBrightness0);
	    mButtonSetBrightness0.setOnClickListener(this);
	    mButtonSetBrightness100 = (Button)findViewById(R.id.buttonSetBrightness100);
	    mButtonSetBrightness100.setOnClickListener(this);
		mButtonSetBrightness0.setClickable(false);
		mButtonSetBrightness100.setClickable(false);        			    
		mButtonSetBrightness0.setTextColor(getResources().getColor(android.R.color.black));		
		mButtonSetBrightness100.setTextColor(getResources().getColor(android.R.color.black));		
		mButtonReadSN = (Button)findViewById(R.id.buttonReadSN);
		mButtonReadSN.setOnClickListener(this);


		Intent intent = getIntent();
		float payable = intent.getFloatExtra("payable", 0);

		TextView disp = (TextView) findViewById(R.id.payable);

		disp.setText(Float.toString(payable));
        
        grayBuffer = new int[JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES*JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES];
        for (int i=0; i<grayBuffer.length; ++i)
        	grayBuffer[i] = android.graphics.Color.GRAY;
        grayBitmap = Bitmap.createBitmap(JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES, Bitmap.Config.ARGB_8888);
        grayBitmap.setPixels(grayBuffer, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, 0, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES); 
        mImageViewFingerprint.setImageBitmap(grayBitmap);

        int[] sintbuffer = new int[(JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES/2)*(JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES/2)];
        for (int i=0; i<sintbuffer.length; ++i)
        	sintbuffer[i] = android.graphics.Color.GRAY;
        Bitmap sb = Bitmap.createBitmap(JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES/2, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES/2, Bitmap.Config.ARGB_8888);
        sb.setPixels(sintbuffer, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES/2, 0, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES/2, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES/2); 
        mImageViewRegister.setImageBitmap(grayBitmap);
        mImageViewVerify.setImageBitmap(grayBitmap);        
        mMaxTemplateSize = new int[1];

        //USB Permissions
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
       	filter = new IntentFilter(ACTION_USB_PERMISSION);
        sgfplib = new JSGFPLib((UsbManager)getSystemService(Context.USB_SERVICE));
        this.mToggleButtonSmartCapture.toggle(); 
        bSecuGenDeviceOpened = false;
        usbPermissionRequested = false;
        
		debugMessage("Starting Activity\n");
		debugMessage("JSGFPLib version: " + sgfplib.GetJSGFPLibVersion() + "\n");
		mLed = false;	
		mAutoOnEnabled = false;
		autoOn = new SGAutoOnEventNotifier (sgfplib, this);
		nCaptureModeN = 0;
    	Log.d(TAG, "Exit onCreate()");	
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onPause() {
    	Log.d(TAG, "Enter onPause()");	
    	if (bSecuGenDeviceOpened)
    	{
    		autoOn.stop();
    		EnableControls();
    		sgfplib.CloseDevice();
            bSecuGenDeviceOpened = false;    		
    	}
    	unregisterReceiver(mUsbReceiver);
    	mRegisterImage = null;
    	mVerifyImage = null;
    	mRegisterTemplate = null;
    	mVerifyTemplate = null;
        mImageViewFingerprint.setImageBitmap(grayBitmap);
        mImageViewRegister.setImageBitmap(grayBitmap);
        mImageViewVerify.setImageBitmap(grayBitmap); 
        super.onPause(); 
    	Log.d(TAG, "Exit onPause()");	
    }
    
    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onResume(){
    	Log.d(TAG, "Enter onResume()");	
        super.onResume();
        DisableControls();
       	registerReceiver(mUsbReceiver, filter);       	        
        long error = sgfplib.Init( SGFDxDeviceName.SG_DEV_AUTO);
        if (error != SGFDxErrorCode.SGFDX_ERROR_NONE){

        	//temporary fix for no device (should revoke)

        	AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
        	if (error == SGFDxErrorCode.SGFDX_ERROR_DEVICE_NOT_FOUND)
        		dlgAlert.setMessage("The attached fingerprint device is not supported on Android");
        	else
        		dlgAlert.setMessage("Fingerprint device initialization failed!");
        	dlgAlert.setTitle("SecuGen Fingerprint SDK");
        	dlgAlert.setPositiveButton("OK",
        			new DialogInterface.OnClickListener() {
        		      public void onClick(DialogInterface dialog,int whichButton){
        		        	finish();
        		        	return;        		    	  
        		      }        			
        			}
        	);
        	dlgAlert.setCancelable(false);
        	dlgAlert.create().show();
        }
        else {
	        UsbDevice usbDevice = sgfplib.GetUsbDevice();
	        if (usbDevice == null){
	        	AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
	        	dlgAlert.setMessage("SecuGen fingerprint sensor not found!");
	        	dlgAlert.setTitle("SecuGen Fingerprint SDK");
	        	dlgAlert.setPositiveButton("OK",
	        			new DialogInterface.OnClickListener() { 
	        		      public void onClick(DialogInterface dialog,int whichButton){
	        		        	finish();
	        		        	return;        		    	  
	        		      }        			
	        			}
	        	);
	        	dlgAlert.setCancelable(false); 
	        	dlgAlert.create().show();
	        }
	        else {
	        	boolean hasPermission = sgfplib.GetUsbManager().hasPermission(usbDevice);
		        if (!hasPermission) {
			        if (!usbPermissionRequested)
			        {
			    		debugMessage("Requesting USB Permission\n");
			        	//Log.d(TAG, "Call GetUsbManager().requestPermission()");	
			        	usbPermissionRequested = true;
			        	sgfplib.GetUsbManager().requestPermission(usbDevice, mPermissionIntent);
			        }
			        else
			        {
			        	//wait up to 20 seconds for the system to grant USB permission
			        	hasPermission = sgfplib.GetUsbManager().hasPermission(usbDevice);
			    		debugMessage("Waiting for USB Permission\n");
			        	int i=0;
				        while ((hasPermission == false) && (i <= 40)) 
				        {	
				        	++i;
				            hasPermission = sgfplib.GetUsbManager().hasPermission(usbDevice);
				        	try {
								Thread.sleep(500);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
				        	//Log.d(TAG, "Waited " + i*50 + " milliseconds for USB permission");					        	
				        }
			        }
		        }
		        if (hasPermission) {
		    		debugMessage("Opening SecuGen Device\n");
			        error = sgfplib.OpenDevice(0);
					debugMessage("OpenDevice() ret: " + error + "\n"); 
					if (error == SGFDxErrorCode.SGFDX_ERROR_NONE)
					{
				        bSecuGenDeviceOpened = true;
						SecuGen.FDxSDKPro.SGDeviceInfoParam deviceInfo = new SecuGen.FDxSDKPro.SGDeviceInfoParam();
				        error = sgfplib.GetDeviceInfo(deviceInfo);
						debugMessage("GetDeviceInfo() ret: " + error + "\n");		
				    	mImageWidth = deviceInfo.imageWidth;
				    	mImageHeight= deviceInfo.imageHeight;
				    	mImageDPI = deviceInfo.imageDPI;
						debugMessage("Image width: " + mImageWidth + "\n");		
						debugMessage("Image height: " + mImageHeight + "\n");		
						debugMessage("Image resolution: " + mImageDPI + "\n");		
				    	debugMessage("Serial Number: " + new String(deviceInfo.deviceSN()) + "\n");		    	
				        sgfplib.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794);
						sgfplib.GetMaxTemplateSize(mMaxTemplateSize); 
						debugMessage("TEMPLATE_FORMAT_ISO19794 SIZE: " + mMaxTemplateSize[0] + "\n");
				        mRegisterTemplate = new byte[(int)mMaxTemplateSize[0]];
				        mVerifyTemplate = new byte[(int)mMaxTemplateSize[0]];
				        EnableControls();
				        boolean smartCaptureEnabled = this.mToggleButtonSmartCapture.isChecked();
				        if (smartCaptureEnabled)
				        	sgfplib.WriteData(SGFDxConstant.WRITEDATA_COMMAND_ENABLE_SMART_CAPTURE, (byte)1);
				        else
				        	sgfplib.WriteData(SGFDxConstant.WRITEDATA_COMMAND_ENABLE_SMART_CAPTURE, (byte)0);
				        if (mAutoOnEnabled){
				        	autoOn.start();
				        	DisableControls();
				        }
			        }
			        else
			        {
						debugMessage("Waiting for USB Permission\n");			        	
			        }
		        }
		        //Thread thread = new Thread(this);
		        //thread.start();
	        }
        }
    	Log.d(TAG, "Exit onResume()");	
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onDestroy() {
    	Log.d(TAG, "Enter onDestroy()");
    	sgfplib.CloseDevice();
    	mRegisterImage = null;
    	mVerifyImage = null;
    	mRegisterTemplate = null;
    	mVerifyTemplate = null;
    	sgfplib.Close();
        super.onDestroy();
    	Log.d(TAG, "Exit onDestroy()");
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    //Converts image to grayscale (NEW)
    public Bitmap toGrayscale(byte[] mImageBuffer, int width, int height)
    {        
        byte[] Bits = new byte[mImageBuffer.length * 4];
        for (int i = 0; i < mImageBuffer.length; i++) {
                        Bits[i * 4] = Bits[i * 4 + 1] = Bits[i * 4 + 2] = mImageBuffer[i]; // Invert the source bits
                        Bits[i * 4 + 3] = -1;// 0xff, that's the alpha.
        }

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmpGrayscale.copyPixelsFromBuffer(ByteBuffer.wrap(Bits));
        return bmpGrayscale;
    }
    
    
    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    //Converts image to grayscale (NEW)
    public Bitmap toGrayscale(byte[] mImageBuffer)
    {        
        byte[] Bits = new byte[mImageBuffer.length * 4];
        for (int i = 0; i < mImageBuffer.length; i++) {
                        Bits[i * 4] = Bits[i * 4 + 1] = Bits[i * 4 + 2] = mImageBuffer[i]; // Invert the source bits
                        Bits[i * 4 + 3] = -1;// 0xff, that's the alpha.
        }

        Bitmap bmpGrayscale = Bitmap.createBitmap(mImageWidth, mImageHeight, Bitmap.Config.ARGB_8888);
        bmpGrayscale.copyPixelsFromBuffer(ByteBuffer.wrap(Bits));
        return bmpGrayscale;
    }

    
    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    //Converts image to grayscale (NEW)
    public Bitmap toGrayscale(Bitmap bmpOriginal)
    {        
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();    
        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y=0; y< height; ++y) {
            for (int x=0; x< width; ++x){
            	int color = bmpOriginal.getPixel(x, y);
            	int r = (color >> 16) & 0xFF;
            	int g = (color >> 8) & 0xFF;
            	int b = color & 0xFF;           	
            	int gray = (r+g+b)/3;
            	color = Color.rgb(gray, gray, gray);
            	//color = Color.rgb(r/3, g/3, b/3);
            	bmpGrayscale.setPixel(x, y, color); 
            }
        }
        return bmpGrayscale;
    }
 
    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    //Converts image to binary (OLD)
    public Bitmap toBinary(Bitmap bmpOriginal)
    {        
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();    
        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }
       
    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    public void DumpFile(String fileName, byte[] buffer)
    {
    	//Uncomment section below to dump images and templates to SD card
    	/*
        try {
            File myFile = new File("/sdcard/Download/" + fileName);
            myFile.createNewFile();
            FileOutputStream fOut = new FileOutputStream(myFile);
            fOut.write(buffer,0,buffer.length);
            fOut.close();
        } catch (Exception e) {
            debugMessage("Exception when writing file" + fileName);
        }
       */
    }   

    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    public void SGFingerPresentCallback (){
		autoOn.stop(); 
		fingerDetectedHandler.sendMessage(new Message());
    }
    
    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
	public void CaptureFingerPrint(){
		long dwTimeStart = 0, dwTimeEnd = 0, dwTimeElapsed = 0;
		this.mCheckBoxMatched.setChecked(false);
	    byte[] buffer = new byte[mImageWidth*mImageHeight];
	    dwTimeStart = System.currentTimeMillis(); 
	    //long result = sgfplib.GetImage(buffer);	    
	    long result = sgfplib.GetImageEx(buffer, IMAGE_CAPTURE_TIMEOUT_MS,IMAGE_CAPTURE_QUALITY);

//		Log.d("myTag_buffer", buffer.toString());
//
//		Log.d("myTag_result",""+result);


	    String NFIQString;
	    if (this.mToggleButtonNFIQ.isChecked()) {
	    	long nfiq = sgfplib.ComputeNFIQ(buffer, mImageWidth, mImageHeight);
	    	//long nfiq = sgfplib.ComputeNFIQEx(buffer, mImageWidth, mImageHeight,500); 
	    	NFIQString =  new String("NFIQ="+ nfiq);
	    }
	    else
	    	NFIQString = "";	    
	    DumpFile("capture2016.raw", buffer);
	    dwTimeEnd = System.currentTimeMillis();
	    dwTimeElapsed = dwTimeEnd-dwTimeStart;
	    debugMessage("getImageEx(10000,50) ret:" + result + " [" + dwTimeElapsed + "ms]" + NFIQString +"\n");
		mTextViewResult.setText("getImageEx(10000,50) ret: " + result + " [" + dwTimeElapsed + "ms] " + NFIQString +"\n");         
	    mImageViewFingerprint.setImageBitmap(this.toGrayscale(buffer));  
	    
        buffer = null;
	}  
	
	
    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    public void onClick(View v) {
		long dwTimeStart = 0, dwTimeEnd = 0, dwTimeElapsed = 0;
		if (v== mToggleButtonSmartCapture)
		{
			if(mToggleButtonSmartCapture.isChecked()){
	        	sgfplib.WriteData(SGFDxConstant.WRITEDATA_COMMAND_ENABLE_SMART_CAPTURE, (byte)1); //Enable Smart Capture
	       		this.mButtonSetBrightness0.setClickable(false);
        		this.mButtonSetBrightness100.setClickable(false);
        		this.mButtonSetBrightness0.setTextColor(getResources().getColor(android.R.color.black));		
        		this.mButtonSetBrightness100.setTextColor(getResources().getColor(android.R.color.black));		
			}
	        else {
	        	sgfplib.WriteData(SGFDxConstant.WRITEDATA_COMMAND_ENABLE_SMART_CAPTURE, (byte)0); //Disable Smart Capture
        		this.mButtonSetBrightness0.setClickable(true);
        		this.mButtonSetBrightness100.setClickable(true);
        		this.mButtonSetBrightness0.setTextColor(getResources().getColor(android.R.color.white));		
        		this.mButtonSetBrightness100.setTextColor(getResources().getColor(android.R.color.white));		
	        }
		}		
		if (v== mToggleButtonCaptureModeN)
		{
			if(mToggleButtonCaptureModeN.isChecked())
	        	sgfplib.WriteData((byte)0, (byte)0); //Enable Mode N
	        else
	        	sgfplib.WriteData((byte)0, (byte)1); //Disable Mode N			
		}
		if (v== mToggleButtonUSBBulkMode64)
		{
			if(mToggleButtonUSBBulkMode64.isChecked())
	        	sgfplib.WriteData(Constant.WRITEDATA_COMMAND_ENABLE_USB_MODE_64, (byte)1); //Enable 64byte USB bulk mode
	        else
	        	sgfplib.WriteData(Constant.WRITEDATA_COMMAND_ENABLE_USB_MODE_64, (byte)0); //Enable 4096byte USB bulk mode		
		}
		if (v == this.mButtonReadSN){
			//Read Serial number
			byte[] szSerialNumber = new byte[15];
	        long result = sgfplib.ReadSerialNumber(szSerialNumber);
	        debugMessage("ReadSerialNumber() ret: " + result + " ["	+ new String(szSerialNumber) + "]\n"); 	        
			//Increment last byte and Write serial number      
	        //szSerialNumber[14] += 1;
	        //error = sgfplib.WriteSerialNumber(szSerialNumber);			
	        szSerialNumber = null;
		}			
        if (v == mButtonCapture) {
        	//DEBUG Log.d(TAG, "Pressed CAPTURE");
        	CaptureFingerPrint();
        }
        if (v == mToggleButtonAutoOn) {
			if(mToggleButtonAutoOn.isChecked()) {
				mAutoOnEnabled = true;
				autoOn.start(); //Enable Auto On
	        	DisableControls();
			}
	        else {
				mAutoOnEnabled = false;
	        	autoOn.stop(); //Disable Auto On
	        	EnableControls();
	        }

        }
        if (v == mButtonLed) {
        	this.mCheckBoxMatched.setChecked(false);
        	mLed = !mLed;
            dwTimeStart = System.currentTimeMillis();          
            long result = sgfplib.SetLedOn(mLed);
            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd-dwTimeStart;
            debugMessage("setLedOn(" + mLed +") ret:" + result + " [" + dwTimeElapsed + "ms]\n");
        	mTextViewResult.setText("setLedOn(" + mLed +") ret: " + result + " [" + dwTimeElapsed + "ms]\n");
        }
        if (v == mSDKTest) {
        	SDKTest();
        }
        if (v == this.mButtonRegister) {
        	//DEBUG Log.d(TAG, "Clicked REGISTER");
            debugMessage("Clicked REGISTER\n");
            if (mRegisterImage != null)
            	mRegisterImage = null;
            mRegisterImage = new byte[mImageWidth*mImageHeight];

        	this.mCheckBoxMatched.setChecked(false); 
            dwTimeStart = System.currentTimeMillis();          
    	    long result = sgfplib.GetImageEx(mRegisterImage, IMAGE_CAPTURE_TIMEOUT_MS,IMAGE_CAPTURE_QUALITY);
            DumpFile("register.raw", mRegisterImage);
            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd-dwTimeStart;
            debugMessage("GetImageEx() ret:" + result + " [" + dwTimeElapsed + "ms]\n");
    	    mImageViewFingerprint.setImageBitmap(this.toGrayscale(mRegisterImage));  
            dwTimeStart = System.currentTimeMillis();          
            result = sgfplib.SetTemplateFormat(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794);
            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd-dwTimeStart;
            debugMessage("SetTemplateFormat(ISO19794) ret:" +  result + " [" + dwTimeElapsed + "ms]\n");
            
            int quality1[] = new int[1];
            result = sgfplib.GetImageQuality(mImageWidth, mImageHeight, mRegisterImage, quality1);         
            debugMessage("GetImageQuality() ret:" +  result + "quality [" + quality1[0] + "]\n");

            SGFingerInfo fpInfo = new SGFingerInfo();
            fpInfo.FingerNumber = 1; 
            fpInfo.ImageQuality = quality1[0];
            fpInfo.ImpressionType = SGImpressionType.SG_IMPTYPE_LP;
            fpInfo.ViewNumber = 1;
            
            for (int i=0; i< mRegisterTemplate.length; ++i)
            	mRegisterTemplate[i] = 0;
            dwTimeStart = System.currentTimeMillis();          
            result = sgfplib.CreateTemplate(fpInfo, mRegisterImage, mRegisterTemplate);
            DumpFile("register.min", mRegisterTemplate);
            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd-dwTimeStart;
            debugMessage("CreateTemplate() ret:" + result + " [" + dwTimeElapsed + "ms]\n");

            int[] size = new int[1];
            result = sgfplib.GetTemplateSize(mRegisterTemplate, size);
            debugMessage("GetTemplateSize() ret:" + result+ " size [" + size[0] + "]\n");

            mImageViewRegister.setImageBitmap(this.toGrayscale(mRegisterImage));
//            Log.d("aa",mRegisterImage)
    	    mTextViewResult.setText("Click Verify");
    	    mRegisterImage = null;
    	    fpInfo = null;
        }
        if (v == this.mButtonMatch) {
        	//DEBUG Log.d(TAG, "Clicked MATCH");
            debugMessage("Clicked MATCH\n");
            if (mVerifyImage != null)
            	mVerifyImage = null;
            mVerifyImage = new byte[mImageWidth*mImageHeight];
            dwTimeStart = System.currentTimeMillis();          
    	    long result = sgfplib.GetImageEx(mVerifyImage, IMAGE_CAPTURE_TIMEOUT_MS,IMAGE_CAPTURE_QUALITY);
            //DumpFile("verify.raw", mVerifyImage);
            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd-dwTimeStart;
            debugMessage("GetImageEx() ret:" + result + " [" + dwTimeElapsed + "ms]\n");
    	    mImageViewFingerprint.setImageBitmap(this.toGrayscale(mVerifyImage));  
    	    mImageViewVerify.setImageBitmap(this.toGrayscale(mVerifyImage));  
            dwTimeStart = System.currentTimeMillis();          
            result = sgfplib.SetTemplateFormat(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794);
            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd-dwTimeStart;
            debugMessage("SetTemplateFormat(ISO19794) ret:" +  result + " [" + dwTimeElapsed + "ms]\n");
            
            int quality[] = new int[1];
            result = sgfplib.GetImageQuality(mImageWidth, mImageHeight, mVerifyImage, quality);         
            debugMessage("GetImageQuality() ret:" +  result + "quality [" + quality[0] + "]\n");

            SGFingerInfo fpInfo = new SGFingerInfo();
            fpInfo.FingerNumber = 1; 
            fpInfo.ImageQuality = quality[0];
            fpInfo.ImpressionType = SGImpressionType.SG_IMPTYPE_LP;
            fpInfo.ViewNumber = 1;
            
                       
            for (int i=0; i< mVerifyTemplate.length; ++i)
            	mVerifyTemplate[i] = 0;
            dwTimeStart = System.currentTimeMillis();          
            result = sgfplib.CreateTemplate(fpInfo, mVerifyImage, mVerifyTemplate);
            DumpFile("verify.min", mVerifyTemplate);
            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd-dwTimeStart;
            debugMessage("CreateTemplate() ret:" + result+ " [" + dwTimeElapsed + "ms]\n");
            
            int[] size = new int[1];
            result = sgfplib.GetTemplateSize(mVerifyTemplate, size);
            debugMessage("GetTemplateSize() ret:" + result+ " size [" + size[0] + "]\n");
            
            boolean[] matched = new boolean[1];
            dwTimeStart = System.currentTimeMillis();          
            result = sgfplib.MatchTemplate(mRegisterTemplate, mVerifyTemplate, SGFDxSecurityLevel.SL_NORMAL, matched);

			Log.d("lol", Long.toString(result));
			Log.d("lalala", Arrays.toString(mRegisterTemplate));
			Log.d("lalala", Arrays.toString(mVerifyTemplate));


//			TextView test = (TextView) findViewById(R.id.test);
//			test.setText(Arrays.toString(mRegisterTemplate));

            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd-dwTimeStart;
            debugMessage("MatchTemplate() ret:" + result+ " [" + dwTimeElapsed + "ms]\n");
            if (matched[0]) {
            	mTextViewResult.setText("MATCHED!!\n");
            	this.mCheckBoxMatched.setChecked(true);
                debugMessage("MATCHED!!\n");
            }
            else {
            	mTextViewResult.setText("NOT MATCHED!!");
            	this.mCheckBoxMatched.setChecked(false);
                debugMessage("NOT MATCHED!!\n");
            }
            mVerifyImage = null;
    	    fpInfo = null;
    	    matched = null;
        }
        if (v == this.mButtonSetBrightness0) {
        	this.sgfplib.SetBrightness(0);
        	debugMessage("SetBrightness(0)\n");
        }
        if (v == this.mButtonSetBrightness100) {
        	this.sgfplib.SetBrightness(100);
        	debugMessage("SetBrightness(100)\n");
        }
    }


    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    private void SDKTest(){
    	mTextViewResult.setText("");
    	debugMessage("\n###############\n");
    	debugMessage("### SDK Test  ###\n");
    	debugMessage("###############\n"); 
    	
    	int X_SIZE = 248;
    	int Y_SIZE = 292;
    	
        long error = 0;
        byte[] sgTemplate1;
        byte[] sgTemplate2;
        byte[] sgTemplate3;
        byte[] ansiTemplateBuffer;
        byte[] ansiTemplate1;
        byte[] ansiTemplate2;
        byte[] isoTemplateBuffer;
        byte[] isoTemplate1;
        byte[] isoTemplate2;
        byte[] ansiTemplate1Windows;
        byte[] ansiTemplate2Windows;
        byte[] ansiTemplate3Windows;
        byte[] isoCompactTemplate1;
        byte[] isoCompactTemplate2;
       
        int[] size = new int[1];
        int[] score = new int[1];
        int[] quality1 = new int[1];
        int[] quality2 = new int[1];
        int[] quality3 = new int[1];
        int[] numOfMinutiae = new int[1];
        long nfiq1;
        long nfiq2;
        long nfiq3;
        boolean[] matched = new boolean[1];
        
        byte[] finger1 = new byte[X_SIZE*Y_SIZE];
        byte[] finger2 = new byte[X_SIZE*Y_SIZE];
        byte[] finger3 = new byte[X_SIZE*Y_SIZE];
      
		long dwTimeStart = 0, dwTimeEnd = 0, dwTimeElapsed = 0; 
        
        try {
            InputStream fileInputStream =getResources().openRawResource(R.raw.finger_0_10_3);
        	error = fileInputStream.read(finger1);
            fileInputStream.close();
        } catch (IOException ex){
            debugMessage("Error: Unable to find fingerprint image R.raw.finger_0_10_3.\n");
        	return;
        }
        try {
            InputStream fileInputStream =getResources().openRawResource(R.raw.finger_1_10_3);
        	error = fileInputStream.read(finger2);
            fileInputStream.close();
        } catch (IOException ex){
            debugMessage("Error: Unable to find fingerprint image R.raw.finger_1_10_3.\n");
        	return;
        }
        try {
            InputStream fileInputStream =getResources().openRawResource(R.raw.finger_2_10_3);
        	error = fileInputStream.read(finger3);
            fileInputStream.close();
        } catch (IOException ex){
            debugMessage("Error: Unable to find fingerprint image R.raw.finger_2_10_3.\n");
        	return;
        }

        try {
            InputStream fileInputStream =getResources().openRawResource(R.raw.ansi378_0_10_3_windows);
            int length = fileInputStream.available();
            debugMessage("ansi378_0_10_3_windows.ansi378 \n\ttemplate length is: " + length + "\n");
            ansiTemplate1Windows = new byte[length];
        	error = fileInputStream.read(ansiTemplate1Windows);
            debugMessage("\tRead: " + error + "bytes\n");
            fileInputStream.close();
        } catch (IOException ex){
            debugMessage("Error: Unable to find fingerprint image R.raw.ansi378_0_10_3_windows.ansi378.\n");
        	return; 
        }
        try {
            InputStream fileInputStream =getResources().openRawResource(R.raw.ansi378_1_10_3_windows);
            int length = fileInputStream.available();
            debugMessage("ansi378_1_10_3_windows.ansi378 \n\ttemplate length is: " + length + "\n");
            ansiTemplate2Windows = new byte[length];
        	error = fileInputStream.read(ansiTemplate2Windows);
            debugMessage("\tRead: " + error + "bytes\n");
            fileInputStream.close();
        } catch (IOException ex){
            debugMessage("Error: Unable to find fingerprint image R.raw.ansi378_1_10_3_windows.ansi378.\n");
        	return; 
        }
        try {
            InputStream fileInputStream =getResources().openRawResource(R.raw.ansi378_2_10_3_windows);
            int length = fileInputStream.available();
            debugMessage("ansi378_2_10_3_windows.ansi378 \n\ttemplate length is: " + length + "\n");
            ansiTemplate3Windows = new byte[length];
        	error = fileInputStream.read(ansiTemplate3Windows);
            debugMessage("\tRead: " + error + "bytes\n");
            fileInputStream.close();
        } catch (IOException ex){
            debugMessage("Error: Unable to find fingerprint image R.raw.ansi378_2_10_3_windows.ansi378.\n");
        	return; 
        }       
        
        JSGFPLib sgFplibSDKTest = new JSGFPLib((UsbManager)getSystemService(Context.USB_SERVICE));
        
        error = sgFplibSDKTest.InitEx( X_SIZE, Y_SIZE, 500);
        debugMessage("InitEx("+ X_SIZE + "," + Y_SIZE + ",500) ret:" +  error + "\n");
        if (error != SGFDxErrorCode.SGFDX_ERROR_NONE)
        	return;
        
        SGFingerInfo fpInfo1 = new SGFingerInfo();
        SGFingerInfo fpInfo2 = new SGFingerInfo();
        SGFingerInfo fpInfo3 = new SGFingerInfo();        

        error = sgFplibSDKTest.GetImageQuality((long)X_SIZE, (long)Y_SIZE, finger1, quality1);         
        debugMessage("GetImageQuality(R.raw.finger_0_10_3) ret:" +  error + "\n\tFinger quality=" +  quality1[0] + "\n");
        error = sgFplibSDKTest.GetImageQuality((long)X_SIZE, (long)Y_SIZE, finger2, quality2);         
        debugMessage("GetImageQuality(R.raw.finger_1_10_3) ret:" +  error + "\n\tFinger quality=" +  quality2[0] + "\n");
        error = sgFplibSDKTest.GetImageQuality((long)X_SIZE, (long)Y_SIZE, finger3, quality3);         
        debugMessage("GetImageQuality(R.raw.finger_2_10_3) ret:" +  error + "\n\tFinger quality=" +  quality3[0] + "\n");

        dwTimeStart = System.currentTimeMillis();                 
        nfiq1 = sgFplibSDKTest.ComputeNFIQ(finger1, X_SIZE, Y_SIZE); 
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;
        debugMessage("ComputeNFIQ(R.raw.finger_0_10_3)\n\tNFIQ=" +  nfiq1 + "\n");
        if (nfiq1 == 2)
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");
       	
        dwTimeStart = System.currentTimeMillis();                 
        nfiq2 = sgFplibSDKTest.ComputeNFIQ(finger2, X_SIZE, Y_SIZE); 
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;
        debugMessage("ComputeNFIQ(R.raw.finger_1_10_3)\n\tNFIQ=" +  nfiq2 + "\n");
        if (nfiq2 == 3)
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");

        dwTimeStart = System.currentTimeMillis();                 
        nfiq3 = sgFplibSDKTest.ComputeNFIQ(finger3, X_SIZE, Y_SIZE); 
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;
        debugMessage("ComputeNFIQ(R.raw.finger_2_10_3)\n\tNFIQ=" +  nfiq3 + "\n");
        if (nfiq3 == 2)
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");
                       
        fpInfo1.FingerNumber = 1; 
        fpInfo1.ImageQuality = quality1[0];
        fpInfo1.ImpressionType = SGImpressionType.SG_IMPTYPE_LP;
        fpInfo1.ViewNumber = 1;

        fpInfo2.FingerNumber = 1;
        fpInfo2.ImageQuality = quality2[0]; 
        fpInfo2.ImpressionType = SGImpressionType.SG_IMPTYPE_LP;
        fpInfo2.ViewNumber = 2;

        fpInfo3.FingerNumber = 1;
        fpInfo3.ImageQuality = quality3[0];
        fpInfo3.ImpressionType = SGImpressionType.SG_IMPTYPE_LP;
        fpInfo3.ViewNumber = 3;
        
        
        
        ///////////////////////////////////////////////////////////////////////////////////////////////        
        //TEST SG400
        debugMessage("#######################\n");   
        debugMessage("TEST SG400\n");        
        debugMessage("###\n###\n");        
        error = sgFplibSDKTest.SetTemplateFormat(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);
        debugMessage("SetTemplateFormat(SG400) ret:" +  error + "\n");
        error = sgFplibSDKTest.GetMaxTemplateSize(size);
        debugMessage("GetMaxTemplateSize() ret:" +  error + " SG400_MAX_SIZE=" +  size[0] + "\n");
         
        sgTemplate1  = new byte[(int)size[0]];
        sgTemplate2 = new byte[(int)size[0]];
        sgTemplate3 = new byte[(int)size[0]];

        //////////////////////////////////////////////////////////////////////////////////////////////
        //TEST DeviceInfo       
        ///////////////////////////////////////////////////////////////////////////////////////////////        
        error = sgFplibSDKTest.CreateTemplate(null, finger1, sgTemplate1);
        debugMessage("CreateTemplate(finger3) ret:" + error + "\n");
        error = sgFplibSDKTest.GetTemplateSize(sgTemplate1, size);
        debugMessage("GetTemplateSize() ret:" +  error + " size=" +  size[0] + "\n");        
        error = sgFplibSDKTest.GetNumOfMinutiae(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400, sgTemplate1, numOfMinutiae);
        debugMessage("GetNumOfMinutiae() ret:" +  error + " minutiae=" +  numOfMinutiae[0] + "\n");        

        error = sgFplibSDKTest.CreateTemplate(null, finger2, sgTemplate2);
        debugMessage("CreateTemplate(finger2) ret:" + error + "\n");
        error = sgFplibSDKTest.GetTemplateSize(sgTemplate2, size);
        debugMessage("GetTemplateSize() ret:" +  error + " size=" +  size[0] + "\n");        
        error = sgFplibSDKTest.GetNumOfMinutiae(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400, sgTemplate2, numOfMinutiae);
        debugMessage("GetNumOfMinutiae() ret:" +  error + " minutiae=" +  numOfMinutiae[0] + "\n");        

        error = sgFplibSDKTest.CreateTemplate(null, finger3, sgTemplate3);
        debugMessage("CreateTemplate(finger3) ret:" + error + "\n");
        error = sgFplibSDKTest.GetTemplateSize(sgTemplate3, size);
        debugMessage("GetTemplateSize() ret:" +  error + " size=" +  size[0] + "\n");        
        error = sgFplibSDKTest.GetNumOfMinutiae(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400, sgTemplate3, numOfMinutiae);
        debugMessage("GetNumOfMinutiae() ret:" +  error + " minutiae=" +  numOfMinutiae[0] + "\n");        
        
        ///////////////////////////////////////////////////////////////////////////////////////////////
        error = sgFplibSDKTest.MatchTemplate(sgTemplate1, sgTemplate2, SGFDxSecurityLevel.SL_NORMAL, matched); 
        debugMessage("MatchTemplate(sgTemplate1,sgTemplate2) \n\tret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");
        
        error = sgFplibSDKTest.GetMatchingScore(sgTemplate1, sgTemplate2,  score);
        debugMessage("GetMatchingScore(sgTemplate1, sgTemplate2) \n\tret:" + error + ". \n\tScore:" + score[0] + "\n");

        
        ///////////////////////////////////////////////////////////////////////////////////////////////
        error = sgFplibSDKTest.MatchTemplate(sgTemplate1, sgTemplate3, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchTemplate(sgTemplate1,sgTemplate3) \n\tret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");
        
        error = sgFplibSDKTest.GetMatchingScore(sgTemplate1, sgTemplate3,  score);
        debugMessage("GetMatchingScore(sgTemplate1, sgTemplate3) \n\tret:" + error + ". \n\tScore:" + score[0] + "\n");
        
        
        ///////////////////////////////////////////////////////////////////////////////////////////////
        error = sgFplibSDKTest.MatchTemplate(sgTemplate2, sgTemplate3, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchTemplate(sgTemplate2,sgTemplate3) \n\tret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");
        
        error = sgFplibSDKTest.GetMatchingScore(sgTemplate2, sgTemplate3,  score);
        debugMessage("GetMatchingScore(sgTemplate2, sgTemplate3) \n\tret:" + error + ". \n\tScore:" + score[0] + "\n");
        

        ///////////////////////////////////////////////////////////////////////////////////////////////
        //TEST ANSI378
        ///////////////////////////////////////////////////////////////////////////////////////////////        
        debugMessage("#######################\n");      
        debugMessage("TEST ANSI378\n");        
        debugMessage("###\n###\n");        
        error = sgFplibSDKTest.SetTemplateFormat(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378);
        debugMessage("SetTemplateFormat(ANSI378) ret:" +  error + "\n");
        error = sgFplibSDKTest.GetMaxTemplateSize(size);
        debugMessage("GetMaxTemplateSize() ret:" +  error + "\n\tANSI378_MAX_SIZE=" +  size[0] + "\n");
        
        ansiTemplateBuffer = new byte[(int)size[0]];

        error = sgFplibSDKTest.CreateTemplate(fpInfo1, finger1, ansiTemplateBuffer);
        debugMessage("CreateTemplate(finger1) ret:" + error + "\n");
        error = sgFplibSDKTest.GetTemplateSize(ansiTemplateBuffer, size);
        debugMessage("GetTemplateSize(ansi) ret:" +  error + " size=" +  size[0] + "\n");
        ansiTemplate1  = new byte[(int)size[0]];
        for (int i=0; i<size[0]; ++i)
        	ansiTemplate1[i] = ansiTemplateBuffer[i];
        
        error = sgFplibSDKTest.GetNumOfMinutiae(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378, ansiTemplate1, numOfMinutiae);
        debugMessage("GetNumOfMinutiae(ansi) ret:" +  error + " minutiae=" +  numOfMinutiae[0] + "\n");        
        
        error = sgFplibSDKTest.CreateTemplate(fpInfo2, finger2, ansiTemplateBuffer);
        debugMessage("CreateTemplate(finger2) ret:" + error + "\n");
        error = sgFplibSDKTest.GetTemplateSize(ansiTemplateBuffer, size);
        debugMessage("GetTemplateSize(ansi) ret:" +  error + " size=" +  size[0] + "\n");
        ansiTemplate2 = new byte[(int)size[0]];        
        for (int i=0; i<size[0]; ++i)
        	ansiTemplate2[i] = ansiTemplateBuffer[i];
        
        
        error = sgFplibSDKTest.GetNumOfMinutiae(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378, ansiTemplate2, numOfMinutiae);
        debugMessage("GetNumOfMinutiae(ansi) ret:" +  error + " minutiae=" +  numOfMinutiae[0] + "\n");        

        error = sgFplibSDKTest.MatchTemplate(ansiTemplate1, ansiTemplate2, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchTemplate(ansi) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");
        
        error = sgFplibSDKTest.GetMatchingScore(ansiTemplate1, ansiTemplate2,  score);
        debugMessage("GetMatchingScore(ansi) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        error = sgFplibSDKTest.GetTemplateSizeAfterMerge(ansiTemplate1, ansiTemplate2, size);
        debugMessage("GetTemplateSizeAfterMerge(ansi) ret:" + error + ". \n\tSize:" + size[0] + "\n");

        byte[] mergedAnsiTemplate1 = new byte[(int)size[0]];
        error = sgFplibSDKTest.MergeAnsiTemplate(ansiTemplate1, ansiTemplate2, mergedAnsiTemplate1);
        debugMessage("MergeAnsiTemplate() ret:" + error + "\n");

        error = sgFplibSDKTest.MatchAnsiTemplate(ansiTemplate1, 0, mergedAnsiTemplate1, 0, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchAnsiTemplate(0,0) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");

        error = sgFplibSDKTest.MatchAnsiTemplate(ansiTemplate1, 0, mergedAnsiTemplate1, 1, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchAnsiTemplate(0,1) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");
        
        error = sgFplibSDKTest.GetAnsiMatchingScore(ansiTemplate1, 0, mergedAnsiTemplate1, 0, score);
        debugMessage("GetAnsiMatchingScore(0,0) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        error = sgFplibSDKTest.GetAnsiMatchingScore(ansiTemplate1, 0, mergedAnsiTemplate1, 1, score);
        debugMessage("GetAnsiMatchingScore(0,1) ret:" + error + ". \n\tScore:" + score[0] + "\n");
        
        SGANSITemplateInfo ansiTemplateInfo = new SGANSITemplateInfo();
        error = sgFplibSDKTest.GetAnsiTemplateInfo(ansiTemplate1, ansiTemplateInfo); 
        debugMessage("GetAnsiTemplateInfo(ansiTemplate1) ret:" + error + "\n");
        debugMessage("   TotalSamples=" + ansiTemplateInfo.TotalSamples + "\n");
        for (int i=0; i<ansiTemplateInfo.TotalSamples; ++i){
	        debugMessage("   Sample[" + i + "].FingerNumber=" + ansiTemplateInfo.SampleInfo[i].FingerNumber + "\n");
	        debugMessage("   Sample[" + i + "].ImageQuality=" + ansiTemplateInfo.SampleInfo[i].ImageQuality + "\n");
	        debugMessage("   Sample[" + i + "].ImpressionType=" + ansiTemplateInfo.SampleInfo[i].ImpressionType + "\n");
	        debugMessage("   Sample[" + i + "].ViewNumber=" + ansiTemplateInfo.SampleInfo[i].ViewNumber + "\n");
        }
        
        error = sgFplibSDKTest.GetAnsiTemplateInfo(mergedAnsiTemplate1, ansiTemplateInfo);
        debugMessage("GetAnsiTemplateInfo(mergedAnsiTemplate1) ret:" + error + "\n");
        debugMessage("   TotalSamples=" + ansiTemplateInfo.TotalSamples + "\n");

        for (int i=0; i<ansiTemplateInfo.TotalSamples; ++i){
	        debugMessage("   Sample[" + i + "].FingerNumber=" + ansiTemplateInfo.SampleInfo[i].FingerNumber + "\n");
	        debugMessage("   Sample[" + i + "].ImageQuality=" + ansiTemplateInfo.SampleInfo[i].ImageQuality + "\n");
	        debugMessage("   Sample[" + i + "].ImpressionType=" + ansiTemplateInfo.SampleInfo[i].ImpressionType + "\n");
	        debugMessage("   Sample[" + i + "].ViewNumber=" + ansiTemplateInfo.SampleInfo[i].ViewNumber + "\n");
        }
        
        
        ///////////////////////////////////////////////////////////////////////////////////////////////        
        //ALGORITHM COMPATIBILITY TEST 
        ///////////////////////////////////////////////////////////////////////////////////////////////        
        boolean compatible;
        debugMessage("#######################\n");      
        debugMessage("TEST ANSI378 Compatibility\n");        
        debugMessage("###\n###\n");        
        ///
        error = sgFplibSDKTest.GetMatchingScore(ansiTemplate1, ansiTemplate1Windows,  score);
   	
        debugMessage("0_10_3.raw <> 0_10_3.ansiw:" + score[0] + "\n");
        if (score[0] == 199)
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");
        
        ///
        error = sgFplibSDKTest.GetMatchingScore(ansiTemplate1, ansiTemplate2Windows,  score);
        debugMessage("0_10_3.raw <> 1_10_3.ansiw:" + score[0] + "\n");
        if (score[0] == 199)
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");
        ///
        error = sgFplibSDKTest.GetMatchingScore(ansiTemplate1, ansiTemplate3Windows,  score);
        debugMessage("0_10_3.raw <> 2_10_3.ansiw:" + score[0] + "\n");
        if (score[0] == 176)
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");
        ///
        error = sgFplibSDKTest.GetMatchingScore(ansiTemplate2, ansiTemplate3Windows,  score);
        if (score[0] == 192)
        	compatible = true;
        else
        	compatible = false;      	
        debugMessage("1_10_3.raw <> 2_10_3.ansiw:" + score[0] + "\n\tCompatible:" + compatible + "\n");
        
        ///////////////////////////////////////////////////////////////////////////////////////////////        
        //TEST ISO19794-2
        ///////////////////////////////////////////////////////////////////////////////////////////////        
        debugMessage("#######################\n");       
        debugMessage("TEST ISO19794-2\n");        
        debugMessage("###\n###\n");        
        error = sgFplibSDKTest.SetTemplateFormat(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794);
        debugMessage("SetTemplateFormat(ISO19794) ret:" +  error + "\n");
        error = sgFplibSDKTest.GetMaxTemplateSize(size);
        debugMessage("GetMaxTemplateSize() ret:" +  error + " ISO19794_MAX_SIZE=" +  size[0] + "\n");

        
        isoTemplateBuffer = new byte[(int)size[0]];

        error = sgFplibSDKTest.CreateTemplate(fpInfo1, finger1, isoTemplateBuffer);
        debugMessage("CreateTemplate(finger1) ret:" + error + "\n");
        error = sgFplibSDKTest.GetTemplateSize(isoTemplateBuffer, size);
        debugMessage("GetTemplateSize(iso) ret:" +  error + " \n\tsize=" +  size[0] + "\n");
        isoTemplate1  = new byte[(int)size[0]];
        for (int i=0; i<size[0]; ++i)
        	isoTemplate1[i] = isoTemplateBuffer[i];
        
        error = sgFplibSDKTest.GetNumOfMinutiae(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794, isoTemplate1, numOfMinutiae);
        debugMessage("GetNumOfMinutiae(iso) ret:" +  error + " minutiae=" +  numOfMinutiae[0] + "\n");        
        
        error = sgFplibSDKTest.CreateTemplate(fpInfo2, finger2, isoTemplateBuffer);
        debugMessage("CreateTemplate(finger2) ret:" + error + "\n");
        error = sgFplibSDKTest.GetTemplateSize(isoTemplateBuffer, size);
        debugMessage("GetTemplateSize(iso) ret:" +  error + " \n\tsize=" +  size[0] + "\n");
        isoTemplate2  = new byte[(int)size[0]];
        for (int i=0; i<size[0]; ++i)
        	isoTemplate2[i] = isoTemplateBuffer[i];
        
        error = sgFplibSDKTest.GetNumOfMinutiae(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794, isoTemplate2, numOfMinutiae);
        debugMessage("GetNumOfMinutiae(iso) ret:" +  error + " minutiae=" +  numOfMinutiae[0] + "\n");        

        error = sgFplibSDKTest.MatchTemplate(isoTemplate1, isoTemplate2, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchTemplate(iso) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");
        
        error = sgFplibSDKTest.GetMatchingScore(isoTemplate1, isoTemplate2,  score);
        debugMessage("GetMatchingScore(iso) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        error = sgFplibSDKTest.GetIsoTemplateSizeAfterMerge(isoTemplate1, isoTemplate2, size);
        debugMessage("GetIsoTemplateSizeAfterMerge() ret:" + error + ". \n\tSize:" + size[0] + "\n");


        byte[] mergedIsoTemplate1 = new byte[(int)size[0]];
        error = sgFplibSDKTest.MergeIsoTemplate(isoTemplate1, isoTemplate2, mergedIsoTemplate1);
        debugMessage("MergeIsoTemplate() ret:" + error + "\n");
        
        error = sgFplibSDKTest.MatchIsoTemplate(isoTemplate1, 0, mergedIsoTemplate1, 0, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchIsoTemplate(0,0) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");

        error = sgFplibSDKTest.MatchIsoTemplate(isoTemplate1, 0, mergedIsoTemplate1, 1, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchIsoTemplate(0,1) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");
        
        error = sgFplibSDKTest.GetIsoMatchingScore(isoTemplate1, 0, mergedIsoTemplate1, 0, score);
        debugMessage("GetIsoMatchingScore(0,0) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        error = sgFplibSDKTest.GetIsoMatchingScore(isoTemplate1, 0, mergedIsoTemplate1, 1, score);
        debugMessage("GetIsoMatchingScore(0,1) ret:" + error + ". \n\tScore:" + score[0] + "\n");                
        
        SGISOTemplateInfo isoTemplateInfo = new SGISOTemplateInfo();
        error = sgFplibSDKTest.GetIsoTemplateInfo(isoTemplate1, isoTemplateInfo);
        debugMessage("GetIsoTemplateInfo(isoTemplate1) \n\tret:" + error + "\n");
        debugMessage("\tTotalSamples=" + isoTemplateInfo.TotalSamples + "\n");
        for (int i=0; i<isoTemplateInfo.TotalSamples; ++i){
	        debugMessage("\tSample[" + i + "].FingerNumber=" + isoTemplateInfo.SampleInfo[i].FingerNumber + "\n");
	        debugMessage("\tSample[" + i + "].ImageQuality=" + isoTemplateInfo.SampleInfo[i].ImageQuality + "\n");
	        debugMessage("\tSample[" + i + "].ImpressionType=" + isoTemplateInfo.SampleInfo[i].ImpressionType + "\n");
	        debugMessage("\tSample[" + i + "].ViewNumber=" + isoTemplateInfo.SampleInfo[i].ViewNumber + "\n");
        }

        error = sgFplibSDKTest.GetIsoTemplateInfo(mergedIsoTemplate1, isoTemplateInfo);
        debugMessage("GetIsoTemplateInfo(mergedIsoTemplate1) \n\tret:" + error + "\n");
        debugMessage("\tTotalSamples=" + isoTemplateInfo.TotalSamples + "\n");
        for (int i=0; i<isoTemplateInfo.TotalSamples; ++i){
	        debugMessage("\tSample[" + i + "].FingerNumber=" + isoTemplateInfo.SampleInfo[i].FingerNumber + "\n");
	        debugMessage("\tSample[" + i + "].ImageQuality=" + isoTemplateInfo.SampleInfo[i].ImageQuality + "\n");
	        debugMessage("\tSample[" + i + "].ImpressionType=" + isoTemplateInfo.SampleInfo[i].ImpressionType + "\n");
	        debugMessage("\tSample[" + i + "].ViewNumber=" + isoTemplateInfo.SampleInfo[i].ViewNumber + "\n"); 
        }  
       
        ///////////////////////////////////////////////////////////////////////////////////////////////        
        //TEST ISO19794-2 Compact Template
        ///////////////////////////////////////////////////////////////////////////////////////////////        
        debugMessage("#######################\n");        
        debugMessage("TEST ISO19794-2 Compact\n");        
        debugMessage("###\n###\n");        
        error = sgFplibSDKTest.SetTemplateFormat(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794_COMPACT);
        debugMessage("SetTemplateFormat(ISO19794COMP) ret:" +  error + "\n");
        error = sgFplibSDKTest.GetMaxTemplateSize(size);
        debugMessage("GetMaxTemplateSize() ret:" +  error + " ISO19794_COMP_MAX_SIZE=" +  size[0] + "\n");
        
        isoCompactTemplate1  = new byte[(int)size[0]];
        isoCompactTemplate2 = new byte[(int) size[0]];       

        error = sgFplibSDKTest.CreateTemplate(fpInfo1, finger1, isoCompactTemplate1);
        debugMessage("CreateTemplate(finger1) ret:" + error + "\n");
        error = sgFplibSDKTest.GetTemplateSize(isoCompactTemplate1, size);
        debugMessage("GetTemplateSize(isocompact) ret:" +  error + " \n\tsize=" +  size[0] + "\n");
        error = sgFplibSDKTest.GetNumOfMinutiae(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794_COMPACT, isoCompactTemplate1, numOfMinutiae);
        debugMessage("GetNumOfMinutiae(isocompact) ret:" +  error + " minutiae=" +  numOfMinutiae[0] + "\n");        
        
        error = sgFplibSDKTest.CreateTemplate(fpInfo2, finger2, isoCompactTemplate2);
        debugMessage("CreateTemplate(finger2) ret:" + error + "\n");
        error = sgFplibSDKTest.GetTemplateSize(isoCompactTemplate2, size);
        debugMessage("GetTemplateSize(isocompact) ret:" +  error + " \n\tsize=" +  size[0] + "\n");
        error = sgFplibSDKTest.GetNumOfMinutiae(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794_COMPACT, isoCompactTemplate2, numOfMinutiae);
        debugMessage("GetNumOfMinutiae(isocompact) ret:" +  error + " minutiae=" +  numOfMinutiae[0] + "\n");        

        error = sgFplibSDKTest.MatchTemplate(isoCompactTemplate1, isoCompactTemplate2, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchTemplate(isocompact) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");
        
        error = sgFplibSDKTest.GetMatchingScore(isoCompactTemplate1, isoCompactTemplate2,  score);
        debugMessage("GetMatchingScore(isocompact) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        error = sgFplibSDKTest.GetIsoCompactTemplateSizeAfterMerge(isoCompactTemplate1, isoCompactTemplate2, size);
        debugMessage("GetIsoTemplateSizeAfterMerge() ret:" + error + ". \n\tSize:" + size[0] + "\n");

        byte[] mergedIsoCompactTemplate = new byte[(int)size[0]];
        error = sgFplibSDKTest.MergeIsoCompactTemplate(isoCompactTemplate1, isoCompactTemplate2, mergedIsoCompactTemplate);
        debugMessage("MergeIsoTemplate() ret:" + error + "\n");
        
        error = sgFplibSDKTest.MatchIsoCompactTemplate(isoCompactTemplate1, 0, mergedIsoCompactTemplate, 0, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchIsoCompactTemplate(0,0) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");

        error = sgFplibSDKTest.MatchIsoCompactTemplate(isoCompactTemplate1, 0, mergedIsoCompactTemplate, 1, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchIsoCompactTemplate(0,1) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");
        
        error = sgFplibSDKTest.GetIsoCompactMatchingScore(isoCompactTemplate1, 0, mergedIsoCompactTemplate, 0, score);
        debugMessage("GetIsoCompactMatchingScore(0,0) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        error = sgFplibSDKTest.GetIsoCompactMatchingScore(isoCompactTemplate1, 0, mergedIsoCompactTemplate, 1, score);
        debugMessage("GetIsoCompactMatchingScore(0,1) ret:" + error + ". \n\tScore:" + score[0] + "\n");                
        
        SGISOTemplateInfo isoCompactTemplateInfo = new SGISOTemplateInfo();
        error = sgFplibSDKTest.GetIsoCompactTemplateInfo(isoCompactTemplate1, isoCompactTemplateInfo);
        debugMessage("GetIsoCompactTemplateInfo(isoCompactTemplate1) \n\tret:" + error + "\n");
        debugMessage("\tTotalSamples=" + isoCompactTemplateInfo.TotalSamples + "\n");
        for (int i=0; i<isoCompactTemplateInfo.TotalSamples; ++i){
	        debugMessage("\tSample[" + i + "].FingerNumber=" + isoCompactTemplateInfo.SampleInfo[i].FingerNumber + "\n");
	        debugMessage("\tSample[" + i + "].ImageQuality=" + isoCompactTemplateInfo.SampleInfo[i].ImageQuality + "\n");
	        debugMessage("\tSample[" + i + "].ImpressionType=" + isoCompactTemplateInfo.SampleInfo[i].ImpressionType + "\n");
	        debugMessage("\tSample[" + i + "].ViewNumber=" + isoCompactTemplateInfo.SampleInfo[i].ViewNumber + "\n");
        }

        error = sgFplibSDKTest.GetIsoCompactTemplateInfo(mergedIsoCompactTemplate, isoCompactTemplateInfo);
        debugMessage("GetIsoCompactTemplateInfo(mergedIsoTemplate1) \n\tret:" + error + "\n");
        debugMessage("\tTotalSamples=" + isoCompactTemplateInfo.TotalSamples + "\n");
        for (int i=0; i<isoCompactTemplateInfo.TotalSamples; ++i){
	        debugMessage("\tSample[" + i + "].FingerNumber=" + isoCompactTemplateInfo.SampleInfo[i].FingerNumber + "\n");
	        debugMessage("\tSample[" + i + "].ImageQuality=" + isoCompactTemplateInfo.SampleInfo[i].ImageQuality + "\n");
	        debugMessage("\tSample[" + i + "].ImpressionType=" + isoCompactTemplateInfo.SampleInfo[i].ImpressionType + "\n");
	        debugMessage("\tSample[" + i + "].ViewNumber=" + isoCompactTemplateInfo.SampleInfo[i].ViewNumber + "\n"); 
        }  

        ///////////////////////////////////////////////////////////////////////////////////////////////        
        //TEST Mixed Templates
        ///////////////////////////////////////////////////////////////////////////////////////////////        
        debugMessage("#######################\n");        
        debugMessage("TEST Mixed Templates\n");
        debugMessage("###\n###\n");        

        //////////////////////////////////////////
        //SG400<>SG400
        matched[0] = false;
        error = sgFplibSDKTest.MatchTemplateEx(
        		sgTemplate1, 
				SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400, 
				(long)0,
				sgTemplate2, 
				SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400,
				(long)0,
				SGFDxSecurityLevel.SL_NORMAL,
				matched);
        debugMessage("MatchTemplateEx(SG400,SG400) ret:" + error + "\n");
        if (matched[0])
        	debugMessage("\tMATCHED!!\n");
        else
        	debugMessage("\tNOT MATCHED!!\n");

        score[0] = 0;
        error = sgFplibSDKTest.GetMatchingScoreEx(
        		sgTemplate1, 
				SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400, 
				(long)0,
				sgTemplate2, 
				SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400,
				(long)0,
				score);
        debugMessage("GetMatchingScoreEx(SG400,SG400) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        //////////////////////////////////////////
        //ISO19794<>ISO19794 
        matched[0] = false;
        error = sgFplibSDKTest.MatchTemplateEx(
        									isoTemplate1, 
        									SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794, 
        									(long)0,
        									isoTemplate1, 
        									SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794,
        									(long)0,
        									SGFDxSecurityLevel.SL_NORMAL,
        									matched);
        debugMessage("MatchTemplateEx(ISO1,ISO1) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");

        matched[0] = false;
        error = sgFplibSDKTest.MatchTemplateEx( 
        									isoTemplate2, 
        									SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794, 
        									(long)0,
        									isoTemplate2, 
        									SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794,
        									(long)0,
        									SGFDxSecurityLevel.SL_NORMAL,
        									matched);
        debugMessage("MatchTemplateEx(ISO2,ISO2) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");

        matched[0] = false;
        error = sgFplibSDKTest.MatchTemplateEx(
        									isoTemplate1, 
        									SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794, 
        									(long)0,
        									isoTemplate2, 
        									SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794,
        									(long)0,
        									SGFDxSecurityLevel.SL_NORMAL,
        									matched);
        debugMessage("MatchTemplateEx(ISO1,ISO2) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");
        
        matched[0] = false;
        error = sgFplibSDKTest.MatchTemplateEx(
        									isoTemplate2, 
        									SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794, 
        									(long)0,
        									isoTemplate1, 
        									SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794,
        									(long)0,
        									SGFDxSecurityLevel.SL_NORMAL,
        									matched);
        debugMessage("MatchTemplateEx(ISO2,ISO1) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");
        
        
        score[0] = 0;
        error = sgFplibSDKTest.GetMatchingScoreEx(
        									isoTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794, 
											(long)0,
											isoTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794,
											(long)0,
											score);
        debugMessage("GetMatchingScoreEx(ISO1,ISO1) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        score[0] = 0;
        error = sgFplibSDKTest.GetMatchingScoreEx(
        									isoTemplate2, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794, 
											(long)0,
											isoTemplate2, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794,
											(long)0,
											score);
        debugMessage("GetMatchingScoreEx(ISO2,ISO2) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        score[0] = 0;
        error = sgFplibSDKTest.GetMatchingScoreEx(
        									isoTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794, 
											(long)0,
											isoTemplate2, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794,
											(long)0,
											score);
        debugMessage("GetMatchingScoreEx(ISO1,ISO2) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        score[0] = 0;
        error = sgFplibSDKTest.GetMatchingScoreEx(
        									isoTemplate2, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794, 
											(long)0,
											isoTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794,
											(long)0,
											score);
        debugMessage("GetMatchingScoreEx(ISO2,ISO1) ret:" + error + ". \n\tScore:" + score[0] + "\n");
        
        
        //////////////////////////////////////////
        //ANSI378<>ANSI378
        matched[0] = false;
        error = sgFplibSDKTest.MatchTemplateEx(
        									ansiTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378, 
											(long)0,
											ansiTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378,
											(long)0,
											SGFDxSecurityLevel.SL_NORMAL,
											matched);
        debugMessage("MatchTemplateEx(ANSI1,ANSI1) ret:" + error + "\n");
        if (matched[0])
        	debugMessage("\tMATCHED!!\n");
        else
        	debugMessage("\tNOT MATCHED!!\n");

        //////////////////////////////////////////
        //ANSI378<>ISO19794
        matched[0] = false;
        error = sgFplibSDKTest.MatchTemplateEx(
        									ansiTemplate2, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378, 
											(long)0,
											ansiTemplate2, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378,
											(long)0,
											SGFDxSecurityLevel.SL_NORMAL,
											matched);
        debugMessage("MatchTemplateEx(ANSI2,ANSI2) ret:" + error + "\n");
        if (matched[0])
        	debugMessage("\tMATCHED!!\n");
        else
        	debugMessage("\tNOT MATCHED!!\n");
        
        
        matched[0] = false;
        error = sgFplibSDKTest.MatchTemplateEx(
        									ansiTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378, 
											(long)0,
											ansiTemplate2, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378,
											(long)0,
											SGFDxSecurityLevel.SL_NORMAL,
											matched);
        debugMessage("MatchTemplateEx(ANSI1,ANSI2) ret:" + error + "\n");
        if (matched[0])
        	debugMessage("\tMATCHED!!\n");
        else
        	debugMessage("\tNOT MATCHED!!\n");

        matched[0] = false;
        error = sgFplibSDKTest.MatchTemplateEx(
        									ansiTemplate2, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378, 
											(long)0,
											ansiTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378,
											(long)0,
											SGFDxSecurityLevel.SL_NORMAL,
											matched);
        debugMessage("MatchTemplateEx(ANSI2,ANSI1) ret:" + error + "\n");
        if (matched[0])
        	debugMessage("\tMATCHED!!\n");
        else
        	debugMessage("\tNOT MATCHED!!\n");
        
        
        score[0] = 0;
        error = sgFplibSDKTest.GetMatchingScoreEx(
        									ansiTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378, 
											(long)0,
											ansiTemplate2, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378,
											(long)0,
											score);
        debugMessage("GetMatchingScoreEx(ANSI1,ANSI1) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        score[0] = 0;
        error = sgFplibSDKTest.GetMatchingScoreEx(
        									ansiTemplate2, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378, 
											(long)0,
											ansiTemplate2, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378,
											(long)0,
											score);
        debugMessage("GetMatchingScoreEx(ANSI2,ANSI2) ret:" + error + ". \n\tScore:" + score[0] + "\n");
        
        score[0] = 0;
        error = sgFplibSDKTest.GetMatchingScoreEx(
        									ansiTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378, 
											(long)0,
											ansiTemplate2, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378,
											(long)0,
											score);
        debugMessage("GetMatchingScoreEx(ANSI1,ANSI2) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        score[0] = 0;
        error = sgFplibSDKTest.GetMatchingScoreEx(
        									ansiTemplate2, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378, 
											(long)0,
											ansiTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378,
											(long)0,
											score);
        debugMessage("GetMatchingScoreEx(ANSI2,ANSI1) ret:" + error + ". \n\tScore:" + score[0] + "\n");
        
        
        //////////////////////////////////////////
        //ISO19794COMPACT<>ISO19794COMPACT
        matched[0] = false;
        error = sgFplibSDKTest.MatchTemplateEx(
        									isoCompactTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794_COMPACT, 
											(long)0,
											isoCompactTemplate2, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794_COMPACT,
											(long)0,
											SGFDxSecurityLevel.SL_NORMAL,
											matched);
        debugMessage("MatchTemplateEx(ISOCOMPACT,ISOCOMPACT) ret:" + error + "\n");
        if (matched[0])
        	debugMessage("\tMATCHED!!\n");
        else
        	debugMessage("\tNOT MATCHED!!\n");

        score[0] = 0;
        error = sgFplibSDKTest.GetMatchingScoreEx(
        									isoCompactTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794_COMPACT, 
											(long)0,
											isoCompactTemplate2, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794_COMPACT,
											(long)0,
											score);
        debugMessage("GetMatchingScoreEx(ISOCOMPACT,ISOCOMPACT) ret:" + error + ". \n\tScore:" + score[0] + "\n");
        
        //////////////////////////////////////////
        //SG400<>ANSI 
        matched[0] = false;
        error = sgFplibSDKTest.MatchTemplateEx(
        									sgTemplate1, 
        									SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400, 
        									(long)0,
        									ansiTemplate1, 
        									SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378,
        									(long)0,
        									SGFDxSecurityLevel.SL_NORMAL,
        									matched);
        debugMessage("MatchTemplateEx(SG4001,ANSI1) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");

        score[0] = 0;
        error = sgFplibSDKTest.GetMatchingScoreEx(
        									sgTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400, 
											(long)0,
											ansiTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378,
											(long)0,
											score);
        debugMessage("GetMatchingScoreEx(SG4001,ANSI1) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        //////////////////////////////////////////
        //ANSI<>SG400 
        matched[0] = false;
        error = sgFplibSDKTest.MatchTemplateEx(
        									ansiTemplate1, 
        									SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378, 
        									(long)0,
        									sgTemplate1, 
        									SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400,
        									(long)0,
        									SGFDxSecurityLevel.SL_NORMAL,
        									matched);
        debugMessage("MatchTemplateEx(ANSI1,SG4001) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");

        score[0] = 0;
        error = sgFplibSDKTest.GetMatchingScoreEx(
        									ansiTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378, 
											(long)0,
											sgTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400,
											(long)0,
											score);
        debugMessage("GetMatchingScoreEx(ANSI1,SG4001) ret:" + error + ". \n\tScore:" + score[0] + "\n");
        
        
        //////////////////////////////////////////
        //ANSI378<>ISO19794
        matched[0] = false;
        error = sgFplibSDKTest.MatchTemplateEx(
        									ansiTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378, 
											(long)0,
											isoTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794,
											(long)0,
											SGFDxSecurityLevel.SL_NORMAL,
											matched);
        debugMessage("MatchTemplateEx(ANSI,ISO) ret:" + error + "\n");
        if (matched[0])
        	debugMessage("\tMATCHED!!\n");
        else
        	debugMessage("\tNOT MATCHED!!\n");

        score[0] = 0;
        error = sgFplibSDKTest.GetMatchingScoreEx(
        									ansiTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378, 
											(long)0,
											isoTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794,
											(long)0,
											score);
        debugMessage("GetMatchingScoreEx(ANSI,ISO) ret:" + error + ". \n\tScore:" + score[0] + "\n");
        
        //////////////////////////////////////////
        //ANSI378<>ISO19794COMPACT
        matched[0] = false;
        error = sgFplibSDKTest.MatchTemplateEx(
        									ansiTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378, 
											(long)0,
											isoCompactTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794_COMPACT,
											(long)0,
											SGFDxSecurityLevel.SL_NORMAL,
											matched);
        debugMessage("MatchTemplateEx(ANSI,ISOCOMPACT) ret:" + error + "\n");
        if (matched[0])
        	debugMessage("\tMATCHED!!\n");
        else
        	debugMessage("\tNOT MATCHED!!\n");

        score[0] = 0;
        error = sgFplibSDKTest.GetMatchingScoreEx(
        									ansiTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378, 
											(long)0,
											isoCompactTemplate1, 
											SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794_COMPACT,
											(long)0,
											score);
        debugMessage("GetMatchingScoreEx(ANSI,ISOCOMPACT) ret:" + error + ". \n\tScore:" + score[0] + "\n");
        
        
        
        ///////////////////////////////////////////////////////////////////////////////////////////////        
    	//Reset extractor/matcher for attached device opened in resume() method
        ///////////////////////////////////////////////////////////////////////////////////////////////        
        error = sgFplibSDKTest.InitEx( mImageWidth, mImageHeight, 500);
        debugMessage("InitEx("+ mImageWidth + "," + mImageHeight + ",500) ret:" +  error + "\n");
        
        
        ///////////////////////////////////////////////////////////////////////////////////////////////        
        //Test WSQ Processing
        ///////////////////////////////////////////////////////////////////////////////////////////////        
        debugMessage("#######################\n");
        debugMessage("TEST WSQ COMPRESSION\n");        
        debugMessage("###\n###\n");
        byte[] wsqfinger1;
        int wsqLen;
        try {
            InputStream fileInputStream =getResources().openRawResource(R.raw.wsq2raw_finger);
            wsqLen = fileInputStream.available();
            debugMessage("WSQ file length is: " + wsqLen + "\n");
            wsqfinger1 = new byte[wsqLen];
        	error = fileInputStream.read(wsqfinger1);
            debugMessage("Read: " + error + "bytes\n");
            fileInputStream.close();
        } catch (IOException ex){
            debugMessage("Error: Unable to find fingerprint image R.raw.wsq2raw_finger.\n");
        	return; 
        }

        
        int[] fingerImageOutSize = new int[1];
        dwTimeStart = System.currentTimeMillis();                 
        error = sgFplibSDKTest.WSQGetDecodedImageSize(fingerImageOutSize, wsqfinger1, wsqLen); 
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;
        debugMessage("WSQGetDecodedImageSize() ret:" +  error + "\n"); 
        debugMessage("\tRAW Image size is: " + fingerImageOutSize[0] + "\n");
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");
//      debugMessage("Byte 0:"+ String.format("%02X",wsqfinger1[0]) + "\n");
//      debugMessage("Byte 1:"+ String.format("%02X",wsqfinger1[1]) + "\n");
//      debugMessage("Byte 201:"+ String.format("%02X",wsqfinger1[201]) + "\n");
//      debugMessage("Byte 1566:"+ String.format("%02X",wsqfinger1[1566]) + "\n");
//      debugMessage("Byte 7001:"+ String.format("%02X",wsqfinger1[7001]) + "\n");
//      debugMessage("Byte 7291:"+ String.format("%02X",wsqfinger1[7291]) + "\n");        

        byte[] rawfinger1ImageOut = new byte[fingerImageOutSize[0]];
        int[] decodeWidth = new int[1];
        int[] decodeHeight = new int[1];
        int[] decodePixelDepth = new int[1];
        int[] decodePPI = new int[1];
        int[] decodeLossyFlag = new int[1];
        debugMessage("Decode WSQ File\n");     
        dwTimeStart = System.currentTimeMillis();                 
        error = sgFplibSDKTest.WSQDecode(rawfinger1ImageOut, decodeWidth, decodeHeight, decodePixelDepth, decodePPI, decodeLossyFlag, wsqfinger1, wsqLen);
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;
        debugMessage("\tret:\t\t\t"+ error + "\n"); 
        debugMessage("\twidth:\t\t"+ decodeWidth[0] + "\n"); 
        debugMessage("\theight:\t\t"+ decodeHeight[0] + "\n"); 
        debugMessage("\tdepth:\t\t"+ decodePixelDepth[0] + "\n"); 
        debugMessage("\tPPI:\t\t\t"+ decodePPI[0] + "\n");
        debugMessage("\tLossy Flag\t"+ decodeLossyFlag[0] + "\n");
        if ((decodeWidth[0] == 258) && (decodeHeight[0] == 336))
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");

	    mImageViewFingerprint.setImageBitmap(this.toGrayscale(rawfinger1ImageOut, decodeWidth[0], decodeHeight[0]));  
        

        byte[] rawfinger1;
        int encodeWidth=258;
        int encodeHeight=336;
        int encodePixelDepth=8;
        int encodePPI=500;
       		
        int rawLen;
        try {
            InputStream fileInputStream =getResources().openRawResource(R.raw.raw2wsq_finger);
            rawLen = fileInputStream.available();
            debugMessage("RAW file length is: " + rawLen + "\n");
            rawfinger1 = new byte[rawLen];
        	error = fileInputStream.read(rawfinger1);
            debugMessage("Read: " + error + "bytes\n");
            fileInputStream.close();
        } catch (IOException ex){
            debugMessage("Error: Unable to find fingerprint image R.raw.raw2wsq_finger.\n");
        	return; 
        }

        int[] wsqImageOutSize = new int[1];
        dwTimeStart = System.currentTimeMillis();                 
        error = sgFplibSDKTest.WSQGetEncodedImageSize(wsqImageOutSize, SGWSQLib.BITRATE_5_TO_1, rawfinger1, encodeWidth, encodeHeight, encodePixelDepth, encodePPI);
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;        
        debugMessage("WSQGetEncodedImageSize() ret:" +  error + "\n"); 
        debugMessage("WSQ Image size is: " + wsqImageOutSize[0] + "\n");
        if (wsqImageOutSize[0] == 20200)
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");

        byte[] wsqfinger1ImageOut = new byte[wsqImageOutSize[0]];
        dwTimeStart = System.currentTimeMillis();                 
        error = sgFplibSDKTest.WSQEncode(wsqfinger1ImageOut, SGWSQLib.BITRATE_5_TO_1, rawfinger1, encodeWidth, encodeHeight, encodePixelDepth, encodePPI);
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;        
        debugMessage("WSQEncode() ret:" +  error + "\n"); 
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");
     
        dwTimeStart = System.currentTimeMillis();                 
        error = sgFplibSDKTest.WSQGetDecodedImageSize(fingerImageOutSize, wsqfinger1ImageOut, wsqImageOutSize[0]); 
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;        
        debugMessage("WSQGetDecodedImageSize() ret:" +  error + "\n"); 
        debugMessage("RAW Image size is: " + fingerImageOutSize[0] + "\n");
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");
 
        byte[] rawfinger2ImageOut = new byte[fingerImageOutSize[0]];
        dwTimeStart = System.currentTimeMillis();                 
        error = sgFplibSDKTest.WSQDecode(rawfinger2ImageOut, decodeWidth, decodeHeight, decodePixelDepth, decodePPI, decodeLossyFlag, wsqfinger1, wsqLen);
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;                
        debugMessage("WSQDecode() ret:" +  error + "\n"); 
        debugMessage("\tret:\t\t\t"+ error + "\n"); 
        debugMessage("\twidth:\t\t"+ decodeWidth[0] + "\n"); 
        debugMessage("\theight:\t\t"+ decodeHeight[0] + "\n"); 
        debugMessage("\tdepth:\t\t"+ decodePixelDepth[0] + "\n"); 
        debugMessage("\tPPI:\t\t\t"+ decodePPI[0] + "\n");
        debugMessage("\tLossy Flag\t"+ decodeLossyFlag[0] + "\n");
        if ((decodeWidth[0] == 258) && (decodeHeight[0] == 336))
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");
	    mImageViewFingerprint.setImageBitmap(this.toGrayscale(rawfinger2ImageOut, decodeWidth[0], decodeHeight[0])); 
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");
                    
        debugMessage("\n## END SDK TEST ##\n");
    }
    
    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    public void run() {
    	
    	//Log.d(TAG, "Enter run()");
        //ByteBuffer buffer = ByteBuffer.allocate(1);
        //UsbRequest request = new UsbRequest();
        //request.initialize(mSGUsbInterface.getConnection(), mEndpointBulk);
        //byte status = -1;
        while (true) {
        	
        	
            // queue a request on the interrupt endpoint
            //request.queue(buffer, 1);
            // send poll status command
          //  sendCommand(COMMAND_STATUS);
            // wait for status event
            /*
            if (mSGUsbInterface.getConnection().requestWait() == request) {
                byte newStatus = buffer.get(0);
                if (newStatus != status) {
                    Log.d(TAG, "got status " + newStatus);
                    status = newStatus;
                    if ((status & COMMAND_FIRE) != 0) {
                        // stop firing
                        sendCommand(COMMAND_STOP);
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            } else {
                Log.e(TAG, "requestWait failed, exiting");
                break;
            }
            */
        }
    }
}