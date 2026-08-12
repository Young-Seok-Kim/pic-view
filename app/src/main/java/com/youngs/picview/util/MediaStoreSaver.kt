package com.youngs.picview.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

/**
 * 사진을 갤러리에 저장합니다.
 *
 * 안드로이드 10(API 29)에서 저장 방식이 통째로 바뀌었는데, 한쪽 방식만 쓰면
 * 다른 쪽에서 조용히 실패합니다. 이 앱은 minSdk 26 이라 두 방식이 모두 필요합니다.
 *
 * | | API ≤ 28 | API ≥ 29 |
 * |---|---|---|
 * | 경로 지정 | `DATA` 에 절대 경로 | `RELATIVE_PATH` |
 * | 저장 중 숨김 | 없음(폴더를 직접 만들어 씀) | `IS_PENDING` |
 * | 권한 | `WRITE_EXTERNAL_STORAGE` 필요 | 자기 사진은 권한 없이 |
 *
 * 예전에는 API 29 방식만 썼습니다. 그래서 안드로이드 9 이하에서는
 * `table files has no column named relative_path` 로 insert 가 실패했고,
 * 셔터는 눌리는데 사진이 어디에도 남지 않았습니다.
 */
object MediaStoreSaver {

    private const val FOLDER = "PicView"

    /**
     * MediaStore insert 에 넣을 값. CameraX 처럼 저장을 직접 하는 쪽에서 씁니다.
     *
     * 버전에 따라 들어가는 컬럼이 다릅니다. 자세한 내용은 이 객체 문서를 보세요.
     */
    fun imageValues(displayName: String): ContentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                FOLDER
            )
            if (!dir.exists()) dir.mkdirs()
            put(MediaStore.Images.Media.DATA, File(dir, displayName).absolutePath)
        } else {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/" + FOLDER)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    /**
     * 저장이 끝난 사진을 갤러리에 노출합니다.
     *
     * API 29 미만에는 IS_PENDING 이 없어 할 일이 없습니다. 그냥 호출해도
     * 되도록 안에서 갈라 둡니다.
     */
    fun publish(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null, null
            )
        }
    }

    /**
     * @param displayName 확장자를 포함한 파일 이름
     * @return 저장된 사진의 URI. 실패하면 null.
     */
    fun saveJpeg(
        context: Context,
        displayName: String,
        quality: Int = 95,
        write: (OutputStream) -> Unit
    ): Uri? = runCatching {
        val resolver = context.contentResolver

        val values = imageValues(displayName)

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching null

        resolver.openOutputStream(uri)?.use(write)
        publish(context, uri)
        uri
    }.getOrNull()

    /** 비트맵을 그대로 저장할 때 쓰는 짧은 형태. */
    fun saveBitmap(context: Context, displayName: String, bitmap: Bitmap, quality: Int = 95): Uri? =
        saveJpeg(context, displayName, quality) { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
}
