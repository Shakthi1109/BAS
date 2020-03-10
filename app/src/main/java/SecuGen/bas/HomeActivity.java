package SecuGen.bas;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Environment;
import android.os.Message;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import SecuGen.FDxSDKPro.SGFingerPresentEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.firebase.storage.StorageReference;

import SecuGen.FDxSDKPro.*;

public class HomeActivity extends Activity implements View.OnClickListener, java.lang.Runnable, SGFingerPresentEvent{

    private static final String TAG = "SecuGen USB";
    private static final int IMAGE_CAPTURE_TIMEOUT_MS = 10000;
    private static final int IMAGE_CAPTURE_QUALITY = 50;

    int SUCCESSFLAG;
    int VALIDATEFLAG;
    int SUBMITFLAG;
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


    private byte[] mRegisterImage;
    private byte[] mRegisterTemplate;

    byte [] regFP;  //this and above are used for comparison

    String emailInp;
    EditText EmailInp;


    Button registerBtn,choose,upload;
    EditText Name, Amt;
    TextView hi;
    String nameStr,registeredFpTemplate,TemplateComparisonInput, name,amount;

    String fpimg;

    Member member;
    long maxid=0;
    StorageReference mStorageRef;
    public Uri imguri;

    Button nextbtn, regBtn;
    float payable;

    TextView nameTV, amountTV, templateTV;
    Button buttonRetrive;
    DatabaseReference reff;

    //Spinner input
    String[] YearArr = {"First", "Second", "Third", "Fourth"};
    String[] DeptArr = {"CSE","MECH","ECE","MBA"};
    String[] SecArr = {"A", "B","C","D","E"};

    public String path = Environment.getExternalStorageDirectory().getAbsolutePath()+"/BAS_ATTENDANCE/";
    //Spinner Dropdown
    Spinner spinnerYear, spinnerDept,spinnerSec;

    //String for spinner
    String Year, Dept, Sec;

    Button paybtn;

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

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
        setContentView(R.layout.activity_home);

        ActionBar bar = getActionBar();
        bar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#2a1735")));
        setTitle("BAS");

        mButtonCapture = (Button)findViewById(R.id.scanFpBtn);
        mButtonCapture.setOnClickListener(this);
        mButtonLed = (Button)findViewById(R.id.buttonLedOnPayment);
        mButtonLed.setOnClickListener(this);
        mImageViewFingerprint = (ImageView)findViewById(R.id.imageViewPayment);


        nextbtn = (Button) findViewById(R.id.nextbtn);
        paybtn = (Button) findViewById(R.id.paybtn);
        regBtn = (Button) findViewById(R.id.regBtn);
        //cost = (EditText) findViewById(R.id.cost);
        EmailInp=(EditText) findViewById(R.id.regNumHome);

        nameTV = (TextView) findViewById(R.id.textViewName);
        hi=(TextView) findViewById(R.id.hi);
        amountTV = (TextView) findViewById(R.id.textViewAmount);
//       templateTV = (TextView) findViewById(R.id.textViewTemplate);
        buttonRetrive = (Button) findViewById(R.id.buttonRetrive);

        spinnerYear=(Spinner) findViewById(R.id.spinnerYearHome);
        spinnerDept=(Spinner) findViewById(R.id.spinnerDeptHome);
        spinnerSec=(Spinner) findViewById(R.id.spinnerSectionHome);

        //Adding pf scan
        SUCCESSFLAG=0;
        SUBMITFLAG=0;
        VALIDATEFLAG=0;
        // ends here

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


        File dir = new File(path);
        dir.mkdirs();

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


        @SuppressLint("SimpleDateFormat") SimpleDateFormat formatter = new SimpleDateFormat("dd_MM_yyyy");
        Date now = new Date();
        String fileName = formatter.format(now) + "_ATTENDANCE.txt";//like 2016_01_12.txt

        //Log.d(TAG, "onClick: "+fileName);


        //Storing values to txt file
        File file = new File (path+fileName);

        DateFormat dateFormat = new SimpleDateFormat("hh.mm aa");
        String dateString = dateFormat.format(new Date()).toString();
        //Log.d(TAG, "SEEEEE "+dateString);

