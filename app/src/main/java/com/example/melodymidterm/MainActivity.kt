package com.example.melodymidterm

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import repository.UserRepository


import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.compose.rememberImagePainter
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.gson.Gson

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.io.File

//import com.google.gson.Gson
//import com.google.gson.reflect.TypeToken

import model.Post

//import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex

//import androidx.compose.material.Icon


class MainActivity : ComponentActivity() {
    lateinit var userRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use the database from MyApp
        val database = (application as MyApp).database

        userRepository = UserRepository(database.userDao())

        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                App(userRepository) // Pass userRepository
            }
        }
    }
}

@Composable
fun App(userRepository: UserRepository) {
    val navController = rememberNavController()
    val isAuthenticated = remember { mutableStateOf(false) }
    val username = remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val generalPosts = remember { mutableStateListOf<Post>() }

    Scaffold(
        bottomBar = {
            if (isAuthenticated.value) {
                BottomNavigationBar(navController, isAuthenticated, username.value, snackbarHostState)
            }
        },
        content = { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                NavHost(
                    navController = navController,
                    startDestination = "login"
                ) {
                    composable("login") {
                        LoginScreen(navController, isAuthenticated, username, userRepository, snackbarHostState)
                    }
                    composable("signup") {
                        SignUpScreen(navController, isAuthenticated, userRepository)
                    }

                    composable("profile/{username}") { backStackEntry ->
                        val usernameArgument = backStackEntry.arguments?.getString("username") ?: ""
                        ProfileScreen(usernameArgument, generalPosts)
                    }
                    composable("feed") {
                        GeneralFeedScreen(generalPosts)
                    }
                    composable("search") {
                        SearchScreen() // Passing list of all users
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    )
}



@Composable
fun BottomNavigationBar(
    navController: NavController,
    isAuthenticated: MutableState<Boolean>,
    username: String,
    snackbarHostState: SnackbarHostState
) {
    val coroutineScope = rememberCoroutineScope()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination?.route

    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
            label = { Text("Profile") },
            selected = currentDestination?.startsWith("profile") == true,
            onClick = {
                navController.navigate("profile/$username") {
                    launchSingleTop = true
                }
            }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            label = { Text("Search") },
            selected = currentDestination == "search",
            onClick = {
                navController.navigate("search") {
                    launchSingleTop = true
                }
            }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Filled.List, contentDescription = "Feed") },
            label = { Text("Feed") },
            selected = currentDestination == "feed",
            onClick = {
                navController.navigate("feed") {
                    launchSingleTop = true
                }
            }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.ExitToApp, contentDescription = "Logout") },
            label = { Text("Logout") },
            selected = false,
            onClick = {
                coroutineScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Are you sure you want to log out of your account?",
                        actionLabel = "Yes",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        isAuthenticated.value = false
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }
        )
    }
}
@Composable
fun ProfileScreen(username: String, generalPosts: SnapshotStateList<Post>) {
    val context = LocalContext.current

    // Load bio from SharedPreferences
    val sharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    var bio by remember { mutableStateOf(sharedPreferences.getString("bio_$username", "") ?: "") }
    var isEditingBio by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var description by remember { mutableStateOf("") }

    var fullScreenImageUri by remember { mutableStateOf<Uri?>(null) }
    var currentFullScreenPost by remember { mutableStateOf<Post?>(null) }

    val gson = Gson()
    val savedPostsJson = sharedPreferences.getString("posts_$username", "[]")
    val savedPosts: List<Post> = gson.fromJson(savedPostsJson, object : TypeToken<List<Post>>() {}.type)
    val posts = remember { mutableStateListOf(*savedPosts.toTypedArray()) }

    // Use LaunchedEffect to load posts when the screen loads
    LaunchedEffect(Unit) {
        val savedPostsJson = sharedPreferences.getString("posts_$username", "[]")
        val savedPosts: List<Post> = gson.fromJson(savedPostsJson, object : TypeToken<List<Post>>() {}.type)
        posts.clear()  // Clear any existing posts to avoid duplication
        posts.addAll(savedPosts)  // Add saved posts to the list
    }

    // Launcher to pick an image from the gallery
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        photoUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        // Profile Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Image
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Profile Picture",
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Username and Bio
            Column {
                Text(
                    text = username,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Posts: ${posts.size}, Followers: 0, Following: 0",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isEditingBio) {
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("Bio") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        maxLines = 3,
                        trailingIcon = {
                            IconButton(onClick = {
                                // Save bio to SharedPreferences
                                sharedPreferences.edit().putString("bio_$username", bio).apply()
                                isEditingBio = false
                            }) {
                                Icon(Icons.Default.Check, contentDescription = "Save Bio")
                            }
                        }
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = bio,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 3,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .weight(1f)  // Allow bio to take up available space
                        )
                        IconButton(onClick = { isEditingBio = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Bio")
                        }
                    }
                }
            }
        }

        // Add New Post Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Image")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add new post", style = MaterialTheme.typography.bodyLarge)
            }
        }

        // Preview the selected image and add caption option if an image is picked
        if (photoUri != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = rememberImagePainter(photoUri),
                    contentDescription = "Picked Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Write a caption...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    trailingIcon = {
                        IconButton(onClick = {
                            // Optionally save caption before sharing
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save Caption")
                        }
                    }
                )

                // Share Post Button
                Button(
                    onClick = {
                        if (photoUri != null) {
                            // Save the post to internal storage
                            val imagePath = saveImageToInternalStorage(photoUri!!, context)
                            if (imagePath != null) {
                                val newPost =
                                    Post(username, photoUri.toString(), description, imagePath)
                                posts.add(newPost)
                                generalPosts.add(newPost)  // Add post to general feed

                                // Save posts to SharedPreferences
                                savePostsToPreferences(context, "posts_$username", posts)
                                savePostsToPreferences(context, "general_posts", generalPosts)

                                // Clear input fields
                                description = ""
                                photoUri = null
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text("Share Post")
                }
            }
        }

        // Posts Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(posts) { post: Post ->
                val painter = if (post.path != null) {
                    rememberImagePainter(File(post.path))
                } else {
                    rememberImagePainter(post.imageUri)
                }

                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            // Set the clicked image to full screen
                            fullScreenImageUri = if (post.path != null) {
                                Uri.fromFile(File(post.path))
                            } else {
                                Uri.parse(post.imageUri)
                            }
                            currentFullScreenPost = post
                        }
                )
            }
        }

        // Full-screen image preview with delete button
        if (fullScreenImageUri != null && currentFullScreenPost != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .zIndex(1f) // Make sure the full-screen image appears above everything else
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween // Space elements from top to bottom of the screen
                ) {
                    // Close Full-Screen Button (Top-Right)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        IconButton(
                            onClick = {
                                fullScreenImageUri = null
                                currentFullScreenPost = null
                            },
                            modifier = Modifier
                                .padding(16.dp)
                                .size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Full Screen",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Full-Screen Image in the Middle
                    AsyncImage(
                        model = fullScreenImageUri,
                        contentDescription = "Full Screen Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )

                    // Delete Post Button at the Bottom
                    Button(
                        onClick = {
                            currentFullScreenPost?.let { postToDelete ->
                                // Remove from profile posts and general feed
                                posts.remove(postToDelete)
                                generalPosts.remove(postToDelete)

                                // Update SharedPreferences for both profile posts and general feed
                                savePostsToPreferences(context, "posts_$username", posts)
                                savePostsToPreferences(context, "general_posts", generalPosts)

                                // Close full-screen view
                                fullScreenImageUri = null
                                currentFullScreenPost = null
                            }
                        },
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .height(50.dp)
                            .fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Post", color = MaterialTheme.colorScheme.onError)
                    }
                }
            }
        }






    }
}



