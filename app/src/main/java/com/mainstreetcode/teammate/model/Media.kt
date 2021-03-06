/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.mainstreetcode.teammate.model


import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.mainstreetcode.teammate.persistence.entity.MediaEntity
import com.mainstreetcode.teammate.util.ObjectId
import com.mainstreetcode.teammate.util.asBooleanOrFalse
import com.mainstreetcode.teammate.util.asStringOrEmpty
import com.mainstreetcode.teammate.util.parseISO8601Date
import com.tunjid.androidx.recyclerview.diff.Differentiable
import java.lang.reflect.Type
import java.util.*

class Media : MediaEntity, TeamHost, Parcelable, Model<Media> {

    constructor(
            id: String,
            url: String,
            mimeType: String,
            thumbnail: String,
            user: User,
            hiddenTeam: Team,
            created: Date,
            flagged: Boolean
    ) : super(id, url, mimeType, thumbnail, user, hiddenTeam, created, flagged)

    constructor(`in`: Parcel) : super(`in`)

    override val team: Team
        get() = hiddenTeam

    override val imageUrl: String
        get() = thumbnail

    val isImage: Boolean
        get() = mimeType.startsWith(IMAGE)

    override val isEmpty: Boolean
        get() = TextUtils.isEmpty(id)

    override fun areContentsTheSame(other: Differentiable): Boolean =
            if (other !is Media) diffId == other.diffId else thumbnail == other.thumbnail && url == other.url

    override fun getChangePayload(other: Differentiable): Any? = other

    override fun update(updated: Media) {
        id = updated.id
        url = updated.url
        thumbnail = updated.thumbnail
        mimeType = updated.mimeType
        created = updated.created

        hiddenTeam.update(updated.hiddenTeam)
        user.update(updated.user)
        isFlagged = updated.isFlagged
    }

    override fun compareTo(other: Media): Int = created.compareTo(other.created)

    class GsonAdapter : JsonSerializer<Media>, JsonDeserializer<Media> {

        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Media {

            val mediaJson = json.asJsonObject

            val id = mediaJson.asStringOrEmpty(UID_KEY)
            val url = mediaJson.asStringOrEmpty(URL_KEY)
            val mimeType = mediaJson.asStringOrEmpty(MIME_TYPE_KEY)
            val thumbnail = mediaJson.asStringOrEmpty(THUMBNAIL_KEY)

            val user = context.deserialize<User>(mediaJson.get(USER_KEY), User::class.java)
                    ?: User.empty()
            val team = context.deserialize<Team>(mediaJson.get(TEAM_KEY), Team::class.java)
                    ?: Team.empty()

            val created = mediaJson.asStringOrEmpty(DATE_KEY).parseISO8601Date()
            val flagged = mediaJson.asBooleanOrFalse(FLAGGED_KEY)

            return Media(id, url, mimeType, thumbnail, user, team, created, flagged)
        }

        override fun serialize(src: Media, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =//            JsonObject media = new JsonObject();
        //            media.addProperty(MIME_TYPE_KEY, src.mimeType);
        //            media.addProperty(URL_KEY, src.url);
        //            media.addProperty(TEAM_KEY, src.team.getId());
                //            media.addProperty(USER_KEY, src.user.getId());
                JsonPrimitive(src.id)

        companion object {

            private const val UID_KEY = "_id"
            private const val URL_KEY = "url"
            private const val MIME_TYPE_KEY = "mimetype"
            private const val THUMBNAIL_KEY = "thumbnail"
            private const val USER_KEY = "user"
            private const val TEAM_KEY = "team"
            private const val DATE_KEY = "created"
            private const val FLAGGED_KEY = "flagged"
        }
    }

    companion object {

        const val UPLOAD_KEY = "team-media"
        private const val IMAGE = "image"

        fun fromUri(user: User, team: Team, uri: Uri): Media =
                Media(ObjectId().toHexString(), uri.toString(), "", "", user, team, Date(), false)

        @JvmField
        val CREATOR: Parcelable.Creator<Media> = object : Parcelable.Creator<Media> {
            override fun createFromParcel(`in`: Parcel): Media = Media(`in`)

            override fun newArray(size: Int): Array<Media?> = arrayOfNulls(size)
        }
    }
}
