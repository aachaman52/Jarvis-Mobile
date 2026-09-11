package com.aachmanstudios.jarvismobile.data.database
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class Message(@PrimaryKey(autoGenerate=true) val id:Long=0, val role:String, val text:String)
@Entity data class Note(@PrimaryKey(autoGenerate=true) val id:Long=0, val text:String, val createdAt:Long=System.currentTimeMillis())
@Entity data class Preference(@PrimaryKey val key:String, val value:String)
@Dao interface JarvisDao {
 @Query("SELECT * FROM Message ORDER BY id DESC LIMIT 100") fun messages():Flow<List<Message>>
 @Query("SELECT * FROM Message ORDER BY id DESC LIMIT :count") suspend fun recent(count:Int):List<Message>
 @Insert suspend fun message(message:Message)
 @Query("DELETE FROM Message WHERE id NOT IN (SELECT id FROM Message ORDER BY id DESC LIMIT 100)") suspend fun prune()
 @Insert suspend fun note(note:Note)
 @Query("SELECT * FROM Note ORDER BY id DESC LIMIT 100") suspend fun notes():List<Note>
 @Query("SELECT * FROM Preference") fun preferences():Flow<List<Preference>>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun preference(preference:Preference)
}
@Database(entities=[Message::class,Note::class,Preference::class],version=1,exportSchema=false)
abstract class JarvisDatabase:RoomDatabase(){abstract fun dao():JarvisDao}