        String write = Year+" "+Dept+" "+Sec;
        //Log.d(TAG, "SEEE2 "+write);
        if(dateFormat.format(new Date()).toString().equals("08.00 AM"))
        {
            heading(file,write+" FIRST HOUR");
        }
        else if(dateFormat.format(new Date()).toString().equals("08.50 AM"))
        {
            heading(file,write+" SECOND HOUR");
        }
        else if(dateFormat.format(new Date()).toString().equals("10.00 AM"))
        {
            heading(file,write+" THIRD HOUR");
        }
        else if(dateFormat.format(new Date()).toString().equals("10.50 AM"))
        {
            heading(file,write+" FOURTH HOUR");
        }
        else if(dateFormat.format(new Date()).toString().equals("12.30 PM"))
        {
            heading(file,write+" FIFTH HOUR");
        }
        else if(dateFormat.format(new Date()).toString().equals("01.20 PM"))
        {
            heading(file,write+" SIXTH HOUR");
        }
        else if(dateFormat.format(new Date()).toString().equals("02.10 PM"))
        {
            heading(file,write+" SEVENTH HOUR");
        }

        buttonRetrive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SUCCESSFLAG=0;
                if(EmailInp.getText().toString().length()==0 && emailInp==null)
                {
                    Toast.makeText(HomeActivity.this,"Fill all details!",Toast.LENGTH_SHORT).show();
                }
                else {
                    SUBMITFLAG=1;

                    Year = spinnerYear.getSelectedItem().toString();
                    Dept  = spinnerDept.getSelectedItem().toString();
                    Sec  = spinnerSec.getSelectedItem().toString();
                    emailInp = EmailInp.getText().toString().trim();

                    Log.d(TAG, "nowcheck: " + emailInp);
                    reff = FirebaseDatabase.getInstance().getReference().child("Member").child(Year).child(Dept).child(Sec).child(emailInp);
                    reff.addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            name = dataSnapshot.child("nameStr").getValue().toString();
                            amount = dataSnapshot.child("regNum").getValue().toString();
                            registeredFpTemplate = dataSnapshot.child("template").getValue().toString();
//                        Log.d("Redistered fp template",registeredFpTemplate);

                            String a[] = registeredFpTemplate.split(", ");
                            regFP = new byte[a.length];
                            for (int i = 0; i < a.length; i++) {
                                //Log.d(TAG, "onDataChange: " + a[i]);
                                if (i == 0) {
                                    regFP[i] = Byte.valueOf(a[i].substring(1));

                                } else if (i == a.length - 1) {
                                    regFP[i] = Byte.valueOf(a[i].substring(0, a[i].length() - 1));

                                } else {
                                    regFP[i] = Byte.valueOf(a[i]);
                                }
                            }
                            Log.d(TAG, "array from db: " + Arrays.toString(regFP));


                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {

                        }
                    });
                }
            }
        });


        mButtonLed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLed = !mLed;
                //dwTimeStart = System.currentTimeMillis();
                long result = sgfplib.SetLedOn(mLed);
