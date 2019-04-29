package com.example.ripudaman.measurefirmnessandwp;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    private Button button;
    private Button button2;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button = findViewById(R.id.button);
        button2 = findViewById(R.id.button2);
        FloatingActionButton button4 = (FloatingActionButton)findViewById(R.id.button4);

        button.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                goToSecondActivity();
            }
        });

        button2.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                goToThirdActivity();
            }
        });

        button4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                goToFourthActivity();
            }
        });
        button4.setImageResource(R.drawable.ic_base_info);
    }

    private void goToSecondActivity() {
        Intent intent = new Intent(this, MeasureFirmness.class);

        startActivity(intent);
    }

    private void goToThirdActivity() {
        Intent intent = new Intent(this, MeasureNoise.class);

        startActivity(intent);
    }

    private void goToFourthActivity() {
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle("Bed Firmness And Noise Ambience");
        alertDialog.setMessage("This application can be used to measure firmness of a surface and noise ambience of a room. Made for Tildstar LLC by Ripudaman Tomar and John Gaylor");
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }

}