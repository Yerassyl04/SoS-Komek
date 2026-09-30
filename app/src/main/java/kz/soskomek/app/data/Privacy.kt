package kz.soskomek.app.data

import androidx.annotation.StringRes
import kz.soskomek.app.R
import java.security.SecureRandom
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong

/** Removes personal data from free text before it is published. */
object PiiScrubber {
    const val MASK = "•••"

    data class Result(val text: String, val changed: Boolean, val hadLinks: Boolean)

    private const val UPPER = "А-ЯЁӘҒҚҢӨҰҮҺІA-Z"
    private const val LOWER = "а-яёәғқңөұүһіa-z"

    private val url = Regex("(?i)(https?://|www\\.)\\S+|[\\p{L}\\p{N}-]+\\.(kz|ru|com|org|net|me|io|info)(/\\S*)?")
    private val email = Regex("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.\\p{L}{2,}")
    private val handle = Regex("(?<![\\p{L}\\p{N}])@[\\p{L}\\p{N}_.]{3,}")

    /** Phone numbers, IIN, card and document numbers. */
    private val longNumber = Regex("\\+?\\d[\\d\\s\\-()]{5,}\\d")

    private val namePhrase = Regex(
        "(?i)(меня зовут|его зовут|её зовут|ее зовут|зовут|по имени|по фамилии|фамилия|менің атым|оның аты|атым|есімі|тегі)\\s+[\\p{L}-]+(\\s+[\\p{L}-]+)?"
    )

    private val address = Regex(
        "(?i)(?<!\\p{L})(ул\\.|ул|улица|улице|улицы|пр\\.|пр-т|проспект|проспекте|мкр\\.|мкр|микрорайон|дом|д\\.|кв\\.|кв|квартира|подъезд|көшесі|көшесінде|даңғылы|шағын аудан|пәтер)(?!\\p{L})" +
            "\\s*[\\p{L}\\p{N}\\-/]*(\\s*,?\\s*(д\\.|дом|кв\\.|кв|үй|пәтер)?\\s*\\d+\\p{L}?(/\\d+)?)*"
    )

    /** Two or three capitalised words in a row look like a full name. */
    private val fullName = Regex("(?<!\\p{L})[$UPPER][$LOWER]+\\s+[$UPPER][$LOWER]+(\\s+[$UPPER][$LOWER]+)?")

    fun scrub(input: String): Result {
        var text = input
        val hadLinks = url.containsMatchIn(text)
        text = url.replace(text, MASK)
        text = email.replace(text, MASK)
        text = handle.replace(text, MASK)
        text = namePhrase.replace(text) { "${it.groupValues[1]} $MASK" }
        text = address.replace(text, MASK)
        text = longNumber.replace(text, MASK)
        text = fullName.replace(text, MASK)
        text = text.replace(Regex("($MASK[\\s,]*){2,}"), "$MASK ").replace(Regex("\\s+"), " ").trim()
        return Result(text, text != input.replace(Regex("\\s+"), " ").trim(), hadLinks)
    }
}

/** Automatic pre-moderation performed on the device before a report is published. */
object Moderation {
    data class Outcome(
        val cleanText: String,
        val personalDataRemoved: Boolean,
        val needsReview: Boolean,
        @StringRes val rejectReason: Int? = null,
    )

    const val MIN_LENGTH = 10
    const val MAX_LENGTH = 300

    private val badWords = listOf("хуй", "хуе", "пизд", "ебат", "ебан", "блят", "бляд", "сука", "мраз", "шлюх", "убью", "өлтіремін")

    fun check(raw: String): Outcome {
        val text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.length < MIN_LENGTH) return reject(R.string.mod_too_short)
        if (text.length > MAX_LENGTH) return reject(R.string.mod_too_long)
        val letters = text.count { it.isLetter() }
        if (letters < 5) return reject(R.string.mod_meaningless)
        val scrub = PiiScrubber.scrub(text)
        if (scrub.text.count { it.isLetter() } < 5) return reject(R.string.mod_only_personal)