//                dwTimeEnd = System.currentTimeMillis();
//                dwTimeElapsed = dwTimeEnd-dwTimeStart;
//            mTextViewResult.setText("setLedOn(" + mLed +") ret: " + result + " [" + dwTimeElapsed + "ms]\n");

            }
        });

        nextbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(SUBMITFLAG==0)
                {
                    Toast.makeText(HomeActivity.this,"Press Submit First!",Toast.LENGTH_SHORT).show();
                }
                else {
                    matchingFingerprint(mRegisterTemplate, regFP);
                }
            }
        });

        paybtn.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onClick(View v) {
                if (VALIDATEFLAG==0)
                {
                    Toast.makeText(HomeActivity.this,"Press Validate First",Toast.LENGTH_SHORT).show();
                }
                else {
//                    String message = emailInp + "    " + regNum;
//
//                    Intent intent = new Intent(HomeActivity.this, pinActivity.class);
//                    intent.putExtra("message", message);
//                    startActivity(intent);





                    //Setting date as file name
                    @SuppressLint("SimpleDateFormat") SimpleDateFormat formatter = new SimpleDateFormat("dd_MM_yyyy");
                    Date now = new Date();
                    String fileName = formatter.format(now) + "_ATTENDANCE.txt";//like 2016_01_12.txt

                    Log.d(TAG, "onClick: "+fileName);


                    //Storing values to txt file
                    File file = new File (path+fileName);

                    DateFormat dateFormat = new SimpleDateFormat("hh.mm aa");
                    String dateString = dateFormat.format(new Date()).toString();
                    Log.d(TAG, "SEEEEE "+dateString);

                    String Date;

                    String write = Year+" "+Dept+" "+Sec+" "+amount+" "+name+" "+dateString;
                    Log.d(TAG, "SEEE2 "+write);
                    try
                    {
                        //String filename= "MyFile.txt";
                        FileWriter fw = new FileWriter(file,true); //the true will append the new data
                        fw.write(write+"\n");//appends the string to the file
                        fw.close();
                    }
                    catch(IOException ioe)
                    {
                        System.err.println("IOException: " + ioe.getMessage());
                    }


                    EmailInp.setText("");
                    mImageViewFingerprint.setImageBitmap(grayBitmap );
                    nameTV.setText("");
                    amountTV.setText("");
                    hi.setText("Next Please");


                    Toast.makeText(HomeActivity.this,"Marked as Present",Toast.LENGTH_SHORT).show();



//                    //String filename="contacts_sid.vcf";
//                    File filelocation = new File(file, fileName);
//                    StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
//                    StrictMode.setVmPolicy(builder.build());
//                    Uri path = Uri.fromFile(filelocation);
//                    Intent emailIntent = new Intent(Intent.ACTION_SEND);
//// set the type to 'email'
//                    emailIntent.setType("vnd.android.cursor.dir/email");
//                    String to[] = {"mail2shakthivel98@gmail.com"};
//                    emailIntent.putExtra(Intent.EXTRA_EMAIL, to);
//// the attachment
//
//                    StrictMode.setVmPolicy(builder.build());
//                    emailIntent.putExtra(Intent.EXTRA_STREAM, path);
//// the mail subject
//                    emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Attendance");
//                    StrictMode.setVmPolicy(builder.build());
//                    startActivity(Intent.createChooser(emailIntent , "Send email..."));


//                    Intent intent = new Intent(Intent.ACTION_SENDTO); // it's not ACTION_SEND
//                    intent.putExtra(Intent.EXTRA_SUBJECT, "Subject of email");
//                    intent.putExtra(Intent.EXTRA_TEXT, "Body of email");
//                    intent.setData(Uri.parse("mailto:mail2shakthivel98@gmail.com")); // or just "mailto:" for blank
//                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // this will make such that when user returns to your app, your app is displayed, instead of the email app.
//                    startActivity(intent);

                    Log.d(TAG, "FFFFFF"+fileName);
                    ShareViaEmail("BAS_ATTENDANCE",fileName,file);
                    Log.d(TAG, "mail:sent");

                    //Save(file,"HAHAHAHA");





//                    String filename = "example.txt";
//
//                    FileOutputStream fos = null;
//
//                    File file = new File(filename);
//
//                    try {
//                        fos = openFileOutput(filename,MODE_PRIVATE);
//                        fos.write("hahahah".getBytes());
//                        Log.d(TAG, "file created:"+"yes");
//                        Toast.makeText(HomeActivity.this,"Saved :"+getFilesDir()+"/"+filename,Toast.LENGTH_LONG).show();
//                    } catch (FileNotFoundException e) {
//                        e.printStackTrace();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    } finally {
//                        if(fos!=null){
//                            try {
//                                fos.close();
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//                        }
//                    }


//                    try
//                    {
//                        File root = new File(Environment.getExternalStorageDirectory()+File.separator+"Music_Folder", "Report Files");
//                        //File root = new File(Environment.getExternalStorageDirectory(), "Notes");
//                        if (!root.exists())
//                        {
//                            root.mkdirs();
//                        }
//                        File gpxfile = new File(root, fileName);
//
//
//                        FileWriter writer = new FileWriter(gpxfile,true);
//                        writer.append("KKKKK"+"\n\n");
//                        writer.flush();
//                        writer.close();
//                        Toast.makeText(HomeActivity.this, "Data has been written to Report File", Toast.LENGTH_LONG).show();
//                    }
//                    catch(IOException e)
//                    {
//                        e.printStackTrace();
//
//                    }
                }
            }
        });
