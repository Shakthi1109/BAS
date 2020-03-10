package SecuGen.bas;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class pinActivity extends AppCompatActivity {

    TextView disp;
    EditText pin;
    Button subBtn;

    int InputPin;
    int DBPin;
    DatabaseReference reff;
    Member member;
    Float balance;
    String email;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);


//        ActionBar bar = getActionBar();
//        assert bar != null;
//        bar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#2a1735")));
//        setTitle("BAS");

        //disp= (TextView) findViewById(R.id.disp);
        pin=(EditText) findViewById(R.id.pin);
        subBtn=(Button)findViewById(R.id.subBtn);

        Bundle bundle = getIntent().getExtras();
        String message=bundle.getString("message");

        //]=disp.setText(message);

        assert message != null;
        String [] msg=message.split("    ");

        email=msg[0];
        Log.d("eeeee",email);
        balance=Float.valueOf(msg[1]);
        Log.d("eeeee",balance.toString());



        member=new Member();
        reff= FirebaseDatabase.getInstance().getReference().child("Member").child(email);
        reff.addValueEventListener(new ValueEventListener() {
           @Override
           public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

               DBPin = Integer.valueOf(dataSnapshot.child("pin").getValue().toString());

               subBtn.setOnClickListener(new View.OnClickListener() {
                   @Override
                   public void onClick(View v) {
                       InputPin = Integer.parseInt(pin.getText().toString());
                       if(InputPin==DBPin){
                           reff.child("regNum").setValue(balance);
                           Log.d("bal",balance.toString());
                           Toast.makeText(pinActivity.this,"Transaction Successful",Toast.LENGTH_SHORT).show();

                           new CountDownTimer(2000, 1000) {
                               public void onFinish() {
                                   finish();
                               }
                               public void onTick(long millisUntilFinished) {
                                   // millisUntilFinished    The amount of time until finished.
                               }
                           }.start();


                       }
                       else{
                           Toast.makeText(pinActivity.this,"Transaction failed. Try again!",Toast.LENGTH_SHORT).show();
                           new CountDownTimer(2000, 1000) {
                               public void onFinish() {
                                   finish();
                               }
                               public void onTick(long millisUntilFinished) {
                                   // millisUntilFinished    The amount of time until finished.
                               }
                           }.start();
                       }
                   }
               });


           }

           @Override
           public void onCancelled(@NonNull DatabaseError databaseError) {

           }
       });







    }

}
