package com.capstone.suhwagi;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.capstone.suhwagi.databinding.ActivityMainBinding;
import com.capstone.suhwagi.databinding.DialogCallBinding;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        requestPermissions();

        DatabaseReference calls = FirebaseDatabase.getInstance().getReference("calls");
        binding.buttonCreateCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String id = calls.push().getKey();
                if (id == null) {
                    Toast.makeText(getApplicationContext(), "ID 생성 실패", Toast.LENGTH_SHORT).show();
                    return;
                }

                DialogCallBinding callBinding = DialogCallBinding.inflate(getLayoutInflater());
                callBinding.layoutCallId.setEndIconOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ClipboardManager manager = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData data = ClipData.newPlainText("id", callBinding.editCallId.getText());
                        manager.setPrimaryClip(data);

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(getApplicationContext(), "클립보드에 복사되었습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                callBinding.editCallId.setText(id);

                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("ID")
                    .setView(callBinding.getRoot())
                    .setPositiveButton("생성", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(MainActivity.this, CallActivity.class);
                            intent.putExtra("isCaller", true);
                            intent.putExtra("id", id);

                            startActivity(intent);
                        }
                    })
                    .setNegativeButton("취소", null)
                    .setCancelable(false)
                    .show();
            }
        });

        binding.buttonJoinCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogCallBinding callBinding = DialogCallBinding.inflate(getLayoutInflater());
                callBinding.layoutCallId.setEndIconMode(TextInputLayout.END_ICON_NONE);
                callBinding.editCallId.setEnabled(true);

                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("ID")
                    .setView(callBinding.getRoot())
                    .setPositiveButton("참가", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Toast toast = Toast.makeText(getApplicationContext(), "ID 확인 실패", Toast.LENGTH_SHORT);

                            String id = callBinding.editCallId.getText().toString().trim();
                            if (id.isEmpty()) {
                                toast.show();
                                return;
                            }

                            calls.child(id).addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot snapshot) {
                                    if (snapshot.exists()) {
                                        Intent intent = new Intent(MainActivity.this, CallActivity.class);
                                        intent.putExtra("id", id);

                                        startActivity(intent);
                                    } else {
                                        toast.show();
                                    }
                                }

                                @Override public void onCancelled(DatabaseError error) { toast.show(); }
                            });
                        }
                    })
                    .setNegativeButton("취소", null)
                    .show();
            }
        });
    }

    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.CAMERA);
        permissions.add(Manifest.permission.RECORD_AUDIO);

        permissions.removeIf(permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        );
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), 0);
        }
    }
}