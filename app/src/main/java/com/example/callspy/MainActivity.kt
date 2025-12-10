package com.example.callspy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var startCallButton: Button
    private lateinit var stopCallButton: Button
    private lateinit var viewRecordingsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startCallButton = findViewById(R.id.startCallButton)
        stopCallButton = findViewById(R.id.stopCallButton)
        viewRecordingsButton = findViewById(R.id.viewRecordingsButton)

        startCallButton.setOnClickListener {
            // Existing start call logic
        }

        stopCallButton.setOnClickListener {
            // Existing stop call logic
        }

        viewRecordingsButton.setOnClickListener {
            val intent = Intent(this, FileManagerActivity::class.java)
            startActivity(intent)
        }
    }
}