        val lower = text.lowercase()
        val repeated = Regex("(.)\\1{6,}").containsMatchIn(text)
        val shouting = letters > 20 && text.count { it.isUpperCase() } > letters * 0.7
        val abusive = badWords.any { lower.contains(it) }
        return Outcome(
            cleanText = scrub.text,
            personalDataRemoved = scrub.changed,
            needsReview = scrub.hadLinks || repeated || shouting || abusive,
        )
    }

    private fun reject(@StringRes reason: Int) = Outcome("", false, true, reason)
}

/** Snaps a point to a ~300 m grid and adds random jitter so the exact place cannot be recovered. */
object LocationFuzzer {
    const val APPROX_RADIUS_M = 400.0
    private const val GRID_DEG = 0.003
    private val random = SecureRandom()

    fun approximate(latitude: Double, longitude: Double): Pair<Double, Double> {
        val lonGrid = GRID_DEG / cos(Math.toRadians(latitude)).coerceAtLeast(0.2)
        val lat = floor(latitude / GRID_DEG) * GRID_DEG + GRID_DEG / 2 + (random.nextDouble() - 0.5) * GRID_DEG * 0.6
        val lon = floor(longitude / lonGrid) * lonGrid + lonGrid / 2 + (random.nextDouble() - 0.5) * lonGrid * 0.6
        return round5(lat) to round5(lon)
    }

    private fun round5(v: Double) = (v * 100_000).roundToLong() / 100_000.0
}

object PhoneUtils {
    /** Normalises to E.164. Kazakhstan formats 8 7xx…, 7xx…, +7 7xx… are accepted. */
    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        var digits = trimmed.filter { it.isDigit() }
        if (!trimmed.startsWith("+")) {
            if (digits.length == 11 && digits.startsWith("8")) digits = "7" + digits.drop(1)
            else if (digits.length == 10 && digits.startsWith("7")) digits = "7$digits"
        }
        return if (digits.length in 11..15) "+$digits" else null
    }

    fun format(e164: String): String {
        val d = e164.filter { it.isDigit() }
        return if (d.length == 11 && d.startsWith("7")) {
            "+7 ${d.substring(1, 4)} ${d.substring(4, 7)} ${d.substring(7, 9)} ${d.substring(9, 11)}"
        } else e164
    }
}

/** One-time code sent by SMS to the user's own number. The code itself is kept only as a hash. */
class PhoneVerifier {
    enum class Result { OK, WRONG, EXPIRED, LOCKED, NO_CODE }

    private val random = SecureRandom()
    private var phone: String? = null
    private var codeHash: String? = null
    private var expiresAt = 0L
    private var lastSentAt = 0L
    var attemptsLeft = 0
        private set

    @Synchronized
    fun issue(phone: String): String {
        val code = (100_000 + random.nextInt(900_000)).toString()
        this.phone = phone
        codeHash = hash(phone, code)
        expiresAt = System.currentTimeMillis() + CODE_TTL_MS
        lastSentAt = System.currentTimeMillis()
        attemptsLeft = MAX_ATTEMPTS
        return code
    }

    fun hasActiveCode(phone: String) = this.phone == phone && codeHash != null && System.currentTimeMillis() < expiresAt

    fun resendSecondsLeft(): Int = ((lastSentAt + RESEND_MS - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)

    @Synchronized
    fun verify(phone: String, input: String): Result {
        val hash = codeHash ?: return Result.NO_CODE
        if (this.phone != phone) return Result.NO_CODE
        if (System.currentTimeMillis() > expiresAt) return Result.EXPIRED
        if (attemptsLeft <= 0) return Result.LOCKED
        if (hash(phone, input.trim()) == hash) {
            codeHash = null
            return Result.OK
        }
        attemptsLeft--
        return if (attemptsLeft <= 0) Result.LOCKED else Result.WRONG
    }

    private fun hash(phone: String, code: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest("$phone:$code".toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val CODE_TTL_MS = 5 * 60 * 1000L
        const val RESEND_MS = 60 * 1000L
        const val MAX_ATTEMPTS = 5
    }
}
