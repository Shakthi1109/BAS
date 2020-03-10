package SecuGen.bas;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import SecuGen.FDxSDKPro.*;


public class RegisterActivity extends Activity implements View.OnClickListener, java.lang.Runnable, SGFingerPresentEvent{

    private static final String TAG = "SecuGen USB";
    private static final int IMAGE_CAPTURE_TIMEOUT_MS = 10000;
    private static final int IMAGE_CAPTURE_QUALITY = 50;

    private Button mButtonCapture;
    private Button mButtonLed;
    private android.widget.TextView mTextViewResult;
    private PendingIntent mPermissionIntent;
    ImageView mImageViewFingerprint;
    private int[] mMaxTemplateSize;
    private int mImageWidth;
    private int mImageHeight;
    private int[] grayBuffer;
    private Bitmap grayBitmap;
    private IntentFilter filter; //2014-04-11
    private SGAutoOnEventNotifier autoOn;
    private boolean mLed;
    private boolean mAutoOnEnabled;
    private boolean bSecuGenDeviceOpened;
    private JSGFPLib sgfplib;
    private boolean usbPermissionRequested;
    String NFIQString,buffer;

    String[] YearArr = {"First", "Second", "Third", "Fourth"};
    String[] DeptArr = {"CSE","MECH","ECE","MBA"};
    String[] SecArr = {"A", "B","C","D","E"};


    //Spinner Dropdown
    Spinner spinnerYear, spinnerDept,spinnerSec;

    //String for spinner
    String Year, Dept, Sec;
    String regNum;
    String last3regNum;
    String email;


    private byte[] mRegisterImage;
    private byte[] mRegisterTemplate;


    Button registerBtn,choose,upload;
    EditText Name, Amt,Email,pinInput;
    String nameStr,template,uploadToFirebase;


    DatabaseReference reff;
    Member member;
    long maxid=0;
    StorageReference mStorageRef;
    public Uri imguri;

    //This broadcast receiver is necessary to get user permissions to access the attached USB device
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";


    //This message handler is used to access local resources not
    //accessible by SGFingerPresentCallback() because it is called by
    //a separate thread.
    @SuppressLint("HandlerLeak")
    public Handler fingerDetectedHandler = new Handler(){
        // @Override
        @RequiresApi(api = Build.VERSION_CODES.O)
        public void handleMessage(Message msg) {
            //Handle the message
            CaptureFingerPrint();
            if (mAutoOnEnabled) {
                EnableControls();
            }
        }
    };


    public void EnableControls(){
        this.mButtonCapture.setClickable(true);
        this.mButtonCapture.setTextColor(getResources().getColor(android.R.color.white));
        this.mButtonLed.setClickable(true);
        this.mButtonLed.setTextColor(getResources().getColor(android.R.color.white));
    }


