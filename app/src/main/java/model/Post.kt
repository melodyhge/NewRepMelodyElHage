package model

data class Post(
    val username: String,
    val imageUri: String,
    val description: String,
    val path: String? = null
)
