package com.googledriveapp.activity

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import com.googledriveapp.R
import com.googledriveapp.adapter.FilesAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

class MainActivity : AppCompatActivity() {
    private lateinit var driveService: Drive
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FilesAdapter
    private val RC_SIGN_IN = 9001
    private val RC_OPEN_FILE = 9002
    private val READ_EXTERNAL_STORAGE_PERMISSION_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = FilesAdapter(mutableListOf())
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.login_button).setOnClickListener {
            signIn()
        }

        findViewById<Button>(R.id.upload_button).setOnClickListener {
            checkAndRequestPermission()
        }
    }

    private fun signIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

        val signInClient = GoogleSignIn.getClient(this, gso)
        startActivityForResult(signInClient.signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        } else if (requestCode == RC_OPEN_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                uploadFile(uri)
            }
        }
    }

    private fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult(ApiException::class.java)
            setupDriveService(account!!)
            listFiles()
            findViewById<Button>(R.id.upload_button).visibility = Button.VISIBLE
            findViewById<RecyclerView>(R.id.recyclerView).visibility = RecyclerView.VISIBLE
        } catch (e: ApiException) {
            Log.w("SignInActivity", "signInResult:failed code=" + e.statusCode)
        }
    }

    private fun setupDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            this, listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account

        driveService = Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory(),
            credential
        ).setApplicationName(getString(R.string.app_name)).build()
    }

    private fun listFiles() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result: FileList = driveService.files().list()
                    .setPageSize(10)
                    .setFields("nextPageToken, files(id, name)")
                    .execute()

                val files: List<File> = result.files
                withContext(Dispatchers.Main) {
                    if (files != null) {
                        adapter.updateFiles(files)
                    }
                }
            } catch (e: IOException) {
                Log.e("MainActivity", "An error occurred: $e")
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, RC_OPEN_FILE)
    }

    private fun uploadFile(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val fileMetadata = File()
            fileMetadata.name = "MyFile"
            val mediaContent = InputStreamContent("application/octet-stream", inputStream)

            try {
                val file: File = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
                Log.i("MainActivity", "File ID: ${file.id}")
            } catch (e: IOException) {
                Log.e("MainActivity", "An error occurred: $e")
            }
        }
    }

    private fun checkAndRequestPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                READ_EXTERNAL_STORAGE_PERMISSION_CODE
            )
        } else {
            openFilePicker()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == READ_EXTERNAL_STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openFilePicker()
            }
        }
    }

}
