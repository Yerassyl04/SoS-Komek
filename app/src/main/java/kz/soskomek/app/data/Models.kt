package kz.soskomek.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class SosReason(val code: String, val emergencyNumber: String) {
    HARASSMENT("harassment", "102"),
    VIOLENCE("violence", "102"),
    MEDICAL("medical", "103");

    companion object {
        fun from(code: String?) = entries.firstOrNull { it.code == code } ?: HARASSMENT
    }
}

enum class Relation(val code: String) {
    RELATIVE("relative"), FRIEND("friend"), COLLEAGUE("colleague"), OTHER("other");

    companion object {
        fun from(code: String?) = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

data class TrustedContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val phone: String = "",
    val relation: Relation = Relation.RELATIVE,
    val receiveSos: Boolean = true,
    val shareLocation: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("phone", phone)
        .put("relation", relation.code)
        .put("receiveSos", receiveSos).put("shareLocation", shareLocation)

    companion object {
        fun fromJson(o: JSONObject) = TrustedContact(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name"),
            phone = o.optString("phone"),
            relation = Relation.from(o.optString("relation")),
            receiveSos = o.optBoolean("receiveSos", true),
            shareLocation = o.optBoolean("shareLocation", true),
        )
    }
}

data class UserProfile(
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val phoneVerified: Boolean = false,
    val bloodGroup: String = "",
    val medicalNotes: String = "",
    val hasPhoto: Boolean = false,
    val contacts: List<TrustedContact> = emptyList(),
) {
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
    val sosRecipients: List<TrustedContact> get() = contacts.filter { it.receiveSos && it.phone.isNotBlank() }

    fun toJson(): JSONObject = JSONObject()
        .put("firstName", firstName).put("lastName", lastName)
        .put("phone", phone).put("phoneVerified", phoneVerified)
        .put("bloodGroup", bloodGroup).put("medicalNotes", medicalNotes)
        .put("hasPhoto", hasPhoto)
        .put("contacts", JSONArray().apply { contacts.forEach { put(it.toJson()) } })

    companion object {
        const val MAX_CONTACTS = 3

        fun fromJson(o: JSONObject): UserProfile {
            val arr = o.optJSONArray("contacts") ?: JSONArray()
            return UserProfile(
                firstName = o.optString("firstName"),
                lastName = o.optString("lastName"),
                phone = o.optString("phone"),
                phoneVerified = o.optBoolean("phoneVerified"),
                bloodGroup = o.optString("bloodGroup"),
                medicalNotes = o.optString("medicalNotes"),
                hasPhoto = o.optBoolean("hasPhoto"),
                contacts = (0 until arr.length()).map { TrustedContact.fromJson(arr.getJSONObject(it)) },
            )
        }
    }
}

enum class SyncStatus { SYNCED, PENDING, LOCAL_ONLY }

data class SosEvent(
    val id: String,
    val timestamp: Long,
    val reason: SosReason,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyM: Float? = null,
    val locationShared: Boolean = false,
    val contactsTotal: Int = 0,
    val contactsNotified: Int = 0,
    val viaSmsApp: Boolean = false,
    val calledNumber: String? = null,
    val resolvedAt: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("timestamp", timestamp).put("reason", reason.code)
        .putOpt("latitude", latitude).putOpt("longitude", longitude)
        .putOpt("accuracyM", accuracyM?.toDouble())
        .put("locationShared", locationShared)
        .put("contactsTotal", contactsTotal).put("contactsNotified", contactsNotified)
        .put("viaSmsApp", viaSmsApp)
        .putOpt("calledNumber", calledNumber).putOpt("resolvedAt", resolvedAt)
        .put("syncStatus", syncStatus.name)

    /** What the server receives: no name, phone numbers or medical data. */
    fun toServerJson(): JSONObject = JSONObject()
        .put("id", id).put("timestamp", timestamp).put("reason", reason.code)
        .putOpt("latitude", if (locationShared) latitude else null)
        .putOpt("longitude", if (locationShared) longitude else null)
        .put("contactsNotified", contactsNotified)

    companion object {
        fun fromJson(o: JSONObject) = SosEvent(
            id = o.getString("id"),
            timestamp = o.getLong("timestamp"),
            reason = SosReason.from(o.optString("reason")),
            latitude = o.optDoubleOrNull("latitude"),
            longitude = o.optDoubleOrNull("longitude"),
            accuracyM = o.optDoubleOrNull("accuracyM")?.toFloat(),
            locationShared = o.optBoolean("locationShared"),
            contactsTotal = o.optInt("contactsTotal"),
            contactsNotified = o.optInt("contactsNotified"),
            viaSmsApp = o.optBoolean("viaSmsApp"),
            calledNumber = o.optStringOrNull("calledNumber"),
            resolvedAt = if (o.has("resolvedAt") && !o.isNull("resolvedAt")) o.getLong("resolvedAt") else null,
            syncStatus = runCatching { SyncStatus.valueOf(o.optString("syncStatus")) }.getOrDefault(SyncStatus.LOCAL_ONLY),
        )
    }
}

enum class IncidentCategory(val code: String, val color: Long) {
    HARASSMENT("harassment", 0xFFE91E63),
    STALKING("stalking", 0xFF8E24AA),
    PHYSICAL_THREAT("physical_threat", 0xFFD32F2F),
    UNSAFE_PLACE("unsafe_place", 0xFFFB8C00),
    OTHER("other", 0xFF546E7A);

    companion object {
        fun from(code: String?) = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

enum class IncidentStatus { PUBLISHED, PENDING, REJECTED }

data class Incident(
    val id: String,
    val category: IncidentCategory,
    /** Already-approximated coordinates. Exact coordinates are never stored. */
    val latitude: Double,
    val longitude: Double,
    val occurredAt: Long,
    val description: String,
    val createdAt: Long,
    val status: IncidentStatus = IncidentStatus.PUBLISHED,
    val isOwn: Boolean = false,
    val isDemo: Boolean = false,
    val complainedByMe: Boolean = false,
    val synced: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("category", category.code)
        .put("latitude", latitude).put("longitude", longitude)
        .put("occurredAt", occurredAt).put("description", description)
        .put("createdAt", createdAt).put("status", status.name)
        .put("isOwn", isOwn).put("isDemo", isDemo)
        .put("complainedByMe", complainedByMe).put("synced", synced)

    fun toServerJson(): JSONObject = JSONObject()
        .put("id", id).put("category", category.code)
        .put("latitude", latitude).put("longitude", longitude)
        .put("occurredAt", occurredAt).put("description", description)

    companion object {
        fun fromJson(o: JSONObject) = Incident(
            id = o.getString("id"),
            category = IncidentCategory.from(o.optString("category")),
            latitude = o.getDouble("latitude"),
            longitude = o.getDouble("longitude"),
            occurredAt = o.getLong("occurredAt"),
            description = o.optString("description"),
            createdAt = o.optLong("createdAt", o.getLong("occurredAt")),
            status = runCatching { IncidentStatus.valueOf(o.optString("status")) }.getOrDefault(IncidentStatus.PUBLISHED),
            isOwn = o.optBoolean("isOwn"),
            isDemo = o.optBoolean("isDemo"),
            complainedByMe = o.optBoolean("complainedByMe"),
            synced = o.optBoolean("synced", true),
        )
    }
}

internal fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name).takeUnless { it.isNaN() } else null

internal fun JSONObject.optStringOrNull(name: String): String? =
    if (has(name) && !isNull(name)) optString(name) else null
