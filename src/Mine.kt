import org.json.JSONObject
import java.io.FileWriter
import java.lang.Math.pow
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.*

val config = Config(JSONObject(readLine()))
const val randomFoodCount = 75
const val randomFoodPriority = 1.0
const val permanentVectorCoef = 0.01
const val ejectionPriority = 50.0
const val scorePriority = 50.0
const val distanceCoefficent = 0.15
const val virusDistanceCoefficent = 0.5
const val virusScorePriority = 100.0
const val rayConstant = 1.0
const val scaryFactor = 5.0
const val eatableEnemyFactor = 1.0
const val maxInerion = 20.0
val foodScores: Double = config.foodMass
val random = Random(System.currentTimeMillis())
val distanceCoefficents: Array<DoubleArray> = Array(config.width) { i ->
    DoubleArray(config.height) { j ->
        if (i == 0 && j == 0)
            1.0
        else
            1 / pow(getDistance(0, 0, i, j), distanceCoefficent)
    }
}
val virusDistanceCoefficents: Array<DoubleArray> = Array(config.width) { i ->
    DoubleArray(config.height) { j ->
        if (i == 0 && j == 0)
            1.0
        else
            1 / pow(getDistance(0, 0, i, j), virusDistanceCoefficent)
    }

}
val phantomFoods: Array<Pair<Coordinates, Double>> = Array(randomFoodCount) {
    Pair(Coordinates(random.nextInt(config.width), random.nextInt(config.height)), randomFoodPriority)
}

fun main(args: Array<String>) {
    while (true) {
        val tickData = JSONObject(readLine())
        val move = onTick(tickData)
        println(move)
    }
}

