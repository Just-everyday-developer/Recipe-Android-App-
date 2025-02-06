package com.example.laboratory_work_8

import android.content.Context
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.laboratory_work_8.ui.theme.Laboratory_Work_8Theme
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.alpha
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

@SuppressLint("StaticFieldLeak")
val db = FirebaseFirestore.getInstance()

data class Recipe(
    val id: String,
    val name: String,
    val ingredients: String,
    val instructions: String,
    val imageUrl: String = "" // Default empty string for recipes without images
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Laboratory_Work_8Theme {
                App(this)
            }
        }
    }
}

@Composable
fun App(context: Context = LocalContext.current) {  // Add context parameter
    val navController = rememberNavController()
    var showDialog by remember { mutableStateOf(false) }
    var recipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var selectedRecipe by remember { mutableStateOf<Recipe?>(null) }

    NavHost(navController = navController, startDestination = "recipesList") {
        composable(
            route = "recipesList",
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            RecipesListScreen(
                recipes = recipes,
                onRecipeClick = { recipe ->
                    selectedRecipe = recipe
                    navController.navigate("recipeDetails")
                },
                onAddClick = { showDialog = true },
                onDelete = { deletedRecipe ->
                    deleteRecipe(deletedRecipe.id) {
                        recipes = recipes.filter { it.id != deletedRecipe.id }
                    }
                }
            )
        }

        composable(
            route = "recipeDetails",
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth } },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth } }
        ) {
            selectedRecipe?.let { recipe ->
                RecipeDetailsScreen(
                    recipe = recipe,
                    onBackClick = { navController.navigateUp() }
                )
            }
        }
    }

    if (showDialog) {
        RecipeDialog(
            onDismiss = { showDialog = false }
        ) { name, ingredients, instructions, imageUri ->  // Update lambda parameters
            saveToFirebase(name, ingredients, instructions, imageUri, context)  // Pass context
            loadRecipes { newRecipes ->
                recipes = newRecipes
            }
            showDialog = false
        }
    }

    LaunchedEffect(Unit) {
        loadRecipes { newRecipes ->
            recipes = newRecipes
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailsScreen(
    recipe: Recipe,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe.name) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (recipe.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(recipe.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Recipe Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Ингредиенты:",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = recipe.ingredients,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "Инструкция:",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = recipe.instructions,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun RecipesListScreen(
    recipes: List<Recipe>,
    onRecipeClick: (Recipe) -> Unit,
    onAddClick: () -> Unit,
    onDelete: (Recipe) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить рецепт")
            }
        }
    ) { padding ->
        Column {
            Header()
            if (recipes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Нет рецептов. Нажмите + чтобы добавить.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    items(
                        items = recipes,
                        key = { recipe -> recipe.id }
                    ) { recipe ->
                        AnimatedRecipeItem(
                            recipe = recipe,
                            onClick = { onRecipeClick(recipe) },
                            onDelete = { onDelete(recipe) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteContainer(
    item: Recipe,
    onDelete: (Recipe) -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { 200f },
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                onDelete(item)
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = Color.Red
                )
            }
        },
        content = { content() },
    )
}

@Composable
fun AnimatedRecipeItem(
    recipe: Recipe,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(500)) +
                expandVertically(animationSpec = spring(dampingRatio = 0.8f)),
        exit = fadeOut(animationSpec = tween(500)) +
                shrinkVertically(animationSpec = spring(dampingRatio = 0.8f))
    ) {
        SwipeToDeleteContainer(
            item = recipe,
            onDelete = { onDelete() }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                // Содержимое карточки рецепта остается прежним
                RecipeItemContent(recipe)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Header() {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = { Text("Recipes") }
    )
}

@Composable
fun RecipeItem(recipe: Recipe) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = recipe.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Ингредиенты: ${recipe.ingredients}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Инструкция: ${recipe.instructions}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RecipeItemContent(recipe: Recipe) {
    Column(modifier = Modifier.padding(16.dp)) {
        if (recipe.imageUrl.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(recipe.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Recipe Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = recipe.name,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Ингредиенты: ${recipe.ingredients}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 4.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Инструкция: ${recipe.instructions}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun loadRecipes(onSuccess: (List<Recipe>) -> Unit) {
    db.collection("recipes")
        .get()
        .addOnSuccessListener { result ->
            val recipesList = result.map { document ->
                Recipe(
                    id = document.id,
                    name = document.getString("name") ?: "",
                    ingredients = document.getString("ingredients") ?: "",
                    instructions = document.getString("instructions") ?: "",
                    imageUrl = document.getString("imageUrl") ?: ""  // Add imageUrl
                )
            }
            onSuccess(recipesList)
        }
        .addOnFailureListener { e ->
            Log.e("Firestore", "Error loading recipes", e)
            onSuccess(emptyList())
        }
}

fun saveToFirebase(
    name: String,
    ingredients: String,
    instructions: String,
    imageUri: Uri?,
    context: Context
) {
    if (imageUri != null) {
        val storageRef = FirebaseStorage.getInstance().reference
        val imageRef = storageRef.child("recipe_images/${UUID.randomUUID()}")

        val uploadTask = imageRef.putFile(imageUri)
        uploadTask.continueWithTask { task ->
            if (!task.isSuccessful) {
                task.exception?.let { throw it }
            }
            imageRef.downloadUrl
        }.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val downloadUrl = task.result.toString()
                saveRecipeWithImage(name, ingredients, instructions, downloadUrl)
            } else {
                Log.e("Firestore", "Error uploading image", task.exception)
                saveRecipeWithImage(name, ingredients, instructions, "")
            }
        }
    } else {
        saveRecipeWithImage(name, ingredients, instructions, "")
    }
}

private fun saveRecipeWithImage(
    name: String,
    ingredients: String,
    instructions: String,
    imageUrl: String
) {
    val recipe = hashMapOf(
        "name" to name,
        "ingredients" to ingredients,
        "instructions" to instructions,
        "imageUrl" to imageUrl
    )

    db.collection("recipes").add(recipe)
        .addOnSuccessListener { Log.d("Firestore", "Рецепт добавлен") }
        .addOnFailureListener { e -> Log.e("Firestore", "Ошибка", e) }
}

// Добавляем функцию удаления рецепта
fun deleteRecipe(recipeId: String, onSuccess: () -> Unit) {
    db.collection("recipes").document(recipeId)
        .delete()
        .addOnSuccessListener {
            Log.d("Firestore", "Рецепт успешно удален")
            onSuccess()
        }
        .addOnFailureListener { e ->
            Log.e("Firestore", "Ошибка при удалении", e)
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDialog(onDismiss: () -> Unit, onSave: (String, String, String, Uri?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var ingredients by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить рецепт") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Next
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ingredients,
                    onValueChange = { ingredients = it },
                    label = { Text("Ингредиенты") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Next
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Инструкция") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done
                    ),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { launcher.launch("image/*") }
                    ) {
                        Text("Добавить фото")
                    }
                    if (imageUri != null) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Photo selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, ingredients, instructions, imageUri) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Отмена") }
        }
    )
}