    public void DisableControls(){
        this.mButtonCapture.setClickable(false);
        this.mButtonCapture.setTextColor(getResources().getColor(android.R.color.black));
        this.mButtonLed.setClickable(false);
        this.mButtonLed.setTextColor(getResources().getColor(android.R.color.black));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        ActionBar bar = getActionBar();
        bar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#2a1735")));
        setTitle("BAS");

        mButtonCapture = (Button)findViewById(R.id.buttonCapture99);
        mButtonCapture.setOnClickListener(this);
        mButtonLed = (Button)findViewById(R.id.buttonLedOn99);
        mButtonLed.setOnClickListener(this);
        mTextViewResult = (android.widget.TextView)findViewById(R.id.textViewResult99);
        mImageViewFingerprint = (ImageView)findViewById(R.id.imageViewFingerprint99);
        Name = (EditText) findViewById(R.id.editName);
        Amt = (EditText) findViewById(R.id.editAmt);
        registerBtn = (Button)findViewById(R.id.registerBtn);
        Email=(EditText) findViewById(R.id.email);
       // pinInput=(EditText) findViewById(R.id.pinInput);

        //Getting Dropdown id
        spinnerYear=(Spinner) findViewById(R.id.spinnerYear);
        spinnerDept=(Spinner) findViewById(R.id.spinnerDept);
        spinnerSec=(Spinner) findViewById(R.id.spinnerSection);


//
////        ArrayAdapter<CharSequence> dataAdapter = ArrayAdapter.createFromResource(this, R.array.YEAR, android.R.layout.simple_spinner_item);
////        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
////        spinnerYear.setAdapter(dataAdapter);
//        spinnerYear.setOnItemSelectedListener(this);
//
//
////        ArrayAdapter<CharSequence> dataAdapter1 = ArrayAdapter.createFromResource(this, R.array.DEPT, android.R.layout.simple_spinner_item);
////        dataAdapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
////        spinnerDept.setAdapter(dataAdapter1);
//        spinnerDept.setOnItemSelectedListener(this);
//
////        ArrayAdapter<CharSequence> dataAdapter2 = ArrayAdapter.createFromResource(this, R.array.SEC, android.R.layout.simple_spinner_item);
////        dataAdapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
////        spinnerSec.setAdapter(dataAdapter2);



        ArrayAdapter<CharSequence> adapterYear;
        ArrayAdapter<CharSequence> adapterDept;
        ArrayAdapter<CharSequence> adapterSec;

        adapterYear =    new ArrayAdapter<CharSequence>(this,android.R.layout.simple_spinner_item,YearArr);
        adapterYear.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerYear.setAdapter(adapterYear);

        adapterDept =    new ArrayAdapter<CharSequence>(this,android.R.layout.simple_spinner_item,DeptArr);
        adapterDept.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDept.setAdapter(adapterDept);

        adapterSec=     new ArrayAdapter<CharSequence>(this,android.R.layout.simple_spinner_item,SecArr);
        adapterSec.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSec.setAdapter(adapterSec);

        mStorageRef = FirebaseStorage.getInstance().getReference("Images");


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
        mMaxTemplateSize = new int[1];

        //USB Permissions
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        filter = new IntentFilter(ACTION_USB_PERMISSION);
        sgfplib = new JSGFPLib((UsbManager)getSystemService(Context.USB_SERVICE));
        bSecuGenDeviceOpened = false;
        usbPermissionRequested = false;

        mLed = false;
        mAutoOnEnabled = false;
        autoOn = new SGAutoOnEventNotifier (sgfplib, this);

        member=new Member();
        reff= FirebaseDatabase.getInstance().getReference().child("Member");
        reff.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists())
                    maxid=(dataSnapshot.getChildrenCount());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        registerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(Name.getText().toString().length()==0 && nameStr==null|| Email.getText().toString().length()==0 && email==null|| Amt.getText().toString().length()==0 || mRegisterTemplate==null) {
                    Toast.makeText(RegisterActivity.this, "Fill all details for registration", Toast.LENGTH_SHORT).show();
                }
                else {


                    Year = spinnerYear.getSelectedItem().toString();
                    Dept  = spinnerDept.getSelectedItem().toString();
                    Sec  = spinnerSec.getSelectedItem().toString();
                    nameStr = Name.getText().toString().trim();
                    regNum = Amt.getText().toString().trim();
                    last3regNum = regNum.substring(regNum.length() - 3);

                    email = Email.getText().toString().trim();
                    //int PIN = Integer.valueOf(pinInput.getText().toString().trim());
                    template = uploadToFirebase;


                    member.setNameStr(nameStr);
                    member.setRegNum(regNum);
                    member.setEmail(email);
                    member.setTemplate(template);
                    //member.setPin(PIN);

                    reff.child(Year).child(Dept).child(Sec).child(last3regNum).setValue(member);

                    //TEMP comment ends..
                    Log.d(TAG, "ANS"+nameStr+" "+regNum+" "+email+" "+Year+" "+Dept+" "+Sec);
//
                    Toast.makeText(RegisterActivity.this,"Successfully Registered",Toast.LENGTH_SHORT).show();
                }

            }
        });


    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onPause() {
        Log.d(TAG, "Enter onPause()");

        mRegisterTemplate = null;

        if (bSecuGenDeviceOpened)
        {
            autoOn.stop();
            EnableControls();
            sgfplib.CloseDevice();
            bSecuGenDeviceOpened = false;
        }

        mImageViewFingerprint.setImageBitmap(grayBitmap);
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
        long error = sgfplib.Init( SGFDxDeviceName.SG_DEV_AUTO);
        if (error != SGFDxErrorCode.SGFDX_ERROR_NONE){
            AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
            if (error == SGFDxErrorCode.SGFDX_ERROR_DEVICE_NOT_FOUND)
                dlgAlert.setMessage("The attached fingerprint device is not supported on Android");
            else
                dlgAlert.setMessage("Fingerprint device initialization failed!");
            dlgAlert.setTitle("Hardware Not Found");
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
                        usbPermissionRequested = true;
                        sgfplib.GetUsbManager().requestPermission(usbDevice, mPermissionIntent);
                    }
                    else
                    {
                        //wait up to 20 seconds for the system to grant USB permission
                        hasPermission = sgfplib.GetUsbManager().hasPermission(usbDevice);
                        //hasPermission = true;
                        //debugMessage("Waiting for USB Permission\n");
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

                        }
                    }
                }
                if (hasPermission) {

                    error = sgfplib.OpenDevice(0);
                    if (error == SGFDxErrorCode.SGFDX_ERROR_NONE)
                    {
                        bSecuGenDeviceOpened = true;
                        SecuGen.FDxSDKPro.SGDeviceInfoParam deviceInfo = new SecuGen.FDxSDKPro.SGDeviceInfoParam();
                        error = sgfplib.GetDeviceInfo(deviceInfo);
                        //debugMessage("GetDeviceInfo() ret: " + error + "\n");
                        mImageWidth = deviceInfo.imageWidth;
                        mImageHeight= deviceInfo.imageHeight;

                        sgfplib.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794);
                        sgfplib.GetMaxTemplateSize(mMaxTemplateSize);

                        mRegisterTemplate = new byte[(int)mMaxTemplateSize[0]];


                        EnableControls();

                        if (mAutoOnEnabled){
                            autoOn.start();
                            DisableControls();
                        }
                    }

                }

            }
        }
        Log.d(TAG, "Exit onResume()");
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onDestroy() {
        Log.d(TAG, "Enter onDestroy()");
        mRegisterTemplate = null;
//        sgfplib.CloseDevice();
//        sgfplib.Close();
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

        try {
            File myFile = new File("/sdcard/Download/" + fileName);
            myFile.createNewFile();
            FileOutputStream fOut = new FileOutputStream(myFile);
            fOut.write(buffer,0,buffer.length);
            Toast.makeText(RegisterActivity.this,"Downloaded",Toast.LENGTH_SHORT).show();
            fOut.close();
        } catch (Exception e) {
            Log.d("D","Exception when writing file" + fileName);
        }

    }



    public void SGFingerPresentCallback (){
        autoOn.stop();
        fingerDetectedHandler.sendMessage(new Message());
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    public void CaptureFingerPrint(){
//
//        Log.d("jajajajajaj", "Im getting exxt");
//        long dwTimeStart = 0, dwTimeEnd = 0, dwTimeElapsed = 0;
//        byte[] buffer = new byte[mImageWidth*mImageHeight];
//        dwTimeStart = System.currentTimeMillis();
//        //long result = sgfplib.GetImage(buffer);
//        long result = sgfplib.GetImageEx(buffer, IMAGE_CAPTURE_TIMEOUT_MS,IMAGE_CAPTURE_QUALITY);
//
//
//        long nfiq = sgfplib.ComputeNFIQ(buffer, mImageWidth, mImageHeight);
//        //long nfiq = sgfplib.ComputeNFIQEx(buffer, mImageWidth, mImageHeight,500);
//        NFIQString =  new String("NFIQ="+ nfiq);
//        DumpFile("capture2016.raw", buffer);
//        dwTimeEnd = System.currentTimeMillis();
//        dwTimeElapsed = dwTimeEnd-dwTimeStart;
//
//        mTextViewResult.setText("getImageEx(10000,50) ret: " + result + " [" + dwTimeElapsed + "ms] " + NFIQString +"\n");
//        mImageViewFingerprint.setImageBitmap(this.toGrayscale(buffer));
//
////        Log.d("lwelele", Arrays.toString(buffer));
////        fpimg = Arrays.toString(buffer);
//
//        UploadTask uploadTask = mStorageRef.putBytes(buffer);
//        uploadTask.addOnFailureListener(new OnFailureListener() {
//            @Override
//            public void onFailure(@NonNull Exception exception) {
//                Log.d("D","Not uploaded");
//            }
//        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
//            @Override
//            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
//                String Uri = taskSnapshot.getMetadata().getReference().getDownloadUrl().toString();
//                Log.d("D","Not uploaded");
//            }
//        });
//
//        buffer = null;




        //DEBUG Log.d(TAG, "Clicked REGISTER");
        if (mRegisterImage != null)
            mRegisterImage = null;
        mRegisterImage = new byte[mImageWidth*mImageHeight];

//        this.mCheckBoxMatched.setChecked(false);
//        dwTimeStart = System.currentTimeMillis();
        long result = sgfplib.GetImageEx(mRegisterImage, IMAGE_CAPTURE_TIMEOUT_MS,IMAGE_CAPTURE_QUALITY);
        DumpFile("register.raw", mRegisterImage);
//        dwTimeEnd = System.currentTimeMillis();
//        dwTimeElapsed = dwTimeEnd-dwTimeStart;
//        debugMessage("GetImageEx() ret:" + result + " [" + dwTimeElapsed + "ms]\n");
        mImageViewFingerprint.setImageBitmap(this.toGrayscale(mRegisterImage));
//        dwTimeStart = System.currentTimeMillis();
        result = sgfplib.SetTemplateFormat(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794);
//        dwTimeEnd = System.currentTimeMillis();
//        dwTimeElapsed = dwTimeEnd-dwTimeStart;
//        debugMessage("SetTemplateFormat(ISO19794) ret:" +  result + " [" + dwTimeElapsed + "ms]\n");

        int quality1[] = new int[1];
        result = sgfplib.GetImageQuality(mImageWidth, mImageHeight, mRegisterImage, quality1);
        //debugMessage("GetImageQuality() ret:" +  result + "quality [" + quality1[0] + "]\n");

        SGFingerInfo fpInfo = new SGFingerInfo();
        fpInfo.FingerNumber = 1;
        fpInfo.ImageQuality = quality1[0];
        fpInfo.ImpressionType = SGImpressionType.SG_IMPTYPE_LP;
        fpInfo.ViewNumber = 1;

        for (int i=0; i< mRegisterTemplate.length; ++i)
            mRegisterTemplate[i] = 0;
        result = sgfplib.CreateTemplate(fpInfo, mRegisterImage, mRegisterTemplate);
        DumpFile("register.min", mRegisterTemplate);

        int[] size = new int[1];
        result = sgfplib.GetTemplateSize(mRegisterTemplate, size);
        Log.d("RegisterAct",mRegisterTemplate.toString());

        uploadToFirebase= Arrays.toString(mRegisterTemplate);


//        uploadToFirebase=new String(mRegisterTemplate);
        Log.d("RegisterAct",Arrays.toString(mRegisterTemplate));



        //Printing byte Array
//        PrintStream ps = new PrintStream(System.out);
//
//        // write bytes 1-3
//        System.out.println("here here here");
//        String
//        Log.d("lolololo",);



        //Printing
//        String s1,s2,s3;
//        s1=Arrays.toString(mRegisterTemplate);   //best
//        s2= new String(mRegisterTemplate);
//        s3=Base64.getEncoder().encodeToString(mRegisterTemplate);
//
//
//        Log.d("TestToarraysbest",s1);
//        Log.d("TestToarrays",s2);
//        Log.d("TestToarrays",s3);
//
//        byte [] b1,b2,b3;
//
//        b1=s1.getBytes();
//        b2=s2.getBytes();
//        b3=Base64.getDecoder().decode(s3);  //best
//
//        Log.d("TestToarrays",Arrays.toString(b1));
//        Log.d("TestToarrays",Arrays.toString(b2));
//        Log.d("TestToarraysbest",Arrays.toString(b3));



        //loop for printing template

//        String out;
//        for (int i=0;i<264;i++)
//        {
//            out=out+
//        }
//
//        Log.d("Test111",uploadToFirebase);
//
//        Log.d("testType111",uploadToFirebase.getClass().getSimpleName());
//
//        byte [] downloadFrmFirebase= Base64.getDecoder().decode(uploadToFirebase);
//
//        Log.d("test222",Arrays.toString(downloadFrmFirebase));
//
//        Log.d("testType222",downloadFrmFirebase.getClass().getSimpleName());

        mTextViewResult.setText("Click Verify");
        mRegisterImage = null;
        fpInfo = null;

    }

    private String getExtension(Uri uri)
    {
        ContentResolver cr = getContentResolver();
        MimeTypeMap mimeTypeMap= MimeTypeMap.getSingleton();
        return mimeTypeMap.getExtensionFromMimeType(cr.getType(uri));
    }

    private void Fileuploader(){

//        StorageReference Ref=mStorageRef.child(System.currentTimeMillis()+"."+getExtension(imguri));
//        Log.d("RRRRR",imguri.toString());
//        Ref.putFile(imguri)
//
//                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
//                    @Override
//                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
//                        // Get a URL to the uploaded content
//                        //Uri downloadUrl = taskSnapshot.getDownloadUrl();
//                        Toast.makeText(RegisterActivity.this,"Image Uploaded successfully",Toast.LENGTH_SHORT).show();
//                    }
//                })
//                .addOnFailureListener(new OnFailureListener() {
//                    @Override
//                    public void onFailure(@NonNull Exception exception) {
//                        // Handle unsuccessful uploads
//                        // ...
//                    }
//                });


    }

//    private void fileChoose(){
//        Intent intent = new Intent();
//        intent.setType("image/*");
//        intent.setAction(Intent.ACTION_GET_CONTENT);
//        startActivityForResult(intent,1);
//    }

//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        if(requestCode==1 && resultCode==RESULT_OK && data!=null && data.getData()!=null)
//        {
//            imguri=data.getData();
//            mImageViewFingerprint.setImageURI(imguri);
//
//        }
//    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void onClick(View v) {
        long dwTimeStart = 0, dwTimeEnd = 0, dwTimeElapsed = 0;

        if (v == mButtonCapture) {
            CaptureFingerPrint();
        }

//        if (v==choose){
//            fileChoose();
//        }

//        if(v==upload){
//            Fileuploader();
//
//        }

        if (v == mButtonLed) {
            mLed = !mLed;
            dwTimeStart = System.currentTimeMillis();
            long result = sgfplib.SetLedOn(mLed);
            dwTimeEnd = System.currentTimeMillis();
            dwTimeElapsed = dwTimeEnd-dwTimeStart;
            mTextViewResult.setText("setLedOn(" + mLed +") ret: " + result + " [" + dwTimeElapsed + "ms]\n");
        }

    }



    public void run() {

        while (true) {

        }
    }

}