fun onTick(tickData: JSONObject): JSONObject {
    val mine = tickData.getJSONArray("Mine").map { MinePlayer(it as JSONObject) }.sortedBy { it.M }.asReversed()
    val input = tickData.getJSONArray("Objects").map { it as JSONObject }
    val enemies = input.filter { it["T"] == "P" }.map { Enemy(it) }.sortedBy { it.M }.asReversed()
    val players = enemies.groupBy { it.id.split('.')[0] }
    val foods = input.filter { it["T"] == "F" }.map { Food(it) }
//    val viruses = input.filter { it["T"] == "V" }.map { Virus(it) }
    val ejections = input.filter { it["T"] == "E" }.map { Ejection(it) }
    val theBiggestOne = mine[0]
    val mineAverages = MineAverages(mine)
    val field = HashMap<Coordinates, Double>()

    input.filter { it["T"] in arrayOf("F", "E") }.forEach {
        val coordinates = Coordinates(it)
        if (coordinates.X in theBiggestOne.R .. config.width - theBiggestOne.R
                && coordinates.Y in theBiggestOne.R .. config.height - theBiggestOne.R )
            field[coordinates] = 0.0
    }

    if (getVisibilityRadius(theBiggestOne.M) < min(config.width, config.height) / 2.0) {
        for (i in 0 until phantomFoods.size)
            if (mine.any {
                        val visibilityRadius = getVisibilityRadius(it.M)
                        val distance = getDistance(it.X, it.Y, phantomFoods[i].first.X, phantomFoods[i].first.Y)
                        visibilityRadius >= distance
                    })
                phantomFoods[i] = Pair(getUnvisibleCoordinate(mine, theBiggestOne.R), randomFoodPriority)
        for (randomFood in phantomFoods)
            field[randomFood.first] = 0.0
    }

    for (fragment in enemies)
        if (fragment.M * 1.25 <= theBiggestOne.M)
            field[fragment] = 0.0

    for (food in foods)
        field.setSqrtPriorities(food.X, food.Y, foodScores * scorePriority)

    for (ejection in ejections)
        field.setSqrtPriorities(ejection.X, ejection.Y, ejectionPriority)

    for (phantom in phantomFoods)
        field.setSqrtPriorities(phantom.first.X, phantom.first.Y, phantom.second)


    for (mineFragment in mine) {
        val coef = mineFragment.M / mineAverages.mineMass.toDouble()
        for (player in players) {
            val enemyFragments = player.value
            val theBiggestEnemy = enemyFragments.maxBy { it.M }!!
            val dangerEnemy = theBiggestEnemy.M >= 1.15 * mineFragment.M
            val targetFragments =
                    if (dangerEnemy)
                        enemyFragments.filter { it.M >= 1.15 * mineFragment.M }
                    else
                        enemyFragments.filter { mineFragment.M >= 1.25 * it.M }

            for (enemy in targetFragments) {
                val deltaX = delta(enemy.X, mineAverages.averageX)
                val deltaY = delta(enemy.Y, mineAverages.averageY)
                val priority =
                        (if (dangerEnemy)
                            -mineFragment.M * scaryFactor
                        else
                            enemy.M * eatableEnemyFactor) * scorePriority * coef * distanceCoefficents[deltaX][deltaY]
                val alpha = getAngle(enemy.X - mineFragment.X, enemy.Y - mineFragment.Y)
                field.setPriorityFromRay(mineFragment.X, mineFragment.Y, alpha, priority)
            }
        }

//        if (mineFragment.M >= 120) {
//            for (virus in viruses) {
////                val distance = getDistance(virus.X, virus.Y, mineFragment.X, mineFragment.Y)
////                if (distance <= getVisibilityRadius(mineFragment.M)) {
//                val deltaX = delta(virus.X, mineFragment.X)
//                val deltaY = delta(virus.Y, mineFragment.Y)
//                val priority = -mineFragment.M *
//                        virusScorePriority *
//                        virusDistanceCoefficents[deltaX][deltaY] *
//                        coef
//                makeLog("VirusPr: $priority")
//                val distance = getDistance(mineFragment.X, mineFragment.Y, virus.X, virus.Y)
//                val alpha = getAngle(virus.X - mineFragment.X, virus.Y - mineFragment.Y)
//                val alphaSub = atan((mineFragment.R + 22) / distance)
//                val alpha2 = alpha + alphaSub
//                val alpha1 = alpha - alphaSub
//                field.setPriorityOnAngle(mineFragment.X,
//                        mineFragment.Y,
//                        alpha1,
//                        alpha2,
//                        priority)
////                }
//            }
//        }

        val angle = getAngle(mineFragment.SX * 100, mineFragment.SY * 100)
        val priority = coef *
                permanentVectorCoef *
                (foodScores * scorePriority * field.size + randomFoodPriority * randomFoodCount) /
                (config.inertion / maxInerion)
        field.setPriorityFromRay(mineFragment.X, mineFragment.Y, angle, priority)
    }

    var target = field.entries.maxBy {
        val deltaX = delta(it.key.X, mineAverages.averageX)
        val deltaY = delta(it.key.Y, mineAverages.averageY)
        it.value * distanceCoefficents[deltaX][deltaY]
    }!!.key
    makeLog("My target in ${target.X};${target.Y} with ${field[target]}")
    val left = mine.first().X < target.X
    val lower = mine.first().Y < target.Y
    if (mine.all { left == (it.X < target.X) } || mine.all { lower == (it.Y < target.Y) })
        target = getBetterTarget(mineAverages.averageX, mineAverages.averageY, target)

    val split = mine.size < config.maxFragsCount
            && theBiggestOne.M >= 150
            && (enemies.isEmpty() || enemies.maxBy { it.M }!!.M * 2.5 < mineAverages.averageMass)

    return JSONObject(mapOf(
            "X" to target.X,
            "Y" to target.Y,
            "Split" to split))

}

