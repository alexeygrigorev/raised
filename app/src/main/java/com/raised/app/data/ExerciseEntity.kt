package com.raised.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One exercise name in a preset's ordered exercise list (Room v1 schema).
 *
 * Belongs to a [PresetEntity] via [presetId]; deleting the preset cascades the
 * delete to its exercises so the child table never dangles. [orderIndex] keeps
 * the list in playback order.
 *
 * @property id         stable row id (0 = not yet inserted / auto-generated).
 * @property presetId   FK → [PresetEntity.id].
 * @property orderIndex 0-based position within the preset's exercise list.
 * @property name       exercise display name (e.g. "Push-ups").
 */
@Entity(
    tableName = "exercises",
    foreignKeys = [
        ForeignKey(
            entity = PresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["presetId"])],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long,
    val orderIndex: Int,
    val name: String,
)
