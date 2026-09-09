package com.youngs.picview.ui.photo

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.youngs.picview.R
import com.youngs.picview.data.repository.CourseRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 내가 찍은 사진 한 장 지우기. 사진 보기 화면과 상세의 사진 띠가 함께 씁니다.
 *
 * 두 군데가 각자 지우면 확인창 문구와 갤러리 처리 방식이 어긋나기 쉬워서
 * 여기 모았습니다. 순서는 "확인창 → 갤러리에서 삭제 → 기록에서 제거" 입니다.
 *
 * 갤러리 삭제는 이 앱이 저장한 사진이라 보통 바로 됩니다. 앱을 지웠다 다시
 * 깔아 소유권이 사라진 사진이면 시스템이 확인창을 띄우라고 하므로
 * [Outcome.NeedsSystemPrompt] 로 돌려주고, 부른 쪽이 그 창을 띄운 뒤 승인되면
 * [removeRecord] 를 부릅니다. 이미 갤러리에 없는 사진이면 지울 것이 없으니
 * 기록만 뺍니다.
 */
object PhotoDeleter {

    sealed class Outcome {
        /** 갤러리에서 지워졌거나 원래 없었음. 기록도 함께 정리됐습니다. */
        object Done : Outcome()

        /** 시스템 확인창이 필요함. 승인되면 [removeRecord] 를 부르세요. */
        class NeedsSystemPrompt(val sender: IntentSender) : Outcome()

        object Failed : Outcome()
    }

    /** 확인창. 사용자가 "삭제"를 누르면 [onConfirm] 을 부릅니다. */
    fun confirm(context: Context, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.photo_viewer_delete_title)
            .setMessage(R.string.photo_viewer_delete_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.photo_viewer_delete_confirm) { _, _ -> onConfirm() }
            .show()
    }

    /** 갤러리에서 지우고, 됐으면 기록에서도 뺍니다. */
    suspend fun delete(context: Context, photoId: Long, uri: String): Outcome {
        val parsed = Uri.parse(uri)
        val attempt = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.delete(parsed, null, null) }
        }
        attempt.exceptionOrNull()?.let { cause ->
            val sender = systemDeleteRequest(context, parsed, cause) ?: return Outcome.Failed
            return Outcome.NeedsSystemPrompt(sender)
        }
        removeRecord(context, photoId)
        return Outcome.Done
    }

    /** 기록에서만 뺍니다. 시스템 확인창이 승인된 뒤에 부릅니다. */
    suspend fun removeRecord(context: Context, photoId: Long) {
        runCatching { CourseRepository(context.applicationContext).deletePhoto(photoId) }
    }

    /** 앱이 직접 못 지울 때 시스템이 대신 물어봐 주는 요청. 못 만들면 null. */
    private fun systemDeleteRequest(context: Context, uri: Uri, cause: Throwable): IntentSender? =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                runCatching {
                    MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender
                }.getOrNull()
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && cause is RecoverableSecurityException ->
                cause.userAction.actionIntent.intentSender
            else -> null
        }
}