fun getBetterTarget(X: Int, Y: Int, target: Coordinates): Coordinates {
    if (target.X == X) {
        if (target.Y - Y > 0)
            return Coordinates(X, config.height)
        return Coordinates(X, 0)
    }
    val a = (target.Y - Y).toDouble() / (target.X - X)
    val b = Y - a * X
    val angle = getAngle(target.X - X, target.Y - Y)
    when  {
        angle < PI / 2 -> {
            if (a * config.width + b <= config.height)
                return Coordinates(config.width, (a * config.width + b).roundToInt())
            return Coordinates(((config.height - b) / a).roundToInt(), config.height)
        }
        angle <= PI -> {
            if (b <= config.height)
                return Coordinates(0, b.roundToInt())
            return Coordinates(((config.height - b) / a).roundToInt(), config.height)
        }
        angle < 3 * PI / 2 -> {
            if (b >= 0)
                return Coordinates(0, b.roundToInt())
            return Coordinates((-b / a).roundToInt(), 0)
        }
        else -> {
            if (a * config.width + b >= 0)
                return Coordinates(config.width, (a * config.width + b).roundToInt())
            return Coordinates((-b / a).roundToInt(), 0)
        }
    }
}

fun HashMap<Coordinates, Double>.setSqrtPriorities(X: Int, Y: Int, force: Double) {
    for (entry in this.entries) {
        val deltaX = delta(X, entry.key.X)
        val deltaY = delta(Y, entry.key.Y)
        val priority = entry.value + distanceCoefficents[deltaX][deltaY] * force
        this[entry.key] = priority
    }
}

fun HashMap<Coordinates, Double>.setPriorityFromRay(X: Int, Y: Int, alpha:Double, priority: Double) {
    for (entry in this.entries) {
        val a = getAngle(entry.key.X - X, entry.key.Y - Y)
        val angle = min(delta(a, alpha), 2 * PI - delta(a, alpha))
        this[entry.key] = entry.value + priority * pow(1 - (angle / PI), rayConstant)
    }
}

fun HashMap<Coordinates, Double>.setPriorityOnAngle(X: Int, Y: Int, priority: Double, alpha1: Double, alpha2: Double) {
    for (entry in this.entries) {
        val angle = getAngle(entry.key.X - X, entry.key.Y - Y)
        val inAngle =
                if (alpha1 < 0 || alpha2 >= 2 * PI)
                    angle in alpha1 % (2 * PI) .. 2 * PI || angle in 0.0 .. alpha2 % (2 * PI)
                else
                    angle in alpha1 .. alpha2
        if (inAngle)
            this[entry.key] = entry.value + priority
    }
}

fun getUnvisibleCoordinate(fragments: List<MinePlayer>, margin: Int): Coordinates {
    val X = random.nextInt(config.width - 2 * margin) + margin
    val Y = random.nextInt(config.height - 2 * margin) + margin
    val phantomFood = Coordinates(X, Y)
    for (mineFragment in fragments) {
        val visibilityRadius = getVisibilityRadius(mineFragment.M) + 10
        if (getDistance(mineFragment.X, mineFragment.Y, phantomFood.X, phantomFood.Y) <= visibilityRadius)
            return getUnvisibleCoordinate(fragments, margin)
    }
    return phantomFood
}


fun delta(a: Double, b: Double) = abs(a - b)

fun delta(a: Int, b: Int): Int = abs(a - b)

fun getRadius(M: Double): Double = 2 * sqrt(M)

fun getVisibilityRadius(M: Int): Double = 4 * getRadius(M.toDouble()) + 10

fun getFlyingDistance(M: Double): Double
        = (8 * 8 - Math.pow(getMaxSpeed(M), 2.0)) / (2 * config.viscosity)

fun getMaxSpeed(M: Double): Double = config.speedFactor / sqrt(M)

fun getAngle(x: Int, y: Int): Double = getAngle(x.toDouble(), y.toDouble())

fun getAngle(x: Double, y: Double): Double {
    if (x == 0.0) {
        var res = PI / 2
        if (y < 0)
            res += PI
        return res
    }
    var alpha = atan(y / x)
    if (x < 0 && y < 0 || x < 0 && y > 0)
        alpha += PI
    else if (x > 0 && y < 0)
        alpha += 2 * PI
    return alpha
}

