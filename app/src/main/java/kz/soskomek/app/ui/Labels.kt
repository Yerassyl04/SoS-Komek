package kz.soskomek.app.ui

import androidx.annotation.StringRes
import kz.soskomek.app.R
import kz.soskomek.app.data.ComplaintReason
import kz.soskomek.app.data.IncidentCategory
import kz.soskomek.app.data.Relation
import kz.soskomek.app.data.SosReason
import kz.soskomek.app.data.SyncStatus

@get:StringRes
val SosReason.labelRes: Int
    get() = when (this) {
        SosReason.HARASSMENT -> R.string.reason_harassment
        SosReason.VIOLENCE -> R.string.reason_violence
        SosReason.MEDICAL -> R.string.reason_medical
    }

@get:StringRes
val SosReason.hintRes: Int
    get() = when (this) {
        SosReason.HARASSMENT -> R.string.reason_harassment_hint
        SosReason.VIOLENCE -> R.string.reason_violence_hint
        SosReason.MEDICAL -> R.string.reason_medical_hint
    }

@StringRes
fun serviceNameRes(number: String): Int = when (number) {
    "102" -> R.string.service_police
    "103" -> R.string.service_ambulance
    "101" -> R.string.service_fire
    "111" -> R.string.service_111
    else -> R.string.service_112
}

@get:StringRes
val IncidentCategory.labelRes: Int
    get() = when (this) {
        IncidentCategory.HARASSMENT -> R.string.cat_harassment
        IncidentCategory.STALKING -> R.string.cat_stalking
        IncidentCategory.PHYSICAL_THREAT -> R.string.cat_physical_threat
        IncidentCategory.UNSAFE_PLACE -> R.string.cat_unsafe_place
        IncidentCategory.OTHER -> R.string.cat_other
    }

@get:StringRes
val Relation.labelRes: Int
    get() = when (this) {
        Relation.RELATIVE -> R.string.rel_relative
        Relation.FRIEND -> R.string.rel_friend
        Relation.COLLEAGUE -> R.string.rel_colleague
        Relation.OTHER -> R.string.rel_other
    }

@get:StringRes
val SyncStatus.labelRes: Int
    get() = when (this) {
        SyncStatus.SYNCED -> R.string.sync_synced
        SyncStatus.PENDING -> R.string.sync_pending
        SyncStatus.LOCAL_ONLY -> R.string.sync_local
    }

@get:StringRes
val ComplaintReason.labelRes: Int
    get() = when (this) {
        ComplaintReason.FALSE_INFO -> R.string.complaint_false
        ComplaintReason.DANGEROUS -> R.string.complaint_dangerous
        ComplaintReason.PERSONAL_DATA -> R.string.complaint_personal
        ComplaintReason.OTHER -> R.string.complaint_other
    }
