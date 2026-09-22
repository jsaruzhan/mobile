package org.lichess.mobileV2.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.widget.RemoteViews
import org.json.JSONObject
import org.lichess.lc0.R
import org.lichess.mobileV2.MainActivity
import org.lichess.mobileV2.widgets.BroadcastWidgetProvider.BroadcastItem
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class CommunityWidgetProvider: AppWidgetProvider() {

  data class CommunityEntryItem(
    val id: String,
    val title: String,
    val imageUrl: String
  )

  override fun onReceive(context: Context, intent: Intent) {
    val pendingResult = goAsync()
    thread {
      try {
        super.onReceive(context, intent)
      } finally {
        pendingResult.finish()
      }
    }
  }

  override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    super.onUpdate(context, appWidgetManager, appWidgetIds)
    appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
  }

  private fun fetchCommunity(lichessHost: String): List<BroadcastItem> {
    try {
      val scheme = if (lichessHost.startsWith("localhost")) "http" else "https"
      val url = URL("$scheme://$lichessHost/api/broadcast/top")
      val connection = url.openConnection() as HttpURLConnection
      connection.connectTimeout = 10000
      connection.readTimeout = 10000
      connection.setRequestProperty("Accept", "application/json")

      try {
        return connection.inputStream.use { stream ->
          val jsonString = stream.bufferedReader().use { it.readText() }
          val active = JSONObject(jsonString).getJSONArray("active")

          (0 until active.length()).map { i ->
            val item = active.getJSONObject(i)
            val tour = item.getJSONObject("tour")
            val round = item.getJSONObject("round")
            val id = item.optJSONObject("roundToLink")?.optString("id")?.takeIf { it.isNotBlank() } ?: round.getString("id")
            val title = item.optString("group").takeIf { !it.isNullOrBlank() } ?: tour.getString("name")

            BroadcastItem(
              id = id,
              title = title,
              roundName = round.getString("name"),
              tourSlug = tour.getString("slug"),
              roundSlug = round.getString("slug"),
              isLive = round.optBoolean("ongoing", false),
              startsAt = round.optLong("startsAt", 0),
              imageUrl = tour.optString("image").takeIf { !it.isNullOrBlank() }
            )
          }
        }
      } finally {
        connection.disconnect()
      }
    } catch (e: Exception) {
      Log.e("BroadcastWidget", "Error fetching broadcasts", e)
      return emptyList()
    }
  }

  private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int){
    val remoteViews = RemoteViews(context.packageName, org.lichess.mobileV2.R.layout.widget_community)

    val homeIntent = Intent(context, MainActivity::class.java).apply {
      action = Intent.ACTION_MAIN
      addCategory(Intent.CATEGORY_LAUNCHER)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val homePendingIntent = PendingIntent.getActivity(
      context,
      appWidgetId,
      homeIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }


  private fun fetchRoundBitmap(urlStr: String, sizePx: Int): Bitmap? {
    return try {
      val url = URL(urlStr)
      val connection = url.openConnection() as HttpURLConnection
      connection.connectTimeout = 5000
      connection.readTimeout = 5000

      try {
        connection.inputStream.use { stream ->
          val src = BitmapFactory.decodeStream(stream) ?: return null
          val square = cropToSquare(src, sizePx)
          val rounded = getRoundedCornerBitmap(square, (sizePx * 0.12f).toInt())
          if (square != src) square.recycle()
          if (src != rounded) src.recycle()
          rounded
        }
      } finally {
        connection.disconnect()
      }
    } catch (e: Exception) {
      Log.e("BroadcastWidget", "Failed to fetch thumbnail: $urlStr", e)
      null
    }
  }

  private fun cropToSquare(src: Bitmap, targetSize: Int): Bitmap {
    val size = minOf(src.width, src.height)
    val cropX = (src.width - size) / 2
    val cropY = (src.height - size) / 2
    val cropped = Bitmap.createBitmap(src, cropX, cropY, size, size)
    return if (size == targetSize) {
      cropped
    } else {
      Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true).also {
        if (cropped != src) cropped.recycle()
      }
    }
  }

  private fun getRoundedCornerBitmap(bitmap: Bitmap, pixels: Int): Bitmap {
    val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint().apply { isAntiAlias = true }
    val rect = Rect(0, 0, bitmap.width, bitmap.height)
    val rectF = RectF(rect)
    val roundPx = pixels.toFloat()

    canvas.drawARGB(0, 0, 0, 0)
    paint.color = Color.BLACK
    canvas.drawRoundRect(rectF, roundPx, roundPx, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(bitmap, rect, rect, paint)

    return output
  }
}
