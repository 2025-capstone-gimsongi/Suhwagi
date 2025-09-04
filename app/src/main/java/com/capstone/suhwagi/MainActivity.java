package com.capstone.suhwagi;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.capstone.suhwagi.databinding.ActivityMainBinding;
import com.capstone.suhwagi.databinding.DialogCallBinding;
import com.google.android.material.textfield.TextInputLayout;

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

        binding.buttonDeafCall.setOnClickListener(v -> {
            String roomName = "dev-room";

            DialogCallBinding callBinding = DialogCallBinding.inflate(getLayoutInflater());
            callBinding.layoutRoomName.setEndIconOnClickListener(view -> {
                ClipboardManager manager = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData data = ClipData.newPlainText("roomName", callBinding.editRoomName.getText());
                manager.setPrimaryClip(data);

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(getApplicationContext(), "클립보드에 복사되었습니다.", Toast.LENGTH_SHORT).show();
                }
            });
            callBinding.editRoomName.setText(roomName);

            new AlertDialog.Builder(MainActivity.this)
                .setTitle("Room")
                .setView(callBinding.getRoot())
                .setPositiveButton("생성", (dialog, which) -> {
                    Intent intent = new Intent(MainActivity.this, CallActivity.class);
                    intent.putExtra("isDeaf", true);

                    startActivity(intent);
                })
                .setNegativeButton("취소", null)
                .setCancelable(false)
                .show();
        });

        binding.buttonHearingCall.setOnClickListener(v -> {
            DialogCallBinding callBinding = DialogCallBinding.inflate(getLayoutInflater());
            callBinding.layoutRoomName.setEndIconMode(TextInputLayout.END_ICON_NONE);
            callBinding.editRoomName.setText("dev-room");

            new AlertDialog.Builder(MainActivity.this)
                .setTitle("Room")
                .setView(callBinding.getRoot())
                .setPositiveButton("참가", (dialog, which) -> {
                    String roomName = callBinding.editRoomName.getText().toString().trim();
                    if (roomName.isEmpty()) {
                        Toast.makeText(getApplicationContext(), "Room 확인 실패", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    startActivity(new Intent(MainActivity.this, CallActivity.class));
                })
                .setNegativeButton("취소", null)
                .show();
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