private fun savePostsToPreferences(context: Context, key: String, posts: List<Post>) {
    val sharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    val gson = Gson()
    val postsJson = gson.toJson(posts)
    sharedPreferences.edit().putString(key, postsJson).apply()
}

private fun saveImageToInternalStorage(uri: Uri, context: Context): String? {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val fileName = "post_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, fileName)
        inputStream?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

data class Post(val username: String, val imageUri: String, val description: String, val path: String? = null)

@Composable
fun GeneralFeedScreen(generalPosts: SnapshotStateList<Post>) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    val gson = Gson()

    // Use LaunchedEffect to load posts from SharedPreferences when the screen loads
    LaunchedEffect(Unit) {
        val savedPostsJson = sharedPreferences.getString("general_posts", "[]")
        val savedPosts: List<Post> = gson.fromJson(savedPostsJson, object : TypeToken<List<Post>>() {}.type)
        generalPosts.clear()
        generalPosts.addAll(savedPosts)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(generalPosts) { post ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "User Profile Picture",
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(post.username, style = MaterialTheme.typography.bodyLarge)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val painter = if (post.path != null) {
                        rememberImagePainter(File(post.path))
                    } else {
                        rememberImagePainter(post.imageUri)
                    }

                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(vertical = 8.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )

                    if (post.description.isNotEmpty()) {
                        Text(
                            text = post.description,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}


// Save posts to SharedPreferences function
fun savePostsToPreferences(context: Context, posts: List<Post>) {
    val sharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    val gson = Gson()
    val postsJson = gson.toJson(posts)
    sharedPreferences.edit().putString("general_posts", postsJson).apply()
}


@Composable
fun ProfileStat(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}


// Updated SearchScreen to allow searching and viewing other users' profiles
@Composable
fun SearchScreen() {
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("You are on the Search Screen", fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search users, posts, or tags...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}







@Composable
fun LoginScreen(
    navController: NavController,
    isAuthenticated: MutableState<Boolean>,
    username: MutableState<String>,
    userRepository: UserRepository,
    snackbarHostState: SnackbarHostState
) {
    val inputUsername = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }
    val passwordVisible = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            value = inputUsername.value,
            onValueChange = { inputUsername.value = it }, // Corrected here
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = password.value,
            onValueChange = { password.value = it },
            label = { Text("Password") },
            visualTransformation = if (passwordVisible.value) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible.value = !passwordVisible.value }) {
                    val icon = if (passwordVisible.value) {
                        painterResource(id = R.drawable.openeye)
                    } else {
                        painterResource(id = R.drawable.closedeye)
                    }
                    Icon(painter = icon, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            coroutineScope.launch {
                val result = userRepository.loginUser(inputUsername.value, password.value)
                if (result.isSuccess) {
                    isAuthenticated.value = true
                    username.value = inputUsername.value // Corrected here
                    navController.navigate("profile/${inputUsername.value}") {
                        popUpTo("login") { inclusive = true }
                    }
                } else {
                    snackbarHostState.showSnackbar("Invalid username or password")
                }
            }
        }) {
            Text("Sign In")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Don't have an account?")
        Button(onClick = { navController.navigate("signup") }) {
            Text("Click here to Sign Up")
        }

        SnackbarHost(hostState = snackbarHostState)
    }
}


@Composable
fun SignUpScreen(
    navController: NavController,
    isAuthenticated: MutableState<Boolean>,
    userRepository: UserRepository
) {
    val username = remember { mutableStateOf("") }
    val email = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }
    val confirmPassword = remember { mutableStateOf("") }
    val passwordVisible = remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            value = username.value,
            onValueChange = { username.value = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = email.value,
            onValueChange = { email.value = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = password.value,
            onValueChange = { password.value = it },
            label = { Text("Create Password") },
            visualTransformation = if (passwordVisible.value) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible.value = !passwordVisible.value }) {
                    val icon = if (passwordVisible.value) {
                        painterResource(id = R.drawable.openeye)
                    } else {
                        painterResource(id = R.drawable.closedeye)
                    }
                    Icon(painter = icon, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = confirmPassword.value,
            onValueChange = { confirmPassword.value = it },
            label = { Text("Confirm Password") },
            visualTransformation = if (passwordVisible.value) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            coroutineScope.launch {
                when {
                    !isEmailValid(email.value) -> {
                        snackbarHostState.showSnackbar("Please enter a valid email address")
                    }
                    !isPasswordValid(password.value) -> {
                        snackbarHostState.showSnackbar(
                            "Password must contain at least one capital letter, two numbers, and be 8 characters long"
                        )
                    }
                    password.value != confirmPassword.value -> {
                        snackbarHostState.showSnackbar("Passwords do not match")
                    }
                    else -> {
                        val result = userRepository.registerUser(
                            username.value,
                            email.value,
                            password.value
                        )
                        if (result.isSuccess) {
                            isAuthenticated.value = true
                            navController.navigate("profile/${username.value}") {
                                popUpTo("signup") { inclusive = true }
                            }
                        } else {
                            snackbarHostState.showSnackbar("Registration failed")
                        }
                    }
                }
            }
        }) {
            Text("Sign Up")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Already have an account?")
        Button(onClick = { navController.navigate("login") }) {
            Text("Click here to Log In")
        }

        SnackbarHost(hostState = snackbarHostState)
    }
}

// Helper functions for email and password validation
private fun isEmailValid(email: String): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
}

private fun isPasswordValid(password: String): Boolean {
    return password.length >= 8 &&
            password.any { it.isDigit() } &&
            password.any { it.isUpperCase() }
}

