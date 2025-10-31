package com.space.shooter.game

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*
import kotlin.random.Random

class MainGameActivity : AppCompatActivity() {
    
    private lateinit var gameView: GameView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        gameView = GameView(this)
        setContentView(gameView)
    }
    
    override fun onResume() {
        super.onResume()
        gameView.resume()
    }
    
    override fun onPause() {
        super.onPause()
        gameView.pause()
    }
}

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {
    
    private val gameThread: Thread
    private var isRunning = false
    private val holder: SurfaceHolder = holder
    private val paint = Paint()
    
    // Game Objects
    private val spacecraft = Spacecraft()
    private val planets = mutableListOf<Planet>()
    private val enemies = mutableListOf<Enemy>()
    private val lasers = mutableListOf<Laser>()
    private val stars = mutableListOf<Star>()
    private val explosions = mutableListOf<Explosion>()
    
    // Game State
    private var currentLevel = 1
    private var score = 0
    private var coins = 1000000
    private var isGameOver = false
    private var isPaused = false
    
    // Controls
    private var joystickX = 0f
    private var joystickY = 0f
    private var isTouching = false
    
    init {
        holder.addCallback(this)
        isFocusable = true
        
        // Initialize game objects
        initializeStars()
        initializeLevel()
        
        gameThread = Thread(this)
    }
    
    private fun initializeStars() {
        stars.clear()
        repeat(200) {
            stars.add(Star(
                x = Random.nextFloat() * width,
                y = Random.nextFloat() * height,
                size = Random.nextFloat() * 3f + 1f,
                speed = Random.nextFloat() * 2f + 0.5f
            ))
        }
    }
    
    private fun initializeLevel() {
        planets.clear()
        enemies.clear()
        lasers.clear()
        
        // Create 20 planets
        repeat(20) {
            planets.add(Planet(
                x = Random.nextFloat() * (width - 200) + 100,
                y = Random.nextFloat() * (height - 200) + 100,
                health = (currentLevel * 10 + Random.nextInt(20)).toFloat(),
                value = (currentLevel * 1000 + Random.nextInt(5000))
            ))
        }
        
        // Create 10 enemies
        repeat(10) {
            enemies.add(createRandomEnemy())
        }
        
        spacecraft.x = width / 2f
        spacecraft.y = height / 2f
    }
    
    private fun createRandomEnemy(): Enemy {
        val side = Random.nextInt(4)
        return when(side) {
            0 -> Enemy(-50f, Random.nextFloat() * height, 2f + currentLevel * 0.5f)
            1 -> Enemy(width + 50f, Random.nextFloat() * height, 2f + currentLevel * 0.5f)
            2 -> Enemy(Random.nextFloat() * width, -50f, 2f + currentLevel * 0.5f)
            else -> Enemy(Random.nextFloat() * width, height + 50f, 2f + currentLevel * 0.5f)
        }
    }
    
    override fun surfaceCreated(holder: SurfaceHolder) {
        isRunning = true
        gameThread.start()
    }
    
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning = false
        gameThread.join()
    }
    
    override fun run() {
        while (isRunning) {
            if (!isPaused && !isGameOver) {
                update()
            }
            draw()
            Thread.sleep(16) // ~60 FPS
        }
    }
    
    private fun update() {
        // Update spacecraft movement
        if (isTouching) {
            spacecraft.vx = (joystickX - width / 2f) * 0.05f
            spacecraft.vy = (joystickY - height / 2f) * 0.05f
        } else {
            spacecraft.vx *= 0.9f
            spacecraft.vy *= 0.9f
        }
        
        spacecraft.update(width, height)
        
        // Update enemies
        enemies.forEach { enemy ->
            enemy.update(width, height, spacecraft.x, spacecraft.y)
            if (enemy.collidesWith(spacecraft)) {
                createExplosion(spacecraft.x, spacecraft.y)
                isGameOver = true
            }
        }
        
        // Update lasers
        lasers.forEach { laser ->
            laser.update()
            
            // Check planet collisions
            planets.forEach { planet ->
                if (laser.collidesWith(planet)) {
                    planet.health -= 25f
                    createExplosion(laser.x, laser.y)
                    if (planet.health <= 0) {
                        score += planet.value
                        coins += planet.value
                        createExplosion(planet.x, planet.y)
                        planets.remove(planet)
                    }
                    lasers.remove(laser)
                    return@forEach
                }
            }
            
            // Check enemy collisions
            enemies.forEach { enemy ->
                if (laser.collidesWith(enemy)) {
                    createExplosion(enemy.x, enemy.y)
                    enemies.remove(enemy)
                    lasers.remove(laser)
                    score += 500
                    coins += 1000
                    return@forEach
                }
            }
        }
        
        // Remove off-screen lasers
        lasers.removeAll { it.x < 0 || it.x > width || it.y < 0 || it.y > height }
        
        // Update explosions
        explosions.forEach { it.update() }
        explosions.removeAll { it.life <= 0 }
        
        // Update stars for parallax effect
        stars.forEach { star ->
            star.x += spacecraft.vx * star.speed * 0.1f
            star.y += spacecraft.vy * star.speed * 0.1f
            
            // Wrap around
            if (star.x < -20) star.x = width + 20f
            if (star.x > width + 20) star.x = -20f
            if (star.y < -20) star.y = height + 20f
            if (star.y > height + 20) star.y = -20f
        }
        
        // Check level completion
        if (planets.isEmpty()) {
            currentLevel++
            coins += 10000000 // 10 million coins for level completion
            initializeLevel()
        }
        
        // Add new enemies if needed
        while (enemies.size < 10) {
            enemies.add(createRandomEnemy())
        }
    }
    
    private fun draw() {
        var canvas: Canvas? = null
        try {
            canvas = holder.lockCanvas()
            if (canvas != null) {
                // Draw background
                canvas.drawColor(Color.BLACK)
                
                // Draw stars
                drawStars(canvas)
                
                // Draw planets
                planets.forEach { it.draw(canvas, paint) }
                
                // Draw enemies
                enemies.forEach { it.draw(canvas, paint) }
                
                // Draw explosions
                explosions.forEach { it.draw(canvas, paint) }
                
                // Draw spacecraft and lasers
                spacecraft.draw(canvas, paint)
                lasers.forEach { it.draw(canvas, paint) }
                
                // Draw UI
                drawUI(canvas)
                
                // Draw game over
                if (isGameOver) {
                    drawGameOver(canvas)
                }
            }
        } finally {
            if (canvas != null) {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
    
    private fun drawStars(canvas: Canvas) {
        stars.forEach { star ->
            paint.color = Color.argb(255, 255, 255, 255)
            paint.alpha = (star.size * 80).toInt()
            canvas.drawCircle(star.x, star.y, star.size, paint)
        }
    }
    
    private fun drawUI(canvas: Canvas) {
        paint.color = Color.WHITE
        paint.textSize = 48f
        paint.typeface = Typeface.DEFAULT_BOLD
        
        canvas.drawText("Level: $currentLevel", 50f, 80f, paint)
        canvas.drawText("Score: $score", 50f, 140f, paint)
        canvas.drawText("Coins: ${formatCoins(coins)}", 50f, 200f, paint)
        
        // Draw joystick
        if (isTouching) {
            paint.color = Color.argb(100, 100, 100, 255)
            canvas.drawCircle(width / 2f, height - 150f, 80f, paint)
            paint.color = Color.argb(200, 150, 150, 255)
            canvas.drawCircle(joystickX, joystickY, 40f, paint)
        }
    }
    
    private fun drawGameOver(canvas: Canvas) {
        paint.color = Color.argb(200, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        paint.color = Color.RED
        paint.textSize = 72f
        val text = "GAME OVER"
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, width / 2f - bounds.width() / 2f, height / 2f, paint)
        
        paint.color = Color.WHITE
        paint.textSize = 36f
        val scoreText = "Final Score: $score"
        paint.getTextBounds(scoreText, 0, scoreText.length, bounds)
        canvas.drawText(scoreText, width / 2f - bounds.width() / 2f, height / 2f + 80f, paint)
    }
    
    private fun formatCoins(coins: Int): String {
        return when {
            coins >= 1000000 -> "${coins / 1000000}M"
            coins >= 1000 -> "${coins / 1000}K"
            else -> coins.toString()
        }
    }
    
    private fun createExplosion(x: Float, y: Float) {
        repeat(30) {
            explosions.add(Explosion(x, y))
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                isTouching = true
                joystickX = event.x
                joystickY = event.y
                
                // Keep joystick in bounds
                val maxDistance = 80f
                val centerX = width / 2f
                val centerY = height - 150f
                val dx = event.x - centerX
                val dy = event.y - centerY
                val distance = sqrt(dx * dx + dy * dy)
                
                if (distance > maxDistance) {
                    val ratio = maxDistance / distance
                    joystickX = centerX + dx * ratio
                    joystickY = centerY + dy * ratio
                }
                
                // Fire laser when touching near spacecraft
                if (event.y < height / 2f) {
                    spacecraft.fireLaser(lasers)
                }
            }
            MotionEvent.ACTION_UP -> {
                isTouching = false
                joystickX = width / 2f
                joystickY = height - 150f
            }
        }
        return true
    }
    
    fun resume() {
        isPaused = false
    }
    
    fun pause() {
        isPaused = true
    }
}
