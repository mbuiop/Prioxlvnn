package com.space.shooter.game

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

// Spacecraft Class
class Spacecraft {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    private val radius = 40f
    private var lastFireTime = 0L
    private val fireCooldown = 300L
    
    fun update(screenWidth: Int, screenHeight: Int) {
        x += vx
        y += vy
        
        // Keep in bounds
        x = x.coerceIn(radius, screenWidth - radius)
        y = y.coerceIn(radius, screenHeight - radius)
    }
    
    fun fireLaser(lasers: MutableList<Laser>) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFireTime >= fireCooldown) {
            lasers.add(Laser(x, y - radius, 0f, -15f))
            lastFireTime = currentTime
        }
    }
    
    fun draw(canvas: Canvas, paint: Paint) {
        // Draw spacecraft (UFO)
        paint.color = Color.CYAN
        canvas.drawCircle(x, y, radius, paint)
        
        // Draw cockpit
        paint.color = Color.BLUE
        canvas.drawCircle(x, y, radius * 0.6f, paint)
        
        // Draw engine glow
        if (abs(vx) > 1 || abs(vy) > 1) {
            paint.color = Color.YELLOW
            canvas.drawCircle(x, y + radius + 5, 10f, paint)
        }
    }
}

// Planet Class
class Planet(var x: Float, var y: Float, var health: Float, val value: Int) {
    private val radius = 35f
    private val originalHealth = health
    
    fun draw(canvas: Canvas, paint: Paint) {
        // Draw planet
        paint.color = Color.GREEN
        canvas.drawCircle(x, y, radius, paint)
        
        // Draw health bar
        val healthRatio = health / originalHealth
        paint.color = when {
            healthRatio > 0.7f -> Color.GREEN
            healthRatio > 0.3f -> Color.YELLOW
            else -> Color.RED
        }
        
        val barWidth = radius * 2
        val barHeight = 8f
        canvas.drawRect(
            x - barWidth / 2,
            y - radius - 15,
            x - barWidth / 2 + barWidth * healthRatio,
            y - radius - 15 + barHeight,
            paint
        )
        
        // Draw value
        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(value.toString(), x, y + 8f, paint)
        paint.textAlign = Paint.Align.LEFT
    }
}

// Enemy Class
class Enemy(var x: Float, var y: Float, private val speed: Float) {
    private val radius = 30f
    
    fun update(screenWidth: Int, screenHeight: Int, targetX: Float, targetY: Float) {
        val dx = targetX - x
        val dy = targetY - y
        val distance = sqrt(dx * dx + dy * dy)
        
        if (distance > 0) {
            x += (dx / distance) * speed
            y += (dy / distance) * speed
        }
    }
    
    fun collidesWith(spacecraft: Spacecraft): Boolean {
        val dx = x - spacecraft.x
        val dy = y - spacecraft.y
        val distance = sqrt(dx * dx + dy * dy)
        return distance < radius + 40f // 40f is spacecraft radius
    }
    
    fun draw(canvas: Canvas, paint: Paint) {
        paint.color = Color.RED
        canvas.drawCircle(x, y, radius, paint)
        
        // Draw enemy details
        paint.color = Color.DKGRAY
        canvas.drawCircle(x, y, radius * 0.7f, paint)
    }
}

// Laser Class
class Laser(var x: Float, var y: Float, var vx: Float, var vy: Float) {
    private val radius = 5f
    
    fun update() {
        x += vx
        y += vy
    }
    
    fun collidesWith(planet: Planet): Boolean {
        val dx = x - planet.x
        val dy = y - planet.y
        val distance = sqrt(dx * dx + dy * dy)
        return distance < radius + 35f // 35f is planet radius
    }
    
    fun collidesWith(enemy: Enemy): Boolean {
        val dx = x - enemy.x
        val dy = y - enemy.y
        val distance = sqrt(dx * dx + dy * dy)
        return distance < radius + 30f // 30f is enemy radius
    }
    
    fun draw(canvas: Canvas, paint: Paint) {
        paint.color = Color.YELLOW
        canvas.drawCircle(x, y, radius, paint)
    }
}

// Star Class for background
data class Star(
    var x: Float,
    var y: Float,
    val size: Float,
    val speed: Float
)

// Explosion Class
class Explosion(x: Float, y: Float) {
    var x = x
    var y = y
    var life = 1f
    private val maxLife = 1f
    private val size = Random.nextFloat() * 10f + 5f
    private val vx = Random.nextFloat() * 8f - 4f
    private val vy = Random.nextFloat() * 8f - 4f
    private val color = Color.argb(255, 
        Random.nextInt(200, 255),
        Random.nextInt(100, 200),
        Random.nextInt(50, 100)
    )
    
    fun update() {
        x += vx
        y += vy
        life -= 0.03f
    }
    
    fun draw(canvas: Canvas, paint: Paint) {
        paint.color = color
        paint.alpha = (life * 255).toInt()
        canvas.drawCircle(x, y, size * life, paint)
    }
}