//
//        nextbtn.setOnClickListener(new View.OnClickListener() {
//            @RequiresApi(api = Build.VERSION_CODES.O)
//            @Override
//            public void onClick(View v) {
////                regFP= Base64.getDecoder().decode(registeredFpTemplate);
////                Log.d("Registered PF",Arrays.toString(regFP));
//
//                Log.d(TAG, "from user: "+scannedFpTemplate);
//                Log.d("ANSWER",Integer.toString(SUCCESSFLAG));
//                //JUST FOR NOW COMMENT (TESTING)
//                /
//
//            }
//        });

        regBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent1 = new Intent(HomeActivity.this, RegisterActivity.class);
                startActivity(intent1);
            }
        });



    }


    public void heading(File file, String data) {
        try {
            //String filename= "MyFile.txt";
            FileWriter fw = new FileWriter(file, true); //the true will append the new data
            fw.write(data + "\n");//appends the string to the file
            fw.close();
        } catch (IOException ioe) {
            System.err.println("IOException: " + ioe.getMessage());
        }
    }

    private void ShareViaEmail(String folder_name, String file_name,File file) {
        try {
            File root= Environment.getExternalStorageDirectory();
            String filelocation= root.getAbsolutePath() + "/"+folder_name + "/" + file_name;
            Log.d(TAG, "ShareViaEmail: "+filelocation);
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setType("message/rfc822");
            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));

           // String message="File to be shared is " + file_name + ".";
