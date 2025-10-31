package com.space.shooter.game

import android.content.Context
import android.content.SharedPreferences

class GameManager(private val context: Context) {
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("space_shooter_prefs", Context.MODE_PRIVATE)
    
    // Save game state
    fun saveGameState(coins: Int, highScore: Int, currentLevel: Int) {
        prefs.edit().apply {
            putInt("coins", coins)
            putInt("high_score", highScore)
            putInt("current_level", currentLevel)
            apply()
        }
    }
    
    // Load game state
    fun loadCoins(): Int = prefs.getInt("coins", 1000000) // Start with 1 million
    fun loadHighScore(): Int = prefs.getInt("high_score", 0)
    fun loadCurrentLevel(): Int = prefs.getInt("current_level", 1)
    
    // Update high score
    fun updateHighScore(score: Int) {
        val currentHighScore = loadHighScore()
        if (score > currentHighScore) {
            prefs.edit().putInt("high_score", score).apply()
        }
    }
    
    // Add coins
    fun addCoins(amount: Int) {
        val currentCoins = loadCoins()
        prefs.edit().putInt("coins", currentCoins + amount).apply()
    }
}

class LevelManager {
    
    private var currentLevel = 1
    private val maxLevel = 100
    
    data class LevelConfig(
        val planetHealthMultiplier: Float,
        val enemySpeedMultiplier: Float,
        val coinReward: Int,
        val enemyCount: Int
    )
    
    fun getLevelConfig(level: Int): LevelConfig {
        return LevelConfig(
            planetHealthMultiplier = 1f + (level - 1) * 0.2f,
            enemySpeedMultiplier = 1f + (level - 1) * 0.15f,
            coinReward = level * 1000000, // 1M per level
            enemyCount = 10 + (level - 1) * 2
        )
    }
    
    fun completeLevel(): LevelConfig {
        val config = getLevelConfig(currentLevel)
        currentLevel++
        if (currentLevel > maxLevel) {
            currentLevel = maxLevel
        }
        return config
    }
    
    fun getCurrentLevel(): Int = currentLevel
    fun setLevel(level: Int) { currentLevel = level.coerceIn(1, maxLevel) }
    fun canUpgrade(): Boolean = currentLevel < maxLevel
}

class AchievementManager(private val context: Context) {
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("achievements", Context.MODE_PRIVATE)
    
    fun unlockAchievement(achievementId: String) {
        prefs.edit().putBoolean(achievementId, true).apply()
    }
    
    fun isAchievementUnlocked(achievementId: String): Boolean {
        return prefs.getBoolean(achievementId, false)
    }
    
    fun getAchievementProgress(achievementId: String): Int {
        return prefs.getInt("${achievementId}_progress", 0)
    }
    
    fun updateAchievementProgress(achievementId: String, progress: Int) {
        prefs.edit().putInt("${achievementId}_progress", progress).apply()
    }
}
