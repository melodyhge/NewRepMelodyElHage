package repository

import dao.UserDao
import model.UserEntity

class UserRepository(private val userDao: UserDao) {
     suspend fun loginUser(username: String, password: String): Result<UserEntity> {
        return try {
            val user = userDao.getUserByCredentials(username, password)
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("Invalid credentials"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

//    suspend fun getAllUsers(): List<String> {
//        return userDao.getAllUsernames().map { it }
//    }

    suspend fun registerUser(username: String, email: String, password: String): Result<UserEntity> {
        return try {
            val existingUser = userDao.getUserByUsername(username)
            if (existingUser != null) {
                Result.failure(Exception("Username already exists"))
            } else {
                val newUser = UserEntity(username = username, email = email, password = password, bio = "")
                userDao.insertUser(newUser)
                Result.success(newUser)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserBio(username: String, newBio: String): Result<Unit> {
        return try {
            val user = userDao.getUserByUsername(username)
            if (user != null) {
                val updatedUser = user.copy(bio = newBio)
                userDao.updateUser(updatedUser)
                Result.success(Unit)
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}