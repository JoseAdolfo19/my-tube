package com.miappvideos.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.miappvideos.MainActivity
import com.miappvideos.R

class LoginActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/youtube.readonly"))
            .requestIdToken(getString(R.string.google_signin_client_id))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        findViewById<Button>(R.id.btnGoogleSignIn).setOnClickListener {
            signIn()
        }

        findViewById<Button>(R.id.btnSkip).setOnClickListener {
            navigateToMain(null)
        }

        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            navigateToMain(account)
        }
    }

    private fun signIn() {
        startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                navigateToMain(account)
            } catch (e: ApiException) {
                Toast.makeText(this, "Error: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToMain(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount?) {
        val name = account?.displayName ?: "Invitado"
        val email = account?.email
        val photoUrl = account?.photoUrl?.toString()
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("user_name", name)
            putExtra("user_email", email)
            putExtra("user_photo", photoUrl)
        }
        startActivity(intent)
        finish()
    }
}
