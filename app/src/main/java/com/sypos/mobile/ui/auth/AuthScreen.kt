package com.sypos.mobile.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthSuccess: (String?, String?) -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val coroutineScope = rememberCoroutineScope()

    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var shopName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(80.dp),
                        color = Color.Transparent
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.sypos.mobile.R.drawable.koyo_logo),
                                contentDescription = "Logo KAYO",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isLoginMode) "Connexion" else "Créer un compte",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isLoginMode) {
                        Text(
                            text = "Profitez de 7 jours d'essai gratuit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Form
                if (!isLoginMode) {
                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        label = { Text("Nom de la Boutique") },
                        leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Numéro de Téléphone") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Adresse Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mot de passe") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(image, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!isLoginMode && shopName.isBlank()) {
                            Toast.makeText(context, "Veuillez entrer le nom de la boutique", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isLoading = true
                        coroutineScope.launch {
                            try {
                                if (isLoginMode) {
                                    auth.signInWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
                                        isLoading = false
                                        if (task.isSuccessful) {
                                            onAuthSuccess(if (isLoginMode) null else shopName, if (isLoginMode) null else phoneNumber)
                                        } else {
                                            Toast.makeText(context, "Erreur: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    auth.createUserWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
                                        isLoading = false
                                        if (task.isSuccessful) {
                                            onAuthSuccess(if (isLoginMode) null else shopName, if (isLoginMode) null else phoneNumber)
                                        } else {
                                            Toast.makeText(context, "Erreur: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                Toast.makeText(context, "Erreur réseau", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text(if (isLoginMode) "Se Connecter" else "Démarrer l'essai gratuit", fontSize = 16.sp)
                    }
                }

                if (isLoginMode) {
                    TextButton(onClick = { 
                        if (email.isNotBlank()) {
                            auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Toast.makeText(context, "Email de réinitialisation envoyé à $email", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Erreur: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Veuillez entrer votre email d'abord", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Mot de passe oublié ?", color = MaterialTheme.colorScheme.primary)
                    }
                }

                TextButton(onClick = { isLoginMode = !isLoginMode }) {
                    Text(
                        if (isLoginMode) "Nouveau sur KAYO ? Créer un compte" 
                        else "Déjà un compte ? Se connecter",
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