//            intent.setData(Uri.parse("mailto:mail2shakthivel98@gmail.com"));
//            intent.putExtra(Intent.EXTRA_SUBJECT, file_name);
//            intent.putExtra(Intent.EXTRA_TEXT, "PFA");

            String uriText = "mailto:" + Uri.encode("mail2shakthivel98@gmail.com") +
                    "?subject=" + Uri.encode(file_name) +
                    "&body=" + Uri.encode("Greetings, FPA.");

            Uri uri = Uri.parse(uriText);

            intent.setData(uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(intent);
        } catch(Exception e)  {
            Log.d(TAG, "is exception raises during sending mail"+e);
        }
    }


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
                        usbPermissionRequested = true;
                        sgfplib.GetUsbManager().requestPermission(usbDevice, mPermissionIntent);
                    }
                    else
                    {
                        //wait up to 20 seconds for the system to grant USB permission
                        hasPermission = sgfplib.GetUsbManager().hasPermission(usbDevice);
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
        sgfplib.CloseDevice();
        sgfplib.Close();
        super.onDestroy();
        Log.d(TAG, "Exit onDestroy()");
    }

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

    public void DumpFile(String fileName, byte[] buffer)
    {
        //Uncomment section below to dump images and templates to SD card

        try {
            File myFile = new File("/sdcard/Download/" + fileName);
            myFile.createNewFile();
            FileOutputStream fOut = new FileOutputStream(myFile);
            fOut.write(buffer,0,buffer.length);
            Toast.makeText(HomeActivity.this,"Downloaded",Toast.LENGTH_SHORT).show();
            fOut.close();
        } catch (Exception e) {
            Log.d("D","Exception when writing file" + fileName);
        }

    }

    public void SGFingerPresentCallback (){
        autoOn.stop();
        fingerDetectedHandler.sendMessage(new Message());
    }

    void matchingFingerprint(byte [] regFP, byte[] scannedFpTemplate){

        VALIDATEFLAG=1;

            boolean[] matched = new boolean[1];
            //dwTimeStart = System.currentTimeMillis();
            sgfplib.MatchTemplate(regFP,scannedFpTemplate,SGFDxSecurityLevel.SL_NORMAL, matched);

            Log.d("output user",Arrays.toString(scannedFpTemplate));
            Log.d("output data",Arrays.toString(regFP));


//			TextView test = (TextView) findViewById(R.id.test);
//			test.setText(Arrays.toString(mRegisterTemplate));

//            dwTimeEnd = System.currentTimeMillis();
//            dwTimeElapsed = dwTimeEnd-dwTimeStart;
//            debugMessage("MatchTemplate() ret:" + result+ " [" + dwTimeElapsed + "ms]\n");
            if (matched[0]) {
                Toast.makeText(HomeActivity.this,"MATCHED",Toast.LENGTH_SHORT).show();

                hi.setText(R.string.hi);
                nameTV.setText(name);
                amountTV.setText(R.string.sufficient);




                   // payable = Float.valueOf(cost.getText().toString().trim());


//                    //amountTV.setText(amount);
//                amtFloat=Float.valueOf(amount);
//                    if(payable<amtFloat)
//                    {
//                        hi.setText(R.string.hi);
//                        nameTV.setText(name);
//                        amountTV.setText(R.string.sufficient);
//                        amtFloat=amtFloat-payable;
//                    }
//                    else
//                    {
//                        amountTV.setText(R.string.notSufficient);
//                        VALIDATEFLAG=0;
//                    }
            }
            else {
                Toast.makeText(HomeActivity.this,"NOT MATCHED",Toast.LENGTH_SHORT).show();
                VALIDATEFLAG=0;
            }
        }


    @RequiresApi(api = Build.VERSION_CODES.O)
    public void CaptureFingerPrint(){

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
          sgfplib.GetImageEx(mRegisterImage, IMAGE_CAPTURE_TIMEOUT_MS,IMAGE_CAPTURE_QUALITY);
//        DumpFile("register.raw", mRegisterImage);
//        dwTimeEnd = System.currentTimeMillis();
//        dwTimeElapsed = dwTimeEnd-dwTimeStart;
//        debugMessage("GetImageEx() ret:" + result + " [" + dwTimeElapsed + "ms]\n");
        mImageViewFingerprint.setImageBitmap(this.toGrayscale(mRegisterImage));
//        dwTimeStart = System.currentTimeMillis();
        sgfplib.SetTemplateFormat(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794);
//        dwTimeEnd = System.currentTimeMillis();
//        dwTimeElapsed = dwTimeEnd-dwTimeStart;
//        debugMessage("SetTemplateFormat(ISO19794) ret:" +  result + " [" + dwTimeElapsed + "ms]\n");

        int quality1[] = new int[1];
        sgfplib.GetImageQuality(mImageWidth, mImageHeight, mRegisterImage, quality1);
        //debugMessage("GetImageQuality() ret:" +  result + "quality [" + quality1[0] + "]\n");

        SGFingerInfo fpInfo = new SGFingerInfo();
        fpInfo.FingerNumber = 1;
        fpInfo.ImageQuality = quality1[0];
        fpInfo.ImpressionType = SGImpressionType.SG_IMPTYPE_LP;
        fpInfo.ViewNumber = 1;

        for (int i=0; i< mRegisterTemplate.length; ++i)
            mRegisterTemplate[i] = 0;
        sgfplib.CreateTemplate(fpInfo, mRegisterImage, mRegisterTemplate);
        DumpFile("register.min", mRegisterTemplate);

        int[] size = new int[1];
        sgfplib.GetTemplateSize(mRegisterTemplate, size);
        Log.d("RegisterAct",mRegisterTemplate.toString());

//        uploadToFirebase= android.util.Base64.encodeToString(mRegisterTemplate, android.util.Base64.DEFAULT);

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

        mRegisterImage = null;
        fpInfo = null;

    }


    @SuppressLint("SetTextI18n")
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void onClick(View v) {
        long dwTimeStart = 0, dwTimeEnd = 0, dwTimeElapsed = 0;

        if (v == mButtonCapture) {
            CaptureFingerPrint();
        }


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