fun HashMap<Coordinates, Double>.setLinePriorities(X: Int, Y: Int, force: Double, coef: Double,
                                                   endR: Int = max(config.width, config.height),
                                                   startR: Int = 0, negative: Boolean = false) {
    for (entry in this.entries) {
        val distance = getDistance(X, Y, entry.key.X, entry.key.Y)
        if (distance in startR .. endR && (negative || force - coef * distance > 0))
            this[entry.key] = entry.value + force - coef * distance

    }
}

fun getDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double
        = sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2))

fun getDistance(x1: Int, y1: Int, x2: Int, y2: Int): Double
        = getDistance(x1.toDouble(), y1.toDouble(), x2.toDouble(), y2.toDouble())

class MinePlayer(player: JSONObject): GameObject("M", player) {
    val id = player["Id"] as String
    val R = player["R"]!!.toString().toDouble().roundToInt()
    val M = player["M"]!!.toString().toDouble().roundToInt()
    val SX = player["SX"]!!.toString().toDouble()
    val SY = player["SY"]!!.toString().toDouble()
}

private class Food(food: JSONObject): GameObject(food)

private class Ejection(ejection: JSONObject): GameObject(ejection)

private class Virus(virus: JSONObject): GameObject(virus) {
    val id = virus["Id"]!!.toString()
    val M = virus["M"].toString().toDouble()
}

private class Enemy(enemy: JSONObject): GameObject(enemy) {
    val id = enemy["Id"]!!.toString()
    val M = enemy["M"]!!.toString().toDouble().roundToInt()
    val R = enemy["R"]!!.toString().toDouble().roundToInt()
}

open class GameObject(val type: String, val obj: JSONObject): Coordinates(obj) {
    constructor(obj: JSONObject):
            this(obj["T"].toString(), obj)
}

open class Coordinates(val X: Int, val Y: Int) {
    constructor(obj: JSONObject): this(obj["X"].toString().toDouble().toInt(), obj["Y"].toString().toDouble().toInt())
}

class Config(config: JSONObject) {
    val height = config["GAME_HEIGHT"].toString().toDouble().toInt()
    val width = config["GAME_WIDTH"].toString().toDouble().toInt()
    val virusCritMass = config["VIRUS_SPLIT_MASS"].toString().toDouble()
    val speedFactor = config["SPEED_FACTOR"].toString().toDouble()
    val maxFragsCount = config["MAX_FRAGS_CNT"].toString().toInt()
    val virusRadius = config["VIRUS_RADIUS"].toString().toDouble()
    val tics = config["GAME_TICKS"].toString().toDouble()
    val fusionDelay = config["TICKS_TIL_FUSION"].toString().toDouble()
    val viscosity = config["VISCOSITY"].toString().toDouble()
    val inertion = config["INERTION_FACTOR"].toString().toDouble()
    val foodMass = config["FOOD_MASS"].toString().toDouble()
}

class MineAverages(mine: List<MinePlayer>) {
    val mineMass: Int = mine.map { it.M }.sum()
    val averageRadius: Int = mine.map { it.R * it.M }.sum() / mineMass
    val averageX: Int = mine.map { it.X * it.M }.sum() / mineMass
    val averageY: Int = mine.map { it.Y * it.M }.sum() / mineMass
    val averageSpeedX: Double = mine.map { it.SX * it.M }.sum() / mineMass
    val averageSpeedY: Double = mine.map { it.SY * it.M }.sum() / mineMass
    val averageMass: Int = mineMass / mine.size
}

val log: FileWriter? =
        if (config.width == 660)
            FileWriter("/home/o442a4o3/IdeaProjects/agariodebug/out/artifacts/agariodebug_jar/output.txt")
        else
            null
fun makeLog(msg: String) {
    if (log == null)
        return
    log.write("$msg\n")
    log.flush()